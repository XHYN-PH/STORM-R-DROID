/* Copyright (c) 2026 XHYN_PH. SPDX-License-Identifier: MIT */
package com.naruto.r2;

import android.view.KeyEvent;
import com.naruto.port.NarutoActivity;

/** Switch to the shared background statistics and refresh recovery once the stock startup reports gameplay. */
public final class GameLoop {
    public static boolean tick(int kind, KeyEvent.Callback owner) {
        if (kind != 1) return false;
        NarutoActivity game = (NarutoActivity) owner;
        if (game.isFinishing() || game.isDestroyed()) return true;
        if (!StartupFeedback.ready(game)) return false;
        if (GameHooks.active && GameHooks.prefs(game).getBoolean("menu_stats", true)) {
            NativeSamples.request(game);
            game.statsHandler.postDelayed(game.statsUpdater, 1000);
        } else if (game.statsView != null) {
            game.statsView.setValues(null);
        }
        return true;
    }

    public static void stockSample(NarutoActivity game, float[] values) {
        boolean ready = StartupFeedback.ready(game);
        StartupFeedback.sample(game, values);
        startRecovery(game, ready);
    }

    public static void stockPrepared(NarutoActivity game) {
        boolean ready = StartupFeedback.ready(game);
        StartupFeedback.prepared(game);
        startRecovery(game, ready);
    }

    private static void startRecovery(NarutoActivity game, boolean wasReady) {
        if (!wasReady && StartupFeedback.ready(game) && GameHooks.active && !game.isFinishing() && !game.isDestroyed()) {
            Performance.resume(game);
        }
    }

    public static void resume(NarutoActivity game) {
        GameHooks.active = true;
        SessionDetails.resume(game);
        if (!StartupFeedback.ready(game)) return;
        GameHooks.refreshPrefs(game);
        Performance.resume(game);
        GameHooks.resumeOriginalStats(game);
    }

    public static void focus(NarutoActivity game, boolean focused) {
        if (StartupFeedback.ready(game)) Performance.focus(game, focused);
    }

    public static void pause(NarutoActivity game) {
        GameHooks.active = false;
        SessionDetails.pause(game);
        if (!StartupFeedback.ready(game)) return;
        game.statsHandler.removeCallbacks(game.statsUpdater);
        StatsOverlay.cancel(game.statsView);
        NativeSamples.cancel(game);
        Performance.pause(game);
    }

    public static void destroy(NarutoActivity game) {
        pause(game);
        game.statsHandler.removeCallbacks(game.prepareUpdater);
        Performance.destroy(game);
        NativeSamples.destroy(game);
        StartupFeedback.destroy(game);
    }
}
