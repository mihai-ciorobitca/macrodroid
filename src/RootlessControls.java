package com.codex.rootlessmacros;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.InputMethod;
import android.icu.text.BreakIterator;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.SurroundingText;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.TextView;
import java.util.Locale;
import java.util.function.Consumer;

public final class RootlessControls extends AccessibilityService {
    private static RootlessControls active;
    private ReelFlow flow;
    private TextView label;
    enum Surface {
        TIKTOK("com.zhiliaoapp.musically", "com.zhiliaoapp.musically:id/viewpager",
                "byi", "eqm", "d85", "fb4"),
        FEED("com.instagram.android", "android:id/list",
                "comment_overswipe_dismiss_container", "comment_composer_parent_updated",
                "layout_comment_thread_edittext_multiline", "layout_comment_thread_post_button_icon"),
        REELS("com.instagram.android", "com.instagram.android:id/clips_viewer_view_pager",
                "comment_overswipe_dismiss_container", "comment_composer_parent_updated",
                "layout_comment_thread_edittext_multiline", "layout_comment_thread_post_button_icon");

        final String app;
        final String scrollId;
        final String[] commentIds;

        Surface(String app, String scrollId, String... commentIds) {
            this.app = app;
            this.scrollId = scrollId;
            this.commentIds = commentIds;
        }
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private int typingGeneration;
    private int scrollGeneration;

    static RootlessControls connected() { return active; }

    @Override protected void onServiceConnected() { active = this; }

    @Override public void onDestroy() {
        if (flow != null) flow.stop();
        showStatus("");
        if (active == this) active = null;
        super.onDestroy();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override public void onInterrupt() { ++typingGeneration; }

    void startFlow(ReelFlow.Profile profile) {
        if (flow != null) flow.stop();
        flow = new ReelFlow(this, profile);
        flow.start();
    }

    boolean stopIfRunning(ReelFlow.Profile profile) {
        if (flow == null || !flow.isRunning(profile)) return false;
        flow.stop();
        return true;
    }

    void showStatus(String message) {
        WindowManager windows = getSystemService(WindowManager.class);
        if (message.isEmpty()) {
            if (label != null) {
                windows.removeView(label);
                label = null;
            }
            return;
        }
        if (label == null) {
            label = new TextView(this);
            label.setPadding(16, 5, 16, 5);
            label.setTextColor(Color.WHITE);
            label.setBackgroundColor(0xe6000000);
            label.setGravity(Gravity.CENTER);
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    -2, -2, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            windows.addView(label, params);
        }
        label.setText(message);
    }

    boolean inApp(Surface surface) { return foreground(surface) != null; }

    boolean commentsOpen(Surface surface) {
        AccessibilityNodeInfo root = foreground(surface);
        return root != null && hasCommentSheet(surface, root);
    }

    String read(Surface surface, String id) {
        AccessibilityNodeInfo root = foreground(surface);
        if (root == null) return "";
        for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByViewId(surface.app + ":id/" + id)) {
            if (!node.isVisibleToUser()) continue;
            String value = nodeText(node);
            if (!value.isEmpty()) return value;
        }
        return "";
    }

    private static String nodeText(AccessibilityNodeInfo node) {
        if (node.getText() != null && node.getText().length() > 0) return node.getText().toString();
        if (node.getContentDescription() != null && node.getContentDescription().length() > 0) {
            return node.getContentDescription().toString();
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null || !child.isVisibleToUser()) continue;
            String value = nodeText(child);
            if (!value.isEmpty()) return value;
        }
        return "";
    }

    boolean click(Surface surface, String id) {
        AccessibilityNodeInfo root = foreground(surface);
        if (root == null) return false;
        String qualified = surface.app + ":id/" + id;
        if (clickId(root, qualified)) return true;
        if ("row_feed_button_comment".equals(id) || "row_feed_button_like".equals(id)) {
            for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByViewId(qualified)) {
                AccessibilityNodeInfo parent = node.getParent();
                if (node.isVisibleToUser() && parent != null && parent.isVisibleToUser()
                        && parent.isClickable() && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
            }
        }
        return false;
    }

    boolean sendIfDraftMatches(Surface surface, String expected) {
        InputMethod.AccessibilityInputConnection connection = currentComposer(surface);
        if (connection == null) return false;
        SurroundingText draft = connection.getSurroundingText(241, 241, 0);
        if (draft == null || draft.getOffset() != 0 || !expected.contentEquals(draft.getText())) return false;
        return click(surface, surface == Surface.TIKTOK ? "d85" : "layout_comment_thread_post_button_icon");
    }

    void cancelActions() {
        ++typingGeneration;
        ++scrollGeneration;
    }

    boolean threadContains(Surface surface, String expected) {
        AccessibilityNodeInfo root = foreground(surface);
        if (root == null || !hasCommentSheet(surface, root)) return false;
        for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText(expected)) {
            if (!node.isVisibleToUser() || node.isEditable()) continue;
            CharSequence text = node.getText();
            if (text != null && expected.contentEquals(text)) return true;
        }
        return false;
    }

    private AccessibilityNodeInfo foreground(Surface surface) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        return belongsTo(root, surface) ? root : null;
    }

    private static boolean belongsTo(AccessibilityNodeInfo root, Surface surface) {
        return root != null && surface.app.contentEquals(root.getPackageName());
    }

    private static boolean hasVisible(AccessibilityNodeInfo root, String id) {
        for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByViewId(id)) {
            if (node.isVisibleToUser()) return true;
        }
        return false;
    }

    private boolean hasCommentSheet(Surface surface, AccessibilityNodeInfo root) {
        if (matchesCommentSheet(surface, root)) return true;
        for (AccessibilityWindowInfo window : getWindows()) {
            if (matchesCommentSheet(surface, window.getRoot())) return true;
        }
        return false;
    }

    private static boolean matchesCommentSheet(Surface surface, AccessibilityNodeInfo root) {
        if (!belongsTo(root, surface)) return false;
        for (String id : surface.commentIds) {
            if (hasVisible(root, surface.app + ":id/" + id)) return true;
        }
        return false;
    }

    private static boolean clickId(AccessibilityNodeInfo root, String id) {
        for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByViewId(id)) {
            if (node.isVisibleToUser() && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
        }
        return false;
    }

    private boolean closeComments(Surface surface, AccessibilityNodeInfo root) {
        if (surface != Surface.TIKTOK) return performGlobalAction(GLOBAL_ACTION_BACK);
        String close = surface.app + ":id/byi";
        if (clickId(root, close)) return true;
        for (AccessibilityWindowInfo window : getWindows()) {
            AccessibilityNodeInfo windowRoot = window.getRoot();
            if (belongsTo(windowRoot, surface) && clickId(windowRoot, close)) return true;
        }
        return performGlobalAction(GLOBAL_ACTION_BACK);
    }

    void closeCommentsThenScroll(Surface surface, Consumer<Boolean> result) {
        int generation = ++scrollGeneration;
        main.post(() -> scrollAttempt(surface, 0, generation, result));
    }

    private void scrollAttempt(Surface surface, int attempts, int generation, Consumer<Boolean> result) {
        if (scrollGeneration != generation) {
            result.accept(false);
            return;
        }
        AccessibilityNodeInfo root = foreground(surface);
        if (root == null) {
            result.accept(false);
            return;
        }
        if (hasCommentSheet(surface, root)) {
            if (attempts >= 3 || !closeComments(surface, root)) {
                result.accept(false);
                return;
            }
            main.postDelayed(() -> scrollAttempt(surface, attempts + 1, generation, result), 550);
            return;
        }
        for (AccessibilityWindowInfo window : getWindows()) {
            if (window.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                result.accept(false);
                return;
            }
        }
        if (surface == Surface.FEED && hasVisible(root, surface.app + ":id/clips_viewer_view_pager")) {
            result.accept(false);
            return;
        }
        for (AccessibilityNodeInfo pager : root.findAccessibilityNodeInfosByViewId(surface.scrollId)) {
            if (pager.isVisibleToUser() && pager.isScrollable()
                    && pager.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
                result.accept(true);
                return;
            }
        }
        result.accept(false);
    }

    private InputMethod.AccessibilityInputConnection currentComposer(Surface surface) {
        AccessibilityNodeInfo root = foreground(surface);
        InputMethod input = getInputMethod();
        if (root == null || input == null || !hasCommentSheet(surface, root)) return null;
        EditorInfo editor = input.getCurrentInputEditorInfo();
        if (editor == null || !surface.app.equals(editor.packageName)) return null;
        String id = surface == Surface.TIKTOK ? "eqm" : "layout_comment_thread_edittext_multiline";
        AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focused == null || !focused.isVisibleToUser() || !focused.isEditable()
                || !surface.app.contentEquals(focused.getPackageName())
                || !(surface.app + ":id/" + id).equals(focused.getViewIdResourceName())) return null;
        return input.getCurrentInputConnection();
    }

    void typeComment(Surface surface, String text, Consumer<Boolean> result) {
        main.post(() -> {
            int generation = ++typingGeneration;
            InputMethod.AccessibilityInputConnection connection = currentComposer(surface);
            if (connection == null || text == null || text.isEmpty() || text.length() > 240) {
                result.accept(false);
                return;
            }
            SurroundingText current = connection.getSurroundingText(241, 241, 0);
            if (current == null || current.getText().length() != 0 || current.getOffset() != 0) {
                result.accept(false);
                return;
            }
            BreakIterator boundaries = BreakIterator.getCharacterInstance(Locale.GERMAN);
            boundaries.setText(text);
            typeNext(surface, text, boundaries, boundaries.first(), generation, result);
        });
    }

    private void typeNext(Surface surface, String text, BreakIterator boundaries, int start,
            int generation, Consumer<Boolean> result) {
        if (generation != typingGeneration) {
            result.accept(false);
            return;
        }
        InputMethod.AccessibilityInputConnection connection = currentComposer(surface);
        if (connection == null) {
            result.accept(false);
            return;
        }
        SurroundingText current = connection.getSurroundingText(241, 241, 0);
        if (current == null || current.getOffset() != 0
                || !text.substring(0, start).contentEquals(current.getText())
                || current.getSelectionStart() != start || current.getSelectionEnd() != start) {
            result.accept(false);
            return;
        }
        int end = boundaries.next();
        if (end == BreakIterator.DONE) {
            result.accept(true);
            return;
        }
        connection.commitText(text.substring(start, end), 1, null);
        main.postDelayed(() -> typeNext(surface, text, boundaries, end, generation, result), 75);
    }
}
