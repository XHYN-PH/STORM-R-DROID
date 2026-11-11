/* Copyright (c) 2026 XHYN_PH. SPDX-License-Identifier: MIT */
package com.naruto.r2;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.hardware.display.DisplayManager;
import android.os.*;
import com.naruto.port.LauncherActivity;
import com.naruto.port.NarutoActivity;
import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.*;

/** One current-session snapshot for the original Send log action; no history. */
public final class SessionDetails {
    private static final String PRIVATE="r2-last-session.txt",SHARED="session-details.txt";
    private static final Handler main=new Handler(Looper.getMainLooper());
    private static final ExecutorService io=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"STORM log details");t.setDaemon(true);return t;});
    private static final AtomicBoolean sharing=new AtomicBoolean();
    private static long started;
    private static String settings="",foreground="Not observed",foregroundAt="Not observed";
    private static DisplayManager manager;
    private static DisplayManager.DisplayListener listener;
    private static boolean recording;
    private static String clean(String value) { return value==null?"Unknown":value.replaceAll("[\\p{Cntrl}]"," ").substring(0,Math.min(256,value.length())); }
    private static String version(Context c) {
        try { PackageInfo p=c.getPackageManager().getPackageInfo(c.getPackageName(),0);return clean(p.versionName)+" / code "+p.getLongVersionCode(); }
        catch(Exception unavailable) { return "Unavailable"; }
    }
    private static String choice(SharedPreferences p,String key,int initial,String... values) { int index=p.getInt(key,initial);return index>=0&&index<values.length?values[index]+(p.contains(key)?"":" (default)"):"Unknown selection ("+index+")"; }
    private static String selected(Context c) {
        SharedPreferences p=GameHooks.prefs(c);
        String driverLabel=DriverSupport.selectedLabel(c);
        return "App: STORM R "+version(c)+"\nAndroid: "+clean(Build.VERSION.RELEASE)+" (API "+Build.VERSION.SDK_INT+")\nDevice: "+clean(Build.MANUFACTURER)+" "+clean(Build.MODEL)
            +"\nResolution selected: "+choice(p,"menu_resolution_v5",1,"360p","480p","540p","720p","900p","1080p","2K","4K")
            +"\nAntialiasing selected: "+choice(p,"menu_antialiasing",0,"Off","FXAA","FXAA+")
            +"\nScaling selected: "+choice(p,"menu_upscaling",0,"Normal","FSR")
            +"\nGame FPS selected: "+choice(p,"menu_fps",2,"20","24","30")
            +"\nFrame generation selected: "+choice(p,"menu_framegen",0,"Off","2x","3x","4x")
            +"\nFrame generation mode selected: "+choice(p,"menu_framegen_mode",1,"Performance","Quality")
            +"\nRefresh selected: "+choice(p,"menu_screen_hz",0,"Auto","60 Hz","90 Hz","120 Hz")
            +"\nDriver selected: "+clean(driverLabel)
            +"\nLogging: "+(p.getBoolean("logging_enabled",false)?"On":"Off")+"\n";
    }
    private static String hz(Activity a) {
        try { return String.format(Locale.ROOT,"%.2f Hz",a.getDisplay().getRefreshRate()); }
        catch(RuntimeException unavailable) { return "Unavailable"; }
    }
    public static void start(NarutoActivity a) {
        started=System.currentTimeMillis();recording=GameHooks.prefs(a).getBoolean("logging_enabled",false);
        foreground="Not observed";foregroundAt="Not observed";
        if(!recording){File file=new File(a.getFilesDir(),PRIVATE);io.execute(()->{try{java.nio.file.Files.deleteIfExists(file.toPath());}catch(IOException ignored){}});return;}
        try{settings=selected(a);}catch(RuntimeException unavailable){settings="Session settings unavailable\n";}
        record(a,"created");
    }
    public static void resume(NarutoActivity a) {
        if(!recording)return;
        if(listener==null) {
            manager=(DisplayManager)a.getSystemService(Context.DISPLAY_SERVICE);WeakReference<NarutoActivity> owner=new WeakReference<>(a);
            listener=new DisplayManager.DisplayListener(){public void onDisplayAdded(int id){}public void onDisplayRemoved(int id){}public void onDisplayChanged(int id){NarutoActivity game=owner.get();if(game!=null&&GameHooks.active&&game.hasWindowFocus()&&!game.isFinishing())record(game,"display changed while focused");}};
            if(manager!=null)manager.registerDisplayListener(listener,main);
        }
        record(a,"resumed");
    }
    public static void focus(NarutoActivity a,boolean focused) { if(recording)record(a,focused?"focused":"focus lost"); }
    public static void pause(NarutoActivity a) {
        if(recording)record(a,"paused");
        if(manager!=null&&listener!=null)manager.unregisterDisplayListener(listener);
        listener=null;manager=null;
    }
    public static void stop(Activity a) { if(recording)record(a,"stopped");recording=false; }
    private static void record(Activity a,String phase) {
        if(!recording)return;
        long captured=System.currentTimeMillis();String now=Instant.ofEpochMilli(captured).toString(),observed=hz(a);
        if(GameHooks.active&&a.hasWindowFocus()&&!a.isFinishing()){foreground=observed;foregroundAt=now;}
        String text="Session started epoch ms: "+started+"\nSession started UTC: "+Instant.ofEpochMilli(started)+"\nSnapshot epoch ms: "+captured+"\nSnapshot UTC: "+now+"\nPhase: "+phase
            +"\nLast foreground Android display rate: "+foreground+"\nForeground observation UTC: "+foregroundAt
            +"\nAndroid display rate at snapshot: "+observed+"\n"+settings
            +(a instanceof NarutoActivity?"Refresh recovery: "+Performance.observations((NarutoActivity)a)+"\nStatistics sampling: "+NativeSamples.observations((NarutoActivity)a)+"\n":"");
        File file=new File(a.getFilesDir(),PRIVATE);io.execute(()->{try{SessionFiles.write(file.toPath(),text);}catch(IOException ignored){}});
    }
    public static String prepare(Context context,String current) throws IOException {
        File external=context.getExternalFilesDir(null);if(external==null)throw new IOException("Log folder is unavailable");
        String session=SessionFiles.read(new File(context.getFilesDir(),PRIVATE).toPath(),16384,false);
        StringBuilder out=new StringBuilder("STORM R Send log details\nShared UTC: ").append(Instant.now()).append("\n\nSettings at share time\n").append(current);
        if(!session.isEmpty())out.append("\nLast recorded game session (may use different settings)\n").append(session);
        else out.append("\nNo recorded session details. Enable Logging before the next game launch to record them.\n");
        File log=new File(external,"naruto.log");long epoch=0;Matcher start=Pattern.compile("(?m)^Session started epoch ms: (\\d+)$").matcher(session);if(start.find())try{epoch=Long.parseLong(start.group(1));}catch(NumberFormatException ignored){}
        long snapshot=0;Matcher end=Pattern.compile("(?m)^Snapshot epoch ms: (\\d+)$").matcher(session);if(end.find())try{snapshot=Long.parseLong(end.group(1));}catch(NumberFormatException ignored){}
        boolean matched=epoch>0&&snapshot>=epoch&&log.isFile()&&log.lastModified()>=epoch-2000&&log.lastModified()<=snapshot+5000;
        out.append("\nRenderer-log timestamps fit the recorded session: ").append(matched?"Yes":"Not established; existing logs may be from an earlier run").append('\n');
        if(matched) {
            String head=SessionFiles.read(log.toPath(),32768,false),tail=SessionFiles.read(log.toPath(),65536,true);
            Matcher resolution=Pattern.compile("Render resolution ([^\\r\\n]+)").matcher(head);if(resolution.find())out.append("Engine render resolution: ").append(clean(resolution.group(1))).append('\n');
            Matcher rate=Pattern.compile("AMD optical FG: (\\d+)x \\(asked (\\d+)x, screen (\\d+) Hz\\)").matcher(tail);String latest="";
            while(rate.find())latest="Engine last reported FG / requested FG / refresh: "+rate.group(1)+"x / "+rate.group(2)+"x / "+rate.group(3)+" Hz\n";
            out.append(latest);
            out.append(RendererReport.describe(SessionFiles.read(log.toPath(),SessionFiles.MAX_READ,true)));
        }
        String gpu=SessionFiles.read(new File(external,"gpu_info.txt").toPath(),4096,false);
        out.append("\nActive GPU report (native status file)\n");
        for(String line:gpu.split("\\r?\\n"))if(line.startsWith("GPU:")||line.startsWith("vendor ")||line.startsWith("result:"))out.append(clean(line)).append('\n');
        String text=out.toString();SessionFiles.write(new File(external,SHARED).toPath(),text);return text;
    }
    public static void share(LauncherActivity a,boolean targeted) {
        if(!sharing.compareAndSet(false,true))return;
        // A settings-only file must not turn the stock 'No log yet' case into a share.
        File external=a.getExternalFilesDir(null);boolean hasLog=false;
        if(external!=null)for(String name:new String[]{"naruto.log","xenia.log","plume_stderr.log","shadowzero.txt","gpu_caps.txt","gpu_info.txt"})if(new File(external,name).isFile())hasLog=true;
        if(!hasLog){if(external!=null)new File(external,SHARED).delete();sharing.set(false);a.r2OriginalSendLog(targeted);return;}
        String chosen;try{chosen=selected(a);}catch(RuntimeException unavailable){chosen="Current settings unavailable\n";}String current=chosen;Context app=a.getApplicationContext();WeakReference<LauncherActivity> owner=new WeakReference<>(a);
        io.execute(()-> {
            boolean ok=true;try{prepare(app,current);}catch(IOException|RuntimeException failure){ok=false;File folder=app.getExternalFilesDir(null);if(folder!=null)new File(folder,SHARED).delete();}
            final boolean complete=ok;
            main.post(()->{sharing.set(false);LauncherActivity host=owner.get();if(host==null||host.isFinishing()||host.isDestroyed())return;
                if(!complete)android.widget.Toast.makeText(host,"Could not add session details. Sharing the available logs.",android.widget.Toast.LENGTH_LONG).show();host.r2OriginalSendLog(targeted);});
        });
    }
}
