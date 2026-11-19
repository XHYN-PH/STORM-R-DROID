/* Copyright (c) 2026 XHYN_PH. SPDX-License-Identifier: MIT */
package com.naruto.r2;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.os.Build;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Appends only the current diagnostic game's Android exit record to Send log. */
final class CrashTrace {
    private static final Pattern PID = Pattern.compile("(?m)^Session process PID: (\\d+)$");

    static boolean enabled(Context context) {
        if (!GameHooks.prefs(context).getBoolean("logging_enabled", false)) return false;
        try (InputStream marker = context.getAssets().open("turnip-trace.txt")) {
            return true;
        } catch (IOException missing) {
            return false;
        }
    }

    static void append(Context context, String session, long started, StringBuilder output) {
        if (started <= 0 || !enabled(context)) return;
        Matcher matcher = PID.matcher(session);
        if (!matcher.find()) return;
        int pid;
        try { pid = Integer.parseInt(matcher.group(1)); }
        catch (NumberFormatException invalid) { return; }
        if (pid <= 0) return;

        output.append("\nAndroid exit record for this game session\n");
        try {
            ActivityManager manager = context.getSystemService(ActivityManager.class);
            if (manager == null) throw new IOException("Android exit history is unavailable");
            String packageName = context.getPackageName();
            for (ApplicationExitInfo exit : manager.getHistoricalProcessExitReasons(packageName, pid, 1)) {
                if (!String.valueOf(exit.getProcessName()).equals(packageName + ":game") || exit.getTimestamp() < started) continue;
                output.append("Exit UTC: ").append(Instant.ofEpochMilli(exit.getTimestamp())).append('\n');
                int reason = exit.getReason();
                output.append("Android exit reason: ").append(reason);
                if (reason == ApplicationExitInfo.REASON_CRASH_NATIVE) output.append(" (native crash)");
                if (reason == ApplicationExitInfo.REASON_EXIT_SELF) output.append(" (self exit)");
                output.append("\nExit status: ").append(exit.getStatus()).append('\n');
                if (reason != ApplicationExitInfo.REASON_CRASH_NATIVE || Build.VERSION.SDK_INT < 31) return;
                try (InputStream trace = exit.getTraceInputStream()) {
                    if (trace != null) output.append(NativeBacktrace.describe(trace));
                    else output.append("Android has no native trace available for this exit.\n");
                }
                return;
            }
            output.append("No matching exit recorded yet.\n");
        } catch (IOException | RuntimeException error) {
            output.append("Could not read the Android native trace.\n");
        }
    }
}
