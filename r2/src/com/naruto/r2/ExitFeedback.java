/* Copyright (c) 2026 XHYN_PH. SPDX-License-Identifier: MIT */
package com.naruto.r2;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import com.naruto.port.LauncherActivity;
import com.naruto.port.NarutoActivity;
import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

/** The launcher can keep rendering while SDL shuts down in the game process. */
public final class ExitFeedback {
    private static final String MARKER="r2-exit.pending";
    private static final Handler main=new Handler(Looper.getMainLooper());
    private static final WeakHashMap<Activity,Wait> waits=new WeakHashMap<>();
    private static final class Wait {
        AlertDialog dialog;
        Thread thread;
        volatile boolean cancelled;
    }
    public static void begin(NarutoActivity game) {
        try { SessionFiles.write(new File(game.getFilesDir(),MARKER).toPath(),"closing\n"); }
        catch(IOException ignored) { android.widget.Toast.makeText(game,"Closing game…",android.widget.Toast.LENGTH_SHORT).show(); }
        // The normal launcher runs in a different process; its dialog stays responsive.
        try{game.startActivity(new Intent(game,LauncherActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));}
        catch(RuntimeException unavailable){android.widget.Toast.makeText(game,"Closing game…",android.widget.Toast.LENGTH_SHORT).show();}
    }
    public static void watch(Activity host) {
        if(host.isFinishing()||host.isDestroyed()||waits.containsKey(host))return;
        File marker=new File(host.getFilesDir(),MARKER);
        if(!marker.isFile())return;
        Wait wait=new Wait();
        LinearLayout body=ModernUi.body(host);body.addView(ModernUi.text(host,"Please wait while the game finishes shutting down.",14,ModernUi.TEXT));
        ProgressBar progress=new ProgressBar(host,null,android.R.attr.progressBarStyleHorizontal);progress.setIndeterminate(true);body.addView(progress);
        wait.dialog=ModernUi.show(new AlertDialog.Builder(host).setTitle("Closing game…").setView(body).setCancelable(false));
        wait.dialog.setCanceledOnTouchOutside(false);waits.put(host,wait);
        WeakReference<Activity> owner=new WeakReference<>(host);
        Context app=host.getApplicationContext();
        wait.thread=new Thread(()-> {
            String failure=null;
            try {
                while(!wait.cancelled&&!SessionFiles.gameIdle(app.getFilesDir().toPath()))Thread.sleep(100);
                if(wait.cancelled)return;
                java.nio.file.Files.deleteIfExists(marker.toPath());
            } catch(InterruptedException cancelled) { Thread.currentThread().interrupt();return; }
            catch(IOException error) { failure="Could not check game shutdown. Close and reopen STORM R before managing saves."; }
            final String message=failure;
            main.post(()-> {
                Activity activity=owner.get();if(activity==null||wait.cancelled)return;
                if(message==null)waits.remove(activity);else wait.cancelled=true;
                if(wait.dialog!=null){wait.dialog.dismiss();wait.dialog=null;}
                if(message!=null&&!activity.isFinishing()&&!activity.isDestroyed())ModernUi.show(new AlertDialog.Builder(activity).setMessage(message).setPositiveButton("OK",null));
            });
        },"STORM-close-status");
        wait.thread.setDaemon(true);wait.thread.start();
    }
    public static void destroy(Activity host) {
        Wait wait=waits.remove(host);if(wait==null)return;
        wait.cancelled=true;if(wait.thread!=null)wait.thread.interrupt();
        if(wait.dialog!=null){wait.dialog.dismiss();wait.dialog=null;}
    }
}
