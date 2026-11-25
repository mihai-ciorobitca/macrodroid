package com.codex.rootlessmacros;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;
import java.security.MessageDigest;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

public final class MacroCommand extends Activity {
    static final String ACTION = "com.codex.rootlessmacros.RUN";

    static String localToken(android.content.Context context) {
        SharedPreferences files = context.getSharedPreferences("macro_commands", MODE_PRIVATE);
        String token = files.getString("token", "");
        if (!token.isEmpty()) return token;
        token = UUID.randomUUID().toString();
        if (!files.edit().putString("token", token).commit()) {
            throw new IllegalStateException("Could not create local command token");
        }
        return token;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        Intent intent = getIntent();
        Uri command = intent.getData();
        if (!ACTION.equals(intent.getAction()) || command == null
                || !"socialmacros".equals(command.getScheme()) || !"run".equals(command.getHost())) {
            finish();
            return;
        }
        String given = command.getQueryParameter("token");
        if (given == null || !MessageDigest.isEqual(localToken(this).getBytes(StandardCharsets.UTF_8),
                given.getBytes(StandardCharsets.UTF_8))) {
            finish();
            return;
        }
        RootlessControls controls = RootlessControls.connected();
        ReelFlow.Profile profile = ReelFlow.Profile.fromId(command.getLastPathSegment());
        if (profile == null || controls == null || !new GeminiKey(this).isConfigured()) {
            Toast.makeText(this, "Open Social Macro Controls and finish setup.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if (!controls.stopIfRunning(profile)) {
            Intent target = targetIntent(this, profile.surface);
            if (target == null) {
                Toast.makeText(this, "The target app is missing.", Toast.LENGTH_SHORT).show();
            } else {
                startActivity(target);
                controls.startFlow(profile);
            }
        }
        finish();
    }

    static Intent targetIntent(android.content.Context context, RootlessControls.Surface surface) {
        Intent target = context.getPackageManager().getLaunchIntentForPackage(surface.app);
        if (target != null) return target;
        String activity = surface == RootlessControls.Surface.TIKTOK
                ? "com.ss.android.ugc.aweme.splash.SplashActivity"
                : "com.instagram.android.activity.MainTabActivity";
        return new Intent(Intent.ACTION_MAIN).setClassName(surface.app, activity)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }
}
