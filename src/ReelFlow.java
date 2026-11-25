package com.codex.rootlessmacros;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.textclassifier.TextClassificationManager;
import android.view.textclassifier.TextLanguage;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

final class ReelFlow {
    enum Profile {
        TIKTOK_100("tiktok-100", RootlessControls.Surface.TIKTOK, 100, 5, 10),
        TIKTOK_20("tiktok-20", RootlessControls.Surface.TIKTOK, 20, 5, 10),
        INSTAGRAM_FEED("instagram-feed", RootlessControls.Surface.FEED, 20, 2, 4),
        INSTAGRAM_REELS("instagram-reels", RootlessControls.Surface.REELS, 20, 5, 10);

        final String id;
        final RootlessControls.Surface surface;
        final int chance;
        final int watchMin;
        final int watchMax;

        Profile(String id, RootlessControls.Surface surface, int chance, int watchMin, int watchMax) {
            this.id = id;
            this.surface = surface;
            this.chance = chance;
            this.watchMin = watchMin;
            this.watchMax = watchMax;
        }

        static Profile fromId(String id) {
            for (Profile profile : values()) if (profile.id.equals(id)) return profile;
            return null;
        }
    }

    private static final long RUNTIME = 300_000;
    private final RootlessControls controls;
    private final Profile profile;
    private final RootlessControls.Surface surface;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private long deadline;
    private long watchUntil;
    private String status = "Opening feed...";
    private boolean running;
    private int failedScrolls;
    private String lastCommentAttempt = "";

    ReelFlow(RootlessControls controls, Profile profile) {
        this.controls = controls;
        this.profile = profile;
        surface = profile.surface;
    }

    void start() {
        running = true;
        deadline = SystemClock.elapsedRealtime() + RUNTIME;
        tick();
        later(2600, () -> {
            if (surface != RootlessControls.Surface.TIKTOK) {
                if (!controls.click(surface, surface == RootlessControls.Surface.REELS ? "clips_tab" : "feed_tab")) {
                    stop();
                    return;
                }
            }
            later(1400, this::watch);
        });
    }

    boolean isRunning(Profile target) { return running && profile == target; }

    void stop() {
        running = false;
        controls.cancelActions();
        main.removeCallbacksAndMessages(null);
        worker.shutdownNow();
        controls.showStatus("");
    }

    private boolean active() {
        return running && SystemClock.elapsedRealtime() < deadline;
    }

    private void later(long delay, Runnable action) {
        main.postDelayed(() -> { if (active()) action.run(); }, delay);
    }

    private void tick() {
        if (!active()) { stop(); return; }
        long now = SystemClock.elapsedRealtime();
        long seconds = (deadline - now + 999) / 1000;
        long watch = Math.max(0, (watchUntil - now + 999) / 1000);
        controls.showStatus(String.format(Locale.ROOT, "Runtime left %d:%02d%s\n%s", seconds / 60,
                seconds % 60, watch == 0 ? "" : " | Watching " + watch + "s", status));
        later(200, this::tick);
        // The final tick must run even when earlier callbacks have reached the deadline.
        if (seconds <= 1) main.postDelayed(() -> { if (!active()) stop(); }, Math.max(0, deadline - now));
    }

    private void watch() {
        if (!controls.inApp(surface)) { stop(); return; }
        if (controls.commentsOpen(surface)) { advance(); return; }
        int seconds = ThreadLocalRandom.current().nextInt(profile.watchMin, profile.watchMax + 1);
        status = "Watching this post...";
        watchUntil = SystemClock.elapsedRealtime() + seconds * 1000L;
        later(seconds * 1000L, this::evaluate);
    }

    private String caption() {
        if (surface == RootlessControls.Surface.TIKTOK) return controls.read(surface, "desc");
        if (surface == RootlessControls.Surface.REELS) return controls.read(surface, "clips_caption_component");
        String text = controls.read(surface, "row_feed_caption");
        return text.isEmpty() ? controls.read(surface, "carousel_video_media_group") : text;
    }

    private static boolean german(Context context, String caption) {
        String cleaned = caption.replaceAll("(?iu)https?://\\S+|[#@][\\p{L}\\p{N}_]+", " ")
                .replaceAll("(?iu)\\b(see more|see translation|übersetzung anzeigen|mehr anzeigen)\\b", " ").trim();
        if (cleaned.split("[^\\p{L}\\p{M}]+").length < 4) return false;
        TextClassificationManager manager = context.getSystemService(TextClassificationManager.class);
        if (manager == null) return false;
        try {
            TextLanguage match = manager.getTextClassifier().detectLanguage(new TextLanguage.Request.Builder(cleaned).build());
            if (match.getLocaleHypothesisCount() == 0) return false;
            android.icu.util.ULocale locale = match.getLocale(0);
            return "de".equals(locale.getLanguage()) && match.getConfidenceScore(locale) >= .82f;
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    private void evaluate() {
        if (!controls.inApp(surface) || controls.commentsOpen(surface)) { advance(); return; }
        String post = caption();
        if (post.isEmpty()) {
            status = "Skipped: no usable caption";
            later(600, this::advance);
            return;
        }
        status = "Checking caption language...";
        worker.execute(() -> {
            boolean german = german(controls, post);
            later(0, () -> engage(post, german));
        });
    }

    private void engage(String post, boolean german) {
        if (!german || !post.equals(caption()) || controls.commentsOpen(surface)) {
            status = german ? "Skipped: post changed" : "Skipped: German caption not confirmed";
            later(600, this::advance);
            return;
        }
        boolean like = roll();
        boolean comment = surface == RootlessControls.Surface.REELS ? like : roll();
        String likeId = surface == RootlessControls.Surface.TIKTOK ? "geh"
                : surface == RootlessControls.Surface.FEED ? "row_feed_button_like" : "like_button";
        if (like) {
            String state = controls.read(surface, surface == RootlessControls.Surface.TIKTOK ? "get" : likeId);
            if ("Like video".equalsIgnoreCase(state.trim()) || "Like".equalsIgnoreCase(state.trim())) {
                controls.click(surface, likeId);
            }
        }
        if (!comment || post.equals(lastCommentAttempt)) { advance(); return; }
        status = "Writing a German comment...";
        later(like ? 3000 : 0, () -> worker.execute(() -> {
            String draft = GeminiComment.generate(controls, post);
            later(0, () -> openComposer(post, draft));
        }));
    }

    private boolean roll() {
        return ThreadLocalRandom.current().nextInt(100) < profile.chance;
    }

    private void openComposer(String post, String draft) {
        if (draft.isEmpty() || !controls.inApp(surface) || !post.equals(caption()) || controls.commentsOpen(surface)) {
            status = "Comment skipped";
            later(600, this::advance);
            return;
        }
        String comments = surface == RootlessControls.Surface.TIKTOK ? "ew2"
                : surface == RootlessControls.Surface.FEED ? "row_feed_button_comment" : "comment_button";
        if (!controls.click(surface, comments)) { advance(); return; }
        status = "Opening Comments...";
        later(1000, () -> {
            String editor = surface == RootlessControls.Surface.TIKTOK ? "eqm" : "layout_comment_thread_edittext_multiline";
            if (!controls.commentsOpen(surface) || !controls.click(surface, editor)) { advance(); return; }
            later(600, () -> controls.typeComment(surface, draft, typed -> {
                if (!active()) return;
                if (!typed) {
                    status = "Draft skipped: composer unavailable";
                    later(750, this::advance);
                    return;
                }
                lastCommentAttempt = post;
                boolean sent = controls.sendIfDraftMatches(surface, draft);
                status = sent ? "Send tapped; checking thread..." : "Draft stayed unsent";
                if (sent) {
                    later(4500, () -> {
                        status = controls.threadContains(surface, draft)
                                ? "Comment visible in thread" : "Comment could not be verified";
                        later(800, this::advance);
                    });
                } else {
                    later(750, this::advance);
                }
            }));
        });
    }

    private void advance() {
        if (!controls.inApp(surface)) { stop(); return; }
        status = "Closing Comments before advancing...";
        controls.closeCommentsThenScroll(surface, moved -> {
            if (!active()) return;
            if (moved) {
                failedScrolls = 0;
                later(900, this::watch);
            } else if (++failedScrolls < 3) {
                status = "Swipe blocked: waiting for a clear feed";
                later(1600, this::advance);
            } else {
                stop();
            }
        });
    }
}
