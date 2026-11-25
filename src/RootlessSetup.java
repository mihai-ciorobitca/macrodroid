package com.codex.rootlessmacros;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.content.Intent;
import android.content.ClipData;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class RootlessSetup extends Activity {
    private TextView status;
    private Button permissions;
    private Button importer;
    private TextView testResults;
    private GeminiKey vault;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        vault = new GeminiKey(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(20 * getResources().getDisplayMetrics().density);
        page.setPadding(pad, pad, pad, pad);
        TextView heading = new TextView(this);
        heading.setTextSize(24);
        heading.setText("Social Macro Controls");
        page.addView(heading);
        status = new TextView(this);
        page.addView(status);
        permissions = new Button(this);
        permissions.setText("Grant Accessibility access");
        permissions.setOnClickListener(view -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        page.addView(permissions);
        EditText key = new EditText(this);
        key.setHint("Gemini API key");
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        key.setSaveEnabled(false);
        key.setSingleLine(true);
        page.addView(key);
        Button save = new Button(this);
        save.setText("Save Gemini key");
        save.setOnClickListener(view -> {
            String value = key.getText().toString().trim();
            if (value.isEmpty()) {
                key.setError("Enter your Gemini API key");
                return;
            }
            try {
                vault.save(value);
                key.setText("");
                showStatus();
            } catch (Exception failure) {
                key.setError("Android could not save the key");
            }
        });
        page.addView(save);
        importer = new Button(this);
        importer.setText("Open four-macro import");
        importer.setOnClickListener(view -> importMacros());
        page.addView(importer);
        TextView tests = new TextView(this);
        tests.setText("Debugging-off navigation checks. These do not Like or post comments.");
        page.addView(tests);
        for (ReelFlow.Profile profile : ReelFlow.Profile.values()) {
            Button test = new Button(this);
            test.setText("Check " + profile.id);
            test.setOnClickListener(view -> runPhoneOnlyCheck(profile));
            page.addView(test);
        }
        testResults = new TextView(this);
        page.addView(testResults);
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(page);
        setContentView(scroll);
    }

    @Override protected void onResume() {
        super.onResume();
        showStatus();
    }

    private void showStatus() {
        AccessibilityManager manager = getSystemService(AccessibilityManager.class);
        boolean enabled = manager != null && manager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK).stream().anyMatch(service ->
                getPackageName().equals(service.getResolveInfo().serviceInfo.packageName));
        permissions.setEnabled(!enabled);
        importer.setEnabled(enabled && vault.isConfigured());
        status.setText("Accessibility: " + (enabled ? "granted" : "required")
                + "\nGemini key: " + (vault.isConfigured() ? "saved on this phone" : "required"));
        StringBuilder results = new StringBuilder("On-device test results");
        for (ReelFlow.Profile profile : ReelFlow.Profile.values()) {
            results.append("\n").append(profile.id).append(": ").append(PhoneOnlyTest.result(this, profile));
        }
        testResults.setText(results);
    }

    private void runPhoneOnlyCheck(ReelFlow.Profile profile) {
        RootlessControls controls = RootlessControls.connected();
        if (controls == null || !PhoneOnlyTest.debuggingOff(this)) {
            status.setText("Enable Accessibility and turn off both USB and Wireless debugging to test.");
            return;
        }
        try {
            startActivity(MacroCommand.targetIntent(this, profile.surface));
            new PhoneOnlyTest(controls, profile).start();
        } catch (RuntimeException failure) {
            status.setText("Could not open " + profile.surface.app);
        }
    }

    private void importMacros() {
        try {
            String template;
            try (InputStream input = getAssets().open("rootless-macros.mdr");
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
                byte[] bytes = new byte[4096];
                int count;
                while ((count = input.read(bytes)) != -1) buffer.write(bytes, 0, count);
                template = buffer.toString("UTF-8");
            }
            try (FileOutputStream output = new FileOutputStream(new File(getCacheDir(), RootlessExport.FILE))) {
                output.write(template.replace("__DEVICE_COMMAND_TOKEN__", MacroCommand.localToken(this))
                        .getBytes(StandardCharsets.UTF_8));
            }
            Intent importRequest = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(RootlessExport.URI, "application/mdr")
                    .setClassName("com.arlosoft.macrodroid", "com.arlosoft.macrodroid.filehandler.FileHandlerProxy")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            importRequest.setClipData(ClipData.newRawUri(RootlessExport.FILE, RootlessExport.URI));
            startActivity(importRequest);
        } catch (Exception failure) {
            status.setText("MacroDroid could not open the import. Check that MacroDroid is installed.");
        }
    }
}
