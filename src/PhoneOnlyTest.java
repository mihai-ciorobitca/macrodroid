package com.codex.rootlessmacros;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

final class PhoneOnlyTest {
    private final RootlessControls controls;
    private final ReelFlow.Profile profile;
    private final RootlessControls.Surface surface;
    private final Handler main = new Handler(Looper.getMainLooper());

    PhoneOnlyTest(RootlessControls controls, ReelFlow.Profile profile) {
        this.controls = controls;
        this.profile = profile;
        surface = profile.surface;
    }

    static boolean debuggingOff(Context context) {
        return Settings.Global.getInt(context.getContentResolver(), "adb_enabled", -1) == 0
                && Settings.Global.getInt(context.getContentResolver(), "adb_wifi_enabled", -1) == 0;
    }

    static String result(Context context, ReelFlow.Profile profile) {
        return context.getSharedPreferences("phone_only_tests", Context.MODE_PRIVATE)
                .getString(profile.id, "Pending");
    }

    void start() {
        controls.showStatus("Phone-only test: opening " + profile.id + "...");
        main.postDelayed(this::openSurface, 2800);
    }

    private void openSurface() {
        if (!debuggingOff(controls)) { finish("Blocked: debugging is on"); return; }
        if (!controls.inApp(surface)) { finish("Blocked: app did not open"); return; }
        if (surface != RootlessControls.Surface.TIKTOK) {
            String tab = surface == RootlessControls.Surface.REELS ? "clips_tab" : "feed_tab";
            if (!controls.click(surface, tab)) { finish("Blocked: tab unavailable"); return; }
        }
        main.postDelayed(this::openComments, 1600);
    }

    private void openComments() {
        if (!debuggingOff(controls) || !controls.inApp(surface)) { finish("Blocked: screen changed"); return; }
        if (!controls.commentsOpen(surface)) {
            String id = surface == RootlessControls.Surface.TIKTOK ? "ew2"
                    : surface == RootlessControls.Surface.FEED ? "row_feed_button_comment" : "comment_button";
            if (!controls.click(surface, id)) { finish("Blocked: Comments button unavailable"); return; }
        }
        main.postDelayed(this::verifyClosedBeforeScroll, 1200);
    }

    private void verifyClosedBeforeScroll() {
        if (!debuggingOff(controls)) { finish("Blocked: debugging is on"); return; }
        if (!controls.commentsOpen(surface)) { finish("Blocked: Comments did not open"); return; }
        controls.closeCommentsThenScroll(surface, moved -> {
            boolean passed = debuggingOff(controls) && moved && !controls.commentsOpen(surface);
            finish(passed ? "Passed: Comments closed before scrolling" : "Blocked: guarded swipe could not be verified");
        });
    }

    private void finish(String result) {
        controls.getSharedPreferences("phone_only_tests", Context.MODE_PRIVATE).edit()
                .putString(profile.id, result).apply();
        controls.showStatus(profile.id + "\n" + result);
        main.postDelayed(() -> controls.showStatus(""), 6500);
    }
}
