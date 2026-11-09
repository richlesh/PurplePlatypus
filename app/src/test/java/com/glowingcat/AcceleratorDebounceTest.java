/*
 * (c) 2026 Glowing Cat Software
 */
package com.glowingcat;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Verifies the macOS Option/Alt accelerator double-fire suppression policy used
 * by {@code EditorWindow.debounced}. The duplicate ActionEvent must be ignored
 * even when the first action opens a modal dialog that blocks the EDT for a long
 * time (the real "export shows the save dialog twice" scenario), while genuine
 * later invocations are still allowed.
 */
public class AcceleratorDebounceTest {

    private static final long W = EditorWindow.ACCELERATOR_DEBOUNCE_MS;

    @Test
    public void firstEventIsNeverSuppressed() {
        // No action in progress, no prior completion.
        assertFalse(EditorWindow.shouldSuppressDuplicate(false, 0L, 10_000L));
    }

    @Test
    public void duplicateDuringModalDialogIsSuppressed() {
        // A duplicate delivered while the first action is still running (e.g. via
        // the modal FileDialog's nested event loop) must be suppressed regardless
        // of elapsed time.
        long start = 10_000L;
        assertTrue(EditorWindow.shouldSuppressDuplicate(true, 0L, start));
        assertTrue(EditorWindow.shouldSuppressDuplicate(true, 0L, start + 60_000L));
    }

    @Test
    public void duplicateRightAfterModalCloseIsSuppressed() {
        // After the blocking action completes, lastCompletionTime is "now". The
        // queued duplicate is processed immediately afterwards and must fall
        // inside the window — this is the exact case the old start-time window
        // failed, because a long modal dialog pushed the duplicate past 300ms.
        long completion = 75_000L; // e.g. user spent time in the dialog
        assertTrue(EditorWindow.shouldSuppressDuplicate(false, completion, completion + 1));
        assertTrue(EditorWindow.shouldSuppressDuplicate(false, completion, completion + (W - 1)));
    }

    @Test
    public void genuineLaterInvocationIsAllowed() {
        long completion = 75_000L;
        assertFalse(EditorWindow.shouldSuppressDuplicate(false, completion, completion + W));
        assertFalse(EditorWindow.shouldSuppressDuplicate(false, completion, completion + 5_000L));
    }
}
