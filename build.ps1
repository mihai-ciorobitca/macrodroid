param(
    [Parameter(Mandatory = $true)][string]$JdkRoot,
    [Parameter(Mandatory = $true)][string]$AndroidSdk
)
$ErrorActionPreference = 'Stop'
$env:JAVA_HOME = (Resolve-Path -LiteralPath $JdkRoot).Path
$sdk = (Resolve-Path -LiteralPath $AndroidSdk).Path
$android = Join-Path $sdk 'platforms/android-36/android.jar'
$tools = Join-Path $sdk 'build-tools/36.0.0'
$build = Join-Path $PSScriptRoot 'build'
$classes = Join-Path $build 'classes'
$dex = Join-Path $build 'dex'
$resources = Join-Path $build 'resources.zip'
$unsigned = Join-Path $build 'unsigned.apk'
$apk = Join-Path $build 'Social-Macro-Controls-dev.apk'
$signing = Join-Path $build 'development.p12'

function Invoke-BuildTool {
    param([string]$Tool, [string[]]$Arguments)
    & $Tool @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Build command failed: $Tool ($LASTEXITCODE)" }
}

New-Item -ItemType Directory -Force -Path $classes,$dex | Out-Null
$sources = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'src') -Filter '*.java' |
    ForEach-Object FullName)
Invoke-BuildTool "$env:JAVA_HOME/bin/javac.exe" (@(
    '-encoding','UTF-8','--release','8','-cp',$android,'-d',$classes
) + $sources)
$compiled = @(Get-ChildItem -LiteralPath $classes -Filter '*.class' -Recurse |
    ForEach-Object FullName)
Invoke-BuildTool "$env:JAVA_HOME/bin/java.exe" (@(
    '-cp',"$tools/lib/d8.jar",'com.android.tools.r8.D8','--lib',$android,
    '--min-api','33','--output',$dex
) + $compiled)
Invoke-BuildTool "$tools/aapt2.exe" @('compile','--dir',(Join-Path $PSScriptRoot 'res'),'-o',$resources)
Invoke-BuildTool "$tools/aapt2.exe" @(
    'link','-I',$android,'--manifest',(Join-Path $PSScriptRoot 'AndroidManifest.xml'),
    '-A',(Join-Path $PSScriptRoot 'assets'),'--min-sdk-version','33','--target-sdk-version','35',
    '--version-code','3','--version-name','0.3','-o',$unsigned,$resources
)
Invoke-BuildTool "$env:JAVA_HOME/bin/jar.exe" @('uf',$unsigned,'-C',$dex,'classes.dex')
if (-not (Test-Path -LiteralPath $signing)) {
    Invoke-BuildTool "$env:JAVA_HOME/bin/keytool.exe" @(
        '-genkeypair','-keystore',$signing,'-storetype','PKCS12','-storepass','android',
        '-alias','development','-keyalg','RSA','-keysize','2048','-validity','3650',
        '-dname','CN=Local Development'
    )
}
Invoke-BuildTool "$tools/apksigner.bat" @('sign','--ks',$signing,'--ks-pass','pass:android','--out',$apk,$unsigned)
Invoke-BuildTool "$tools/apksigner.bat" @('verify',$apk)
Write-Output $apk
