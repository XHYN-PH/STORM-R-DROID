/* Copyright (c) 2026 XHYN_PH. SPDX-License-Identifier: MIT */
package com.naruto.r2;
import android.app.Activity;
import android.content.*;
import android.os.*;
import android.view.*;
import java.io.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class GameHooks {
    public static boolean active=true,preparationDone,returnToLauncher;
    private static RandomAccessFile gameLockFile;
    private static FileLock gameLock;
    public static SharedPreferences prefs(Context c){return c.getSharedPreferences("naruto",0);}
    public static void beforeLauncher(Activity a){prefs(a).edit().remove("r2_graph").remove("r2_rumble").remove("r2_rumble_strength").remove("r2_touch_right").remove("r2_touch_gain").remove("r2_left_deadzone").remove("r2_right_deadzone").remove("r2_right_axes").remove("r2_right_invert").remove("r2_ps_labels").apply();if(!a.getIntent().hasExtra("trailer"))a.getIntent().putExtra("trailer",prefs(a).getBoolean("r2_video",true));}
    public static void gameStart(Activity a){
        active=true;preparationDone=false;returnToLauncher=false;
        try{gameLockFile=new RandomAccessFile(new File(a.getFilesDir(),"r2-game.lock"),"rw");gameLock=gameLockFile.getChannel().tryLock();
            if(gameLock==null)throw new IOException("A save operation is in progress. Close and reopen the game after it finishes.");
        }catch(IOException|OverlappingFileLockException e){try{if(gameLockFile!=null)gameLockFile.close();}catch(IOException ignored){}gameLockFile=null;gameLock=null;android.widget.Toast.makeText(a,"Save management is in progress. Try starting the game again when it finishes.",android.widget.Toast.LENGTH_LONG).show();a.finish();}
    }
    public static void gameStop(Activity a){
        active=false;SessionDetails.stop(a);
        Thread engine=a instanceof com.naruto.port.NarutoActivity?((com.naruto.port.NarutoActivity)a).r2GameThread():null;
        if(engine!=null&&engine.isAlive()){
            // SDL's normal shutdown only waits one second. Keep saves locked if it takes longer.
            Thread waiter=new Thread(()->{try{engine.join();}catch(InterruptedException interrupted){Thread.currentThread().interrupt();return;}new Handler(Looper.getMainLooper()).post(()->finishStop(a));},"STORM-exit-wait");waiter.setDaemon(true);waiter.start();
        }else finishStop(a);
    }
    private static void finishStop(Activity a){
        try{if(gameLock!=null)gameLock.release();}catch(IOException ignored){}
        try{if(gameLockFile!=null)gameLockFile.close();}catch(IOException ignored){}
        gameLock=null;gameLockFile=null;
        if(returnToLauncher){returnToLauncher=false;a.startActivity(new Intent(a,com.naruto.port.LauncherActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));}
    }
    public static void refreshPrefs(Activity a){a.getSharedPreferences("naruto",4).getAll();}
    /** Compatibility builds keep the original UI-thread dispatcher after an FPS toggle. */
    public static void resumeOriginalStats(com.naruto.port.NarutoActivity a){
        a.statsHandler.removeCallbacks(a.statsUpdater);
        if(!a.isFinishing()&&!a.isDestroyed())a.statsHandler.post(a.statsUpdater);
    }
    public static void gameReady(final Activity a){
        if(a.isFinishing())return;
        if(a instanceof com.naruto.port.NarutoActivity){GameMenu.applyTouch((com.naruto.port.NarutoActivity)a);StartupFeedback.begin((com.naruto.port.NarutoActivity)a);SessionDetails.start((com.naruto.port.NarutoActivity)a);}
        final SharedPreferences p=prefs(a);
        final View surface=findSurface(a.getWindow().getDecorView());
        if(surface!=null)surface.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->applyCrop(surface,p.getInt("r2_screen",0),p.getInt("r2_crop",100)));
        if(surface!=null)surface.post(()->applyCrop(surface,p.getInt("r2_screen",0),p.getInt("r2_crop",100)));

    }
    public static void updateCrop(Activity a){View v=findSurface(a.getWindow().getDecorView());if(v!=null)applyCrop(v,prefs(a).getInt("r2_screen",0),prefs(a).getInt("r2_crop",100));}
    public static void stockScreen(Activity a,boolean stretch){prefs(a).edit().putInt("r2_screen",stretch?1:0).apply();View v=findSurface(a.getWindow().getDecorView());if(v!=null)applyCrop(v,stretch?1:0,100);}
    public static int dp(Context c,float v){return Math.round(c.getResources().getDisplayMetrics().density*v);}
    static View findSurface(View v){if(v instanceof SurfaceView)return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View found=findSurface(g.getChildAt(i));if(found!=null)return found;}}return null;}
    public static float cropScale(float w,float h,int amount){if(w<=0||h<=0)return 1;float ratio=w/h,target=16f/9f,fill=Math.max(ratio/target,target/ratio);return 1+(fill-1)*Math.max(0,Math.min(100,amount))/100f;}
    private static void applyCrop(View v,int mode,int amount){float scale=mode==2?cropScale(v.getWidth(),v.getHeight(),amount):1;v.setPivotX(v.getWidth()/2f);v.setPivotY(v.getHeight()/2f);v.setScaleX(scale);v.setScaleY(scale);}
    public static void config(Activity a){
        File file=new File(a.getExternalFilesDir(null),"naruto.ini");if(!file.isFile())return;
        SharedPreferences p=prefs(a);LinkedHashMap<String,String> overrides=new LinkedHashMap<>();
        overrides.put("language","en");
        overrides.put("xnr.renut_brightness_gamma",Float.toString(Math.max(.5f,Math.min(2,p.getFloat("r2_gamma",1)))));
        int shadow=p.getInt("r2_shadow",0);if(shadow!=0)overrides.put("xnr.szr_shadow_map_max_height",Integer.toString(shadow));
        int budget=p.getInt("r2_budget",0);if(budget>0)overrides.put("xnr.gpu_budget_mb",Integer.toString(budget));
        int cache=p.getInt("r2_cache",0);if(cache>0)overrides.put("xnr.banjo_renut_stream_cache_mb",Integer.toString(cache));
        int mode=p.getInt("r2_screen",-1);if(mode>=0){overrides.put("stretch",mode==1?"1":"0");overrides.put("xnr.renut_present_stretch",mode==1?"true":"false");overrides.put("xnr.szr_present_trim","0");}
        try{
            String text=new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8);
            text=merge(text,overrides);
            File temp=new File(file.getParentFile(),"naruto.ini.r2.tmp");
            try(FileOutputStream out=new FileOutputStream(temp)){out.write(text.getBytes(StandardCharsets.UTF_8));out.getFD().sync();}
            Files.move(temp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
        }catch(IOException e){throw new IllegalStateException("Could not save the selected STORM R settings.",e);}
    }
    public static String merge(String source,Map<String,String> values){
        StringBuilder result=new StringBuilder();
        for(String line:source.split("\\r?\\n")){int at=line.indexOf('=');String key=at<0?"":line.substring(0,at).trim();if(!values.containsKey(key))result.append(line).append('\n');}
        for(Map.Entry<String,String> entry:values.entrySet())result.append(entry.getKey()).append(" = ").append(entry.getValue()).append('\n');
        return result.toString();
    }
}
