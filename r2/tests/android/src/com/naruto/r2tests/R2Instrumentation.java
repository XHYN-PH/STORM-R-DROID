/* Copyright (c) 2026 XHYN_PH. SPDX-License-Identifier: MIT */
package com.naruto.r2tests;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.io.File;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class R2Instrumentation extends Instrumentation {
    private int assertions;
    private volatile boolean completed;
    private volatile String lastProgress="starting";
    private Activity launcher;
    private void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
        progress("PASS: " + message);
    }
    private void progress(String message) {
        lastProgress=message;
        Bundle status = new Bundle(); status.putString("stream", message + "\n"); sendStatus(1, status);
    }
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    private void collect(View view, List<View> views) {
        views.add(view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup)view;
            for (int i=0; i<group.getChildCount(); i++) collect(group.getChildAt(i), views);
        }
    }
    private boolean hasText(List<View> views, String text) {
        for (View view: views) if (view instanceof TextView && text.contentEquals(((TextView)view).getText())) return true;
        return false;
    }
    private View findLoggingSwitch(List<View> views) {
        for (View view: views) {
            if (!(view instanceof TextView) || !"Logging".contentEquals(((TextView)view).getText())) continue;
            View current=view;
            while (current.getParent() instanceof ViewGroup) {
                current=(View)current.getParent();
                List<View> children=new ArrayList<>(); collect(current, children);
                for (View child:children) if (child.getClass().getName().equals("a.v0")) return child;
            }
        }
        return null;
    }
    private void touch(final View view) {
        runOnMainSync(new Runnable() { public void run() {
            long time=SystemClock.uptimeMillis();
            MotionEvent down=MotionEvent.obtain(time,time,MotionEvent.ACTION_DOWN,5,5,0);
            MotionEvent up=MotionEvent.obtain(time,time+10,MotionEvent.ACTION_UP,5,5,0);
            view.dispatchTouchEvent(down); view.dispatchTouchEvent(up); down.recycle(); up.recycle();
        }});
    }
    private String read(File file) throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try (FileInputStream in=new FileInputStream(file)) {
            byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))>0)out.write(buffer,0,n);
        }
        return new String(out.toByteArray(), "UTF-8");
    }
    private void testController(Context context,ClassLoader loader)throws Exception {
        Intent intent=new Intent();intent.setClassName(context.getPackageName(),"com.naruto.port.ControllerMapActivity");intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        final Activity map=startActivitySync(intent);waitForIdleSync();SystemClock.sleep(1000);screenshot(context,"controller");final Throwable[] error=new Throwable[1];
        runOnMainSync(()->{try{
            List<View> views=new ArrayList<>();collect(map.getWindow().getDecorView(),views);
            check(!hasText(views,"Reset selected")&&!hasText(views,"Diagram"),"Added controller preview and reset UI are removed");
            java.util.Map rows=(java.util.Map)map.getClass().getField("f").get(map);
            check(rows.size()==16,"Stock controller mapping list contains all original bindings");
            ((View)rows.get("a")).performClick();check("a".equals(map.getClass().getField("h").get(map)),"Original mapping selection works");
            ((View)rows.get("a")).performClick();check(map.getClass().getField("h").get(map)==null,"Original mapping selection cancels normally");
            map.finish();
        }catch(Throwable e){error[0]=e;}});
        if(error[0]!=null)throw new RuntimeException(error[0]);
    }
    private void testSaveFiles(Context c,ClassLoader loader)throws Exception {
        File base=new File(c.getExternalFilesDir(null),"r2-fixture-"+java.util.UUID.randomUUID());File locks=new File(c.getFilesDir(),"r2-fixture-"+java.util.UUID.randomUUID());check(base.mkdirs()&&locks.mkdirs(),"Isolated Android save fixtures are created");
        File save=new File(base,"data_revolution/content/stormr.s/STORMR.S");save.getParentFile().mkdirs();write(save,"progress-A");
        File flag=new File(base,"save100-revolution.active");write(flag,"protected-flag");File original=new File(base,"save100-original-revolution/package/STORMR.S");original.getParentFile().mkdirs();write(original,"protected-original");
        Class<?> type=Class.forName("com.naruto.r2.SaveFiles",true,loader);Object store=type.getConstructor(File.class,File.class).newInstance(base,locks);
        try{
            ByteArrayOutputStream exported=new ByteArrayOutputStream();type.getMethod("exportCurrent",java.io.OutputStream.class).invoke(store,exported);
            check(exported.size()>0&&read(save).equals("progress-A"),"Android save export is read-only");
            final File destination=new File(c.getExternalFilesDir(null),"r2-reimport-"+java.util.UUID.randomUUID());if(!destination.mkdirs())throw new java.io.IOException("Cannot create reimport fixture");
            final Context originalContext=c;
            Context isolated=new android.content.ContextWrapper(c){@Override public File getExternalFilesDir(String kind){return destination;}@Override public File getFilesDir(){File f=new File(destination,"private");f.mkdirs();return f;}@Override public android.content.SharedPreferences getSharedPreferences(String name,int mode){return originalContext.getSharedPreferences("r2_reimport_"+name,mode);}};
            File zip=new File(c.getCacheDir(),"r2-export-test-"+java.util.UUID.randomUUID()+".zip");try(java.io.FileOutputStream out=new java.io.FileOutputStream(zip)){out.write(exported.toByteArray());}
            Class.forName("a.i",true,loader).getMethod("j",Context.class,android.net.Uri.class).invoke(null,isolated,android.net.Uri.fromFile(zip));
            check(read(new File(destination,"data_revolution/content/stormr.s/STORMR.S")).equals("progress-A"),"Exported ZIP reimports through the unchanged stock importer in an isolated fixture");zip.delete();
            File backup=(File)type.getMethod("backupCurrent").invoke(store);
            try{type.getMethod("restore",File.class).invoke(store,backup);throw new AssertionError("100% restore protection failed");}catch(java.lang.reflect.InvocationTargetException expected){check(expected.getCause() instanceof java.io.IOException,"Android restore refuses an active 100% save");}
            check(read(flag).equals("protected-flag")&&read(original).equals("protected-original"),"Android save operations preserve both protected 100% files");
            check(flag.delete(),"Fixture deactivation happens outside the save manager");write(save,"progress-B");type.getMethod("restore",File.class).invoke(store,backup);
            check(read(save).equals("progress-A"),"Android filesystem supports staged save restore");
        }finally{type.getMethod("close").invoke(store);}
    }
    private void write(File f,String value)throws Exception {try(java.io.FileOutputStream out=new java.io.FileOutputStream(f)){out.write(value.getBytes("UTF-8"));}}
    private void screenshot(Context c,String label)throws Exception{android.graphics.Bitmap image=getUiAutomation().takeScreenshot();if(image==null)throw new AssertionError("Screenshot unavailable");try(java.io.FileOutputStream out=new java.io.FileOutputStream(new File(c.getExternalFilesDir(null),"r2-tests-"+label+".png"))){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}android.graphics.Bitmap preview=android.graphics.Bitmap.createScaledBitmap(image,640,Math.max(1,image.getHeight()*640/image.getWidth()),true);try(java.io.FileOutputStream out=new java.io.FileOutputStream(new File(c.getExternalFilesDir(null),"r2-tests-"+label+".preview.jpg"))){preview.compress(android.graphics.Bitmap.CompressFormat.JPEG,45,out);}if(preview!=image)preview.recycle();image.recycle();}
    private void verifyR2(final Context context,final ClassLoader loader,List<View> views)throws Exception {
        waitForIdleSync();screenshot(context,"launcher");
        check("STORM R".contentEquals(context.getApplicationInfo().loadLabel(context.getPackageManager())),"Installed app is named STORM R");
        View optionsTitle=named(views,"More options");List<View> optionViews=new ArrayList<>();if(optionsTitle!=null)collect((View)optionsTitle.getParent(),optionViews);
        check(hasText(optionViews,"Display")&&hasText(optionViews,"Saves")&&hasText(optionViews,"Tools")&&!hasText(optionViews,"Performance"),"Added presets are removed while the stock Performance selector is retained");
        check(hasText(views,"More options"),"Stock launcher contains one compact More options card");
        android.graphics.drawable.Drawable icon=context.getApplicationInfo().loadIcon(context.getPackageManager());android.graphics.Bitmap iconImage=android.graphics.Bitmap.createBitmap(64,64,android.graphics.Bitmap.Config.ARGB_8888);icon.setBounds(0,0,64,64);icon.draw(new android.graphics.Canvas(iconImage));check(android.graphics.Color.alpha(iconImage.getPixel(0,0))==0&&android.graphics.Color.alpha(iconImage.getPixel(32,32))>0,"Circular app icon has transparent corners and an opaque center");iconImage.recycle();
        check(Class.forName("a.g0",true,loader).getDeclaredMethod("setValues",float[].class)!=null,"Patched stock statistics class verifies on Android");
        testSessionDetailsAndClosing(context,loader);
        testDriverSupport(context,loader);
        testNativeSampling(context,loader);
        View display=null;for(View v:views)if(v instanceof TextView&&"Display".contentEquals(((TextView)v).getText()))display=v;
        check(display!=null,"Stock More options card exposes Display");final View displayButton=display;
        runOnMainSync(()->displayButton.performClick());waitForIdleSync();
        android.view.accessibility.AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();
        check(root!=null&&!root.findAccessibilityNodeInfosByText("Opening video").isEmpty(),"Display opens a stock dialog containing the native video switch");
        screenshot(context,"display");
        List<android.view.accessibility.AccessibilityNodeInfo> done=root.findAccessibilityNodeInfosByText("Done");check(!done.isEmpty(),"Display has a Done action");android.graphics.Rect doneBounds=new android.graphics.Rect();done.get(0).getBoundsInScreen(doneBounds);android.util.DisplayMetrics metrics=context.getResources().getDisplayMetrics();check(doneBounds.top>=0&&doneBounds.bottom<=metrics.heightPixels&&doneBounds.height()>=Math.round(metrics.density*32),"Display Done action stays visible at the configured font scale");check(done.get(0).performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK),"Stock settings dialog closes normally");waitForIdleSync();
        testSaveFiles(context,loader);
        testOverlays(context,loader);
        Class<?> math=Class.forName("com.naruto.r2.GameHooks",true,loader);
        check(Math.abs(((Float)math.getMethod("cropScale",float.class,float.class,int.class).invoke(null,2400f,1080f,100))-1.25f)<.001f,"Proportional crop uses equal scale for both dimensions");
        testModernMenu(context,loader);
        testShutdownAndLookup(context,loader);
        Class<?> game=Class.forName("com.naruto.port.NarutoActivity",true,loader);
        for(String bridge:new String[]{"r2ShaderPrepare","r2StatsValues","r2SetTouch","r2SetShowStats","r2ShadowSet"}){boolean exposed=false;for(Method method:game.getDeclaredMethods())if(method.getName().equals(bridge))exposed=java.lang.reflect.Modifier.isPublic(method.getModifiers())&&java.lang.reflect.Modifier.isStatic(method.getModifiers());check(exposed,"Native bridge is accessible to runtime helpers: "+bridge);}

        Method stretch=game.getDeclaredMethod("applyStretch",boolean.class);stretch.setAccessible(true);
        // Deliberately unattached: execution must fail at Context access, never ART verification.
        progress("Verifying the patched game screen method on the UI thread");
        final Throwable[] verifierError=new Throwable[1];
        runOnMainSync(()->{try{Object instance=game.getConstructor().newInstance();try{stretch.invoke(instance,false);throw new AssertionError("Unattached Activity unexpectedly ran");}catch(java.lang.reflect.InvocationTargetException expected){check(expected.getCause() instanceof NullPointerException,"Patched game screen method verifies before reaching its Context dependency");}}catch(Throwable e){verifierError[0]=e;}});
        if(verifierError[0]!=null)throw new RuntimeException(verifierError[0]);
    }
    private void testShutdownAndLookup(Context context,ClassLoader loader)throws Exception {
        final Throwable[] error=new Throwable[1];final Object[] instance={null};Class<?> game=Class.forName("com.naruto.port.NarutoActivity",true,loader),loop=Class.forName("com.naruto.r2.GameLoop",true,loader),hooks=Class.forName("com.naruto.r2.GameHooks",true,loader);
        runOnMainSync(()->{try{instance[0]=game.getConstructor().newInstance();android.os.Handler handler=(android.os.Handler)game.getField("statsHandler").get(instance[0]);Runnable stats=(Runnable)game.getField("statsUpdater").get(instance[0]),prepare=(Runnable)game.getField("prepareUpdater").get(instance[0]);handler.postDelayed(stats,60000);handler.postDelayed(prepare,60000);loop.getMethod("pause",game).invoke(null,instance[0]);check(!handler.hasCallbacks(stats)&&!handler.hasCallbacks(prepare),"Game pause cancels both existing polling tasks");check(Boolean.FALSE.equals(loop.getMethod("tick",int.class,android.view.KeyEvent.Callback.class).invoke(null,2,null)),"Unrelated emblem fade task keeps its original path");}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new RuntimeException(error[0]);
        java.util.concurrent.CountDownLatch finishEngine=new java.util.concurrent.CountDownLatch(1);Thread engine=new Thread(()->{try{finishEngine.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}},"R2-simulated-SDL-thread");engine.start();
        File lockPath=new File(context.getFilesDir(),"r2-shutdown-test-"+java.util.UUID.randomUUID());java.io.RandomAccessFile file=new java.io.RandomAccessFile(lockPath,"rw");java.nio.channels.FileLock lock=file.getChannel().tryLock();Field lockField=hooks.getDeclaredField("gameLock"),fileField=hooks.getDeclaredField("gameLockFile");lockField.setAccessible(true);fileField.setAccessible(true);Field threadField=Class.forName("org.libsdl.app.SDLActivity",true,loader).getDeclaredField("mSDLThread");threadField.setAccessible(true);Object previousThread=threadField.get(null);
        try{lockField.set(null,lock);fileField.set(null,file);hooks.getField("returnToLauncher").setBoolean(null,false);threadField.set(null,engine);runOnMainSync(()->{try{hooks.getMethod("gameStop",Activity.class).invoke(null,instance[0]);}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new RuntimeException(error[0]);check(lock.isValid(),"Save lock remains held while the simulated game thread is alive");finishEngine.countDown();engine.join(5000);long deadline=SystemClock.uptimeMillis()+5000;while(lock.isValid()&&SystemClock.uptimeMillis()<deadline)SystemClock.sleep(20);waitForIdleSync();check(!lock.isValid()&&!engine.isAlive(),"Save lock is released only after the simulated game thread stops");}
        finally{finishEngine.countDown();threadField.set(null,previousThread);if(lock.isValid())lock.release();file.close();lockField.set(null,null);fileField.set(null,null);lockPath.delete();hooks.getField("active").setBoolean(null,true);}
        Class<?> handlerClass=Class.forName("org.libsdl.app.SDLJoystickHandler_API16",true,loader),stick=Class.forName("org.libsdl.app.SDLJoystickHandler_API16$SDLJoystick",true,loader);java.lang.reflect.Constructor<?> constructor=handlerClass.getDeclaredConstructor();constructor.setAccessible(true);Object handler=constructor.newInstance();Field devices=handlerClass.getDeclaredField("mJoysticks");devices.setAccessible(true);java.util.ArrayList<Object> list=(java.util.ArrayList<Object>)devices.get(handler);java.lang.reflect.Constructor<?> stickConstructor=stick.getDeclaredConstructor();stickConstructor.setAccessible(true);Object first=stickConstructor.newInstance(),second=stickConstructor.newInstance();Field deviceId=stick.getDeclaredField("device_id");deviceId.setAccessible(true);deviceId.setInt(first,7);deviceId.setInt(second,42);list.add(first);list.add(second);Method find=handlerClass.getDeclaredMethod("getJoystick",int.class);find.setAccessible(true);check(find.invoke(handler,7)==first&&find.invoke(handler,42)==second,"Allocation-free joystick lookup returns the matching device");check(find.invoke(handler,99)==null,"Missing joystick lookup remains safe");list.remove(first);check(find.invoke(handler,7)==null&&find.invoke(handler,42)==second,"Joystick lookup stays correct after device removal");
    }
    private View named(List<View> views,String text){for(View v:views)if(v instanceof TextView&&text.contentEquals(((TextView)v).getText()))return v;return null;}
    private void testModernMenu(Context context,ClassLoader loader)throws Exception {
        for(String retired:new String[]{"Haptics","FrameGraph","RightInput","ControllerUi","ControllerInput"}){try{Class.forName("com.naruto.r2."+retired,true,loader);throw new AssertionError("Retired class present "+retired);}catch(ClassNotFoundException expected){check(true,"Retired runtime removed: "+retired);}}
        Class<?> menu=Class.forName("com.naruto.r2.GameMenu",true,loader),actions=Class.forName("com.naruto.r2.GameMenu$Actions",true,loader);
        final java.util.List<String> calls=new java.util.ArrayList<>();Object listener=java.lang.reflect.Proxy.newProxyInstance(loader,new Class<?>[]{actions},(proxy,m,args)->{calls.add(m.getName());return null;});
        final android.app.AlertDialog[] dialog=new android.app.AlertDialog[1];final Throwable[] error=new Throwable[1];
        runOnMainSync(()->{try{dialog[0]=(android.app.AlertDialog)menu.getMethod("open",Activity.class,actions).invoke(null,launcher,listener);List<View> views=new ArrayList<>();collect(dialog[0].getWindow().getDecorView(),views);
            check(hasText(views,"Game options")&&hasText(views,"Return to launcher")&&hasText(views,"Resume"),"Modern game menu exposes resume and launcher return");
            check(hasText(views,"Control opacity")&&hasText(views,"Brightness")&&hasText(views,"Touch controls")&&hasText(views,"FPS / statistics size"),"Expanded game menu contains live controls");
            check(!hasText(views,"Rumble")&&!hasText(views,"Performance history")&&!hasText(views,"Export diagnostics"),"Removed features do not appear in the game menu");
            View fps=named(views,"Show FPS");((View)fps.getParent().getParent()).performClick();check(calls.contains("stats"),"Game-menu FPS switch invokes its action");
        }catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new RuntimeException(error[0]);
        waitForIdleSync();screenshot(context,"game-menu");runOnMainSync(()->dialog[0].getButton(-1).performClick());waitForIdleSync();
        check(!dialog[0].isShowing(),"Resume closes the game menu");
        final int[] exits={0};
        runOnMainSync(()->{try{dialog[0]=(android.app.AlertDialog)menu.getMethod("confirmExit",Activity.class,Runnable.class).invoke(null,launcher,(Runnable)()->exits[0]++);check(dialog[0].isShowing(),"Exit confirmation opens before leaving");dialog[0].getButton(-2).performClick();check(exits[0]==0,"Cancelling exit keeps the game running");}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new RuntimeException(error[0]);
        waitForIdleSync();
        runOnMainSync(()->{try{dialog[0]=(android.app.AlertDialog)menu.getMethod("confirmExit",Activity.class,Runnable.class).invoke(null,launcher,(Runnable)()->exits[0]++);dialog[0].getButton(-1).performClick();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new RuntimeException(error[0]);waitForIdleSync();check(exits[0]==1,"Confirmed exit invokes its supplied action exactly once");

    }
    private android.graphics.Bitmap region(View view) {
        waitForIdleSync(); SystemClock.sleep(100);
        final int[] xy=new int[2]; runOnMainSync(()->view.getLocationOnScreen(xy));
        android.graphics.Bitmap screen=getUiAutomation().takeScreenshot();
        android.graphics.Bitmap result=android.graphics.Bitmap.createBitmap(screen,xy[0],xy[1],view.getWidth(),view.getHeight());
        if(result!=screen)screen.recycle(); return result;
    }
    private boolean samePixels(android.graphics.Bitmap left,android.graphics.Bitmap right) {
        if(left.getWidth()!=right.getWidth()||left.getHeight()!=right.getHeight())return false;
        int[] a=new int[left.getWidth()*left.getHeight()],b=new int[a.length];
        left.getPixels(a,0,left.getWidth(),0,0,left.getWidth(),left.getHeight());
        right.getPixels(b,0,right.getWidth(),0,0,right.getWidth(),right.getHeight());
        int changed=0;
        for(int i=0;i<a.length;i++)if(a[i]!=b[i]) {
            int delta=Math.max(Math.abs(android.graphics.Color.red(a[i])-android.graphics.Color.red(b[i])),Math.max(Math.abs(android.graphics.Color.green(a[i])-android.graphics.Color.green(b[i])),Math.abs(android.graphics.Color.blue(a[i])-android.graphics.Color.blue(b[i]))));
            if(delta>8)changed++;
        }
        return changed<=a.length/1000;
    }
    private void testOverlays(Context context,ClassLoader loader)throws Exception {
        Class<?> touchClass=Class.forName("a.l0",true,loader),listenerClass=Class.forName("a.j0",true,loader),render=Class.forName("com.naruto.r2.TouchRender",true,loader),stats=Class.forName("a.g0",true,loader),statsHelper=Class.forName("com.naruto.r2.StatsOverlay",true,loader);
        final android.widget.FrameLayout[] frame={null}; final View[] touchView={null},statsView={null}; final Throwable[] error={null};
        final List<int[]> inputs=new ArrayList<>(); Object listener=java.lang.reflect.Proxy.newProxyInstance(loader,new Class<?>[]{listenerClass},(proxy,method,args)->{if(method.getName().equals("c")){int[] state=new int[7];for(int i=0;i<7;i++)state[i]=(Integer)args[i];inputs.add(state);}return null;});
        android.content.SharedPreferences prefs=context.getSharedPreferences("naruto",0); prefs.edit().remove("r2_stats_scale").remove("stats_collapsed").commit();
        try {
            runOnMainSync(()->{try{
                Class.forName("com.naruto.r2.LauncherMotion",true,loader).getMethod("pause",Activity.class).invoke(null,launcher);
                frame[0]=new android.widget.FrameLayout(launcher);frame[0].setBackgroundColor(android.graphics.Color.BLACK);
                launcher.addContentView(frame[0],new ViewGroup.LayoutParams(-1,-1));
                touchView[0]=(View)touchClass.getConstructor(Activity.class,listenerClass).newInstance(launcher,listener);
                frame[0].addView(touchView[0],new android.widget.FrameLayout.LayoutParams(-1,-1));
                touchClass.getMethod("setOpacity",float.class).invoke(touchView[0],.7f);
            }catch(Throwable e){error[0]=e;}}); if(error[0]!=null)throw new RuntimeException(error[0]);
            waitForIdleSync();check(touchView[0].isHardwareAccelerated(),"Touch cache is tested on a real hardware Canvas");
            Field stateField=touchClass.getField("r2RenderState");render.getMethod("invalidate",touchClass).invoke(null,touchView[0]);
            Object state=stateField.get(touchView[0]);Field enabled=state.getClass().getField("enabled"),recordings=state.getClass().getField("recordings"),hits=state.getClass().getField("hits");
            for(int mask:new int[]{0,8192,4096,32768}) {
                runOnMainSync(()->{try{enabled.setBoolean(state,false);touchClass.getField("D").setInt(touchView[0],mask);touchView[0].invalidate();}catch(Throwable e){error[0]=e;}});
                android.graphics.Bitmap original=region(touchView[0]);
                runOnMainSync(()->{try{enabled.setBoolean(state,true);touchView[0].invalidate();}catch(Throwable e){error[0]=e;}});
                android.graphics.Bitmap cached=region(touchView[0]);
                check(samePixels(original,cached),"Cached stock touch artwork matches original rendering, button mask "+mask);original.recycle();cached.recycle();
            }
            Object[] compactButtons=(Object[])touchClass.getField("a").get(touchView[0]);boolean[] hidden=new boolean[compactButtons.length];
            Object compact=compactButtons[0];float previousX=compact.getClass().getField("j").getFloat(compact);
            for(int i=0;i<compactButtons.length;i++)hidden[i]=compactButtons[i].getClass().getField("p").getBoolean(compactButtons[i]);
            try {
                runOnMainSync(()->{try{for(Object button:compactButtons)button.getClass().getField("p").setBoolean(button,button!=compact);compact.getClass().getField("j").setFloat(compact,touchView[0].getWidth()*.2f);enabled.setBoolean(state,false);touchView[0].invalidate();}catch(Throwable e){error[0]=e;}});
                android.graphics.Bitmap fullLayer=region(touchView[0]);
                runOnMainSync(()->{try{enabled.setBoolean(state,true);touchView[0].invalidate();}catch(Throwable e){error[0]=e;}});android.graphics.Bitmap boundedLayer=region(touchView[0]);
                check(samePixels(fullLayer,boundedLayer),"Compact custom layouts preserve caption/glow pixels and grouped opacity with tighter compositing bounds");fullLayer.recycle();boundedLayer.recycle();
                check(state.getClass().getField("layerArea").getFloat(state)<state.getClass().getField("originalLayerArea").getFloat(state)*.85f,"A compact control layout uses a smaller transparency layer");
            }finally{runOnMainSync(()->{try{for(int i=0;i<compactButtons.length;i++)compactButtons[i].getClass().getField("p").setBoolean(compactButtons[i],hidden[i]);compact.getClass().getField("j").setFloat(compact,previousX);touchView[0].invalidate();}catch(Throwable e){error[0]=e;}});}
            // Restoring the compact fixture legitimately invalidates its cached artwork.
            // Flush that frame before measuring a subsequent unchanged redraw.
            android.graphics.Bitmap restoredLayout=region(touchView[0]);restoredLayout.recycle();
            int recorded=recordings.getInt(state),hit=hits.getInt(state);
            runOnMainSync(()->touchView[0].invalidate());waitForIdleSync();SystemClock.sleep(100);
            check(recordings.getInt(state)==recorded&&hits.getInt(state)>hit,"Unchanged buttons reuse recorded artwork without rebuilding gradients");
            Object stick=touchClass.getField("w").get(touchView[0]);Class<?> stickType=stick.getClass();int oldPointer=stickType.getField("a").getInt(stick);float[] oldStick=new float[7];String[] stickFields={"b","c","d","e","f","g","h"};for(int i=0;i<7;i++)oldStick[i]=stickType.getField(stickFields[i]).getFloat(stick);
            try {
                for(int direction:new int[]{1,-1}) {
                    runOnMainSync(()->{try{float x=touchClass.getField("y").getFloat(touchView[0]),y=touchClass.getField("z").getFloat(touchView[0]),r=touchClass.getField("A").getFloat(touchView[0]);stickType.getField("a").setInt(stick,42);stickType.getField("b").setFloat(stick,x);stickType.getField("c").setFloat(stick,y);stickType.getField("d").setFloat(stick,x+direction*r*.65f);stickType.getField("e").setFloat(stick,y+r*.3f);enabled.setBoolean(state,false);touchView[0].invalidate();}catch(Throwable e){error[0]=e;}});
                    android.graphics.Bitmap stockStick=region(touchView[0]);
                    runOnMainSync(()->{try{enabled.setBoolean(state,true);touchView[0].invalidate();}catch(Throwable e){error[0]=e;}});android.graphics.Bitmap cachedStick=region(touchView[0]);
                    check(samePixels(stockStick,cachedStick),"Cached left-stick artwork matches the stock moving control, direction "+direction);stockStick.recycle();cachedStick.recycle();
                }
                int stickRecords=state.getClass().getField("stickRecordings").getInt(state);
                runOnMainSync(()->{try{stickType.getField("d").setFloat(stick,stickType.getField("b").getFloat(stick));touchView[0].invalidate();}catch(Throwable e){error[0]=e;}});waitForIdleSync();SystemClock.sleep(100);
                check(state.getClass().getField("stickRecordings").getInt(state)==stickRecords,"Moving the knob reuses stick shapes and gradients");
            }finally{runOnMainSync(()->{try{stickType.getField("a").setInt(stick,oldPointer);for(int i=0;i<7;i++)stickType.getField(stickFields[i]).setFloat(stick,oldStick[i]);touchView[0].invalidate();}catch(Throwable e){error[0]=e;}});}
            runOnMainSync(()->{try{touchClass.getField("D").setInt(touchView[0],0);touchView[0].invalidate();}catch(Throwable e){error[0]=e;}});
            android.graphics.Bitmap gameplay=region(touchView[0]);
            runOnMainSync(()->{try{touchClass.getMethod("setEditing",boolean.class).invoke(touchView[0],true);}catch(Throwable e){error[0]=e;}});
            waitForIdleSync();SystemClock.sleep(100);
            runOnMainSync(()->{try{touchClass.getMethod("setEditing",boolean.class).invoke(touchView[0],false);}catch(Throwable e){error[0]=e;}});
            android.graphics.Bitmap afterEditor=region(touchView[0]);
            check(samePixels(gameplay,afterEditor),"Leaving the stock layout editor redraws gameplay even when the layout is unchanged");gameplay.recycle();afterEditor.recycle();
            runOnMainSync(()->{try{
                Object[] buttons=(Object[])touchClass.getField("a").get(touchView[0]);Object button=buttons[1];float x=button.getClass().getField("j").getFloat(button),y=button.getClass().getField("k").getFloat(button);
                touchClass.getField("D").setInt(touchView[0],0);inputs.clear();
                long t=SystemClock.uptimeMillis();
                for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_MOVE,MotionEvent.ACTION_CANCEL}){MotionEvent event=MotionEvent.obtain(t,t+10,action,x,y,0);touchView[0].onTouchEvent(event);event.recycle();}
                check(inputs.size()>=2&&(inputs.get(0)[0]&4096)!=0&&inputs.get(inputs.size()-1)[0]==0,"Stock touch press and cancellation still deliver native button transitions immediately");
                statsView[0]=(View)stats.getConstructor(Activity.class,SharedPreferences.class).newInstance(launcher,prefs);
                statsHelper.getMethod("resize",stats).invoke(null,statsView[0]);
                float d=context.getResources().getDisplayMetrics().density;
                frame[0].addView(statsView[0],new android.widget.FrameLayout.LayoutParams(Math.round(230*d),Math.round(100*d)));
                stats.getMethod("setValues",float[].class).invoke(statsView[0],(Object)new float[]{30,20,2000,40,600,2,60});
            }catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new RuntimeException(error[0]);
            long until=SystemClock.uptimeMillis()+5000;
            while(stats.getField("a").get(statsView[0])==null&&SystemClock.uptimeMillis()<until)SystemClock.sleep(20);
            waitForIdleSync();check(stats.getField("a").get(statsView[0])!=null&&statsView[0].getVisibility()==View.VISIBLE,"Statistics sample publishes after asynchronous memory collection");
            check(Math.abs(statsView[0].getScaleX()-.7f)<.001f&&Math.abs(statsView[0].getScaleY()-.7f)<.001f,"Stock statistics panel defaults to 70 percent in both dimensions");
            runOnMainSync(()->{try{prefs.edit().putFloat("r2_stats_scale",.5f).apply();statsHelper.getMethod("resize",stats).invoke(null,statsView[0]);statsView[0].setX(100000);statsView[0].setY(100000);statsHelper.getMethod("clamp",stats).invoke(null,statsView[0]);}catch(Throwable e){error[0]=e;}});
            waitForIdleSync();check(statsView[0].getScaleX()==.5f&&statsView[0].getX()+statsView[0].getWidth()*.5f<=frame[0].getWidth()+1&&statsView[0].getY()+statsView[0].getHeight()*.5f<=frame[0].getHeight()+1,"Changing panel size keeps its visible bounds on screen");
            runOnMainSync(()->{try{
                float d=context.getResources().getDisplayMetrics().density;long t=SystemClock.uptimeMillis();
                for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP}){MotionEvent e=MotionEvent.obtain(t,t+10,action,statsView[0].getWidth()-16*d,14*d,0);statsView[0].onTouchEvent(e);e.recycle();}
                check(stats.getField("i").getBoolean(statsView[0]),"Stock collapse gesture still works on the resized panel");
            }catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new RuntimeException(error[0]);
            waitForIdleSync();Object statsState=stats.getField("r2StatsState").get(statsView[0]);Field samples=statsState.getClass().getField("memorySamples"),redraws=statsState.getClass().getField("redraws");int beforeSamples=samples.getInt(statsState),beforeRedraws=redraws.getInt(statsState);
            runOnMainSync(()->{try{stats.getMethod("setValues",float[].class).invoke(statsView[0],(Object)new float[]{30,90,3000,80,900,2,60});}catch(Throwable e){error[0]=e;}});SystemClock.sleep(200);waitForIdleSync();
            check(samples.getInt(statsState)==beforeSamples&&redraws.getInt(statsState)==beforeRedraws,"Unchanged collapsed FPS skips memory sampling and panel redraws");
            runOnMainSync(()->{try{long t=SystemClock.uptimeMillis();for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP}){MotionEvent e=MotionEvent.obtain(t,t+10,action,10,10,0);statsView[0].onTouchEvent(e);e.recycle();}}catch(Throwable e){error[0]=e;}});
            until=SystemClock.uptimeMillis()+5000;while(samples.getInt(statsState)==beforeSamples&&SystemClock.uptimeMillis()<until)SystemClock.sleep(20);waitForIdleSync();
            check(!stats.getField("i").getBoolean(statsView[0])&&samples.getInt(statsState)>beforeSamples,"Expanding the panel resumes full statistics without waiting for a new native sample");
            runOnMainSync(()->{try{prefs.edit().putFloat("r2_stats_scale",.7f).apply();statsHelper.getMethod("resize",stats).invoke(null,statsView[0]);statsView[0].setX(16);statsView[0].setY(16);}catch(Throwable e){error[0]=e;}});
            screenshot(context,"statistics");
            runOnMainSync(()->{try{
                stats.getMethod("setValues",float[].class).invoke(statsView[0],(Object)new float[]{30,20,2000,40,600});
                stats.getMethod("setValues",float[].class).invoke(statsView[0],(Object)null);
            }catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new RuntimeException(error[0]);
            SystemClock.sleep(300);waitForIdleSync();check(statsView[0].getVisibility()==View.GONE&&stats.getField("a").get(statsView[0])==null,"Late background samples cannot resurrect a hidden statistics panel");
            runOnMainSync(()->{try{statsHelper.getMethod("sizeDialog",Activity.class,stats).invoke(null,launcher,statsView[0]);}catch(Throwable e){error[0]=e;}});waitForIdleSync();
            check(!getUiAutomation().getRootInActiveWindow().findAccessibilityNodeInfosByText("FPS / statistics size").isEmpty(),"FPS size adjustment uses the existing dialog style");
            sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);waitForIdleSync();
        } finally {
            prefs.edit().remove("r2_stats_scale").remove("stats_collapsed").commit();
            runOnMainSync(()->{if(frame[0]!=null&&frame[0].getParent() instanceof ViewGroup)((ViewGroup)frame[0].getParent()).removeView(frame[0]);});
        }
    }
    private void testSessionDetailsAndClosing(Context context,ClassLoader loader)throws Exception {
        Class<?> details=Class.forName("com.naruto.r2.SessionDetails",true,loader),exit=Class.forName("com.naruto.r2.ExitFeedback",true,loader);
        File external=context.getExternalFilesDir(null),privateFile=new File(context.getFilesDir(),"r2-last-session.txt"),log=new File(external,"naruto.log"),gpu=new File(external,"gpu_info.txt"),shared=new File(external,"session-details.txt");
        File[] files={privateFile,log,gpu,shared};byte[][] previous=new byte[files.length][];
        for(int i=0;i<files.length;i++)if(files[i].isFile())previous[i]=java.nio.file.Files.readAllBytes(files[i].toPath());
        SharedPreferences prefs=context.getSharedPreferences("naruto",0);boolean hadDirectory=prefs.contains("r23_private_test"),hadToken=prefs.contains("r23_token_test");String oldDirectory=prefs.getString("r23_private_test",null),oldToken=prefs.getString("r23_token_test",null);
        final java.util.concurrent.atomic.AtomicReference<Intent> captured=new java.util.concurrent.atomic.AtomicReference<>();
        boolean monitorAdded=false;
        ActivityMonitor monitor=new ActivityMonitor(){@Override public ActivityResult onStartActivity(Intent intent){if(Intent.ACTION_CHOOSER.equals(intent.getAction())){captured.set(intent);return new ActivityResult(Activity.RESULT_CANCELED,null);}return null;}};
        try {
            long now=System.currentTimeMillis();String session="Session started epoch ms: "+(now-1000)+"\nSnapshot epoch ms: "+now+"\nLast foreground Android display rate: 60.00 Hz\nPhase: paused\nApp: STORM R R1\n";
            java.nio.file.Files.write(privateFile.toPath(),session.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            java.nio.file.Files.write(log.toPath(),"0.1 Render resolution 1600x900 (1.25x)\n1.0 AMD optical FG: 2x (asked 2x, screen 121 Hz)\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            java.nio.file.Files.write(gpu.toPath(),"GPU: Turnip Adreno (TM) 750\nvendor 5143, driver test, Vulkan 1.4\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            prefs.edit().putString("r23_private_test","PRIVATE_GAME_PATH_DO_NOT_SHARE").putString("r23_token_test","PRIVATE_TOKEN_DO_NOT_SHARE").commit();
            Method selected=details.getDeclaredMethod("selected",Context.class);selected.setAccessible(true);String current=(String)selected.invoke(null,launcher);
            String report=(String)details.getMethod("prepare",Context.class,String.class).invoke(null,context,current);
            check(report.contains("App: STORM R R1")&&report.contains("Refresh selected:")&&report.contains("Frame generation selected:")&&report.contains("Renderer observations in bounded log excerpt"),"Stock log details include app version and selected graphics settings");
            check(report.contains("60.00 Hz")&&report.contains("2x / 2x / 121 Hz")&&report.contains("1600x900"),"Foreground display observations and final engine reports remain separately labelled");
            check(!report.contains("PRIVATE_GAME_PATH_DO_NOT_SHARE")&&!report.contains("PRIVATE_TOKEN_DO_NOT_SHARE"),"Log details never dump arbitrary preferences or game paths");
            android.net.Uri uri=android.net.Uri.parse("content://"+context.getPackageName()+".logs/session-details.txt");
            try(java.io.InputStream stream=context.getContentResolver().openInputStream(uri)){check(stream!=null&&stream.read()=='S',"Original LogProvider can share the new metadata attachment");}
            try{context.getContentResolver().openInputStream(android.net.Uri.parse("content://"+context.getPackageName()+".logs/r2-last-session.txt"));throw new AssertionError("Private metadata exposed");}catch(java.io.FileNotFoundException expected){check(true,"Provider whitelist still rejects private files");}
            log.setLastModified(now-60000);report=(String)details.getMethod("prepare",Context.class,String.class).invoke(null,context,current);
            check(report.contains("Not established")&&!report.contains("Engine render resolution:"),"Old renderer logs are not presented as observations from the current session");
            log.setLastModified(System.currentTimeMillis());addMonitor(monitor);monitorAdded=true;
            runOnMainSync(()->{try{Class.forName("com.naruto.r2.SessionDetails",true,loader).getMethod("share",launcher.getClass(),boolean.class).invoke(null,launcher,false);}catch(Exception e){throw new RuntimeException(e);}});
            long deadline=SystemClock.uptimeMillis()+5000;while(captured.get()==null&&SystemClock.uptimeMillis()<deadline)SystemClock.sleep(20);
            check(captured.get()!=null,"Original Send log still opens its normal share chooser");
            Intent payload=(Intent)captured.get().getParcelableExtra(Intent.EXTRA_INTENT);java.util.ArrayList<android.net.Uri> attachments=payload.getParcelableArrayListExtra(Intent.EXTRA_STREAM);boolean foundNative=false,foundDetails=false;
            for(android.net.Uri attachment:attachments){foundNative|="naruto.log".equals(attachment.getLastPathSegment());foundDetails|="session-details.txt".equals(attachment.getLastPathSegment());}
            check(foundNative&&foundDetails&&(payload.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION)!=0,"The existing share retains native logs and adds readable session details");
        } finally {
            if(monitorAdded)removeMonitor(monitor);SharedPreferences.Editor restore=prefs.edit();if(hadDirectory)restore.putString("r23_private_test",oldDirectory);else restore.remove("r23_private_test");if(hadToken)restore.putString("r23_token_test",oldToken);else restore.remove("r23_token_test");restore.commit();
            for(int i=0;i<files.length;i++){if(previous[i]==null)java.nio.file.Files.deleteIfExists(files[i].toPath());else java.nio.file.Files.write(files[i].toPath(),previous[i]);}
        }
        File marker=new File(context.getFilesDir(),"r2-exit.pending"),lockFile=new File(context.getFilesDir(),"r2-game.lock");
        try(java.io.RandomAccessFile file=new java.io.RandomAccessFile(lockFile,"rw")) {
            java.nio.channels.FileLock lock=file.getChannel().lock();
            try {
                java.nio.file.Files.write(marker.toPath(),"closing\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                runOnMainSync(()->{try{exit.getMethod("watch",Activity.class).invoke(null,launcher);}catch(Exception e){throw new RuntimeException(e);}});waitForIdleSync();
                check(!getUiAutomation().getRootInActiveWindow().findAccessibilityNodeInfosByText("Closing game").isEmpty(),"Closing feedback uses the launcher's existing dialog style");
                screenshot(context,"closing");
                SystemClock.sleep(200);check(lock.isValid()&&marker.exists(),"Closing feedback waits without releasing the game/save lock");
                runOnMainSync(()->{try{exit.getMethod("destroy",Activity.class).invoke(null,launcher);}catch(Exception e){throw new RuntimeException(e);}});waitForIdleSync();
                check(marker.exists()&&lock.isValid(),"Activity cleanup cancels only its watcher, preserving shutdown state and saves");
                runOnMainSync(()->{try{exit.getMethod("watch",Activity.class).invoke(null,launcher);}catch(Exception e){throw new RuntimeException(e);}});lock.release();
                long deadline=SystemClock.uptimeMillis()+5000;while(marker.exists()&&SystemClock.uptimeMillis()<deadline)SystemClock.sleep(20);waitForIdleSync();
                check(!marker.exists()&&getUiAutomation().getRootInActiveWindow().findAccessibilityNodeInfosByText("Closing game").isEmpty(),"Reopened closing feedback clears only after the owner releases its lock");
            } finally {if(lock.isValid())lock.release();runOnMainSync(()->{try{exit.getMethod("destroy",Activity.class).invoke(null,launcher);}catch(Exception e){throw new RuntimeException(e);}});marker.delete();}
        }
    }
    private byte[] driverZip(String metadata)throws Exception {
        byte[] elf=new byte[64];elf[0]=127;elf[1]='E';elf[2]='L';elf[3]='F';elf[4]=2;elf[5]=1;elf[6]=1;elf[16]=3;elf[18]=(byte)183;
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(java.util.zip.ZipOutputStream zip=new java.util.zip.ZipOutputStream(bytes)){
            zip.putNextEntry(new java.util.zip.ZipEntry("meta.json"));zip.write(metadata.getBytes("UTF-8"));zip.closeEntry();
            zip.putNextEntry(new java.util.zip.ZipEntry("libvulkan_panfrost.so"));zip.write(elf);zip.closeEntry();
            zip.putNextEntry(new java.util.zip.ZipEntry("LICENSE-Mesa.txt"));zip.write("fixture notice".getBytes("UTF-8"));zip.closeEntry();
        }return bytes.toByteArray();
    }
    private void testDriverSupport(Context context,ClassLoader loader)throws Exception {
        Class<?> support=Class.forName("com.naruto.r2.DriverSupport",true,loader);String actual=(String)support.getMethod("gpu").invoke(null);
        check(actual!=null,"ARM64 system-Vulkan probe links and returns without starting the game");
        Method nativeProbe=support.getDeclaredMethod("systemGpu");nativeProbe.setAccessible(true);
        check(nativeProbe.invoke(null) instanceof String,"GPU probe JNI symbol binds and its actual ARM64 implementation executes");
        Field gpu=support.getDeclaredField("gpu");gpu.setAccessible(true);Object previous=gpu.get(null);
        File fixture=new File(context.getFilesDir(),"r24-driver-"+java.util.UUID.randomUUID());fixture.mkdirs();
        Context isolated=new android.content.ContextWrapper(context){@Override public File getFilesDir(){return fixture;}};
        String metadata="{\"schemaVersion\":1,\"name\":\"PanVK-FristOneRR-V.3.7.0\",\"description\":\"Fixture\",\"driverVersion\":\"FristOneRR v3.7.0\",\"minApi\":28,\"libraryName\":\"libvulkan_panfrost.so\"}";
        Method install=support.getMethod("importDriver",Context.class,java.io.InputStream.class),validate=support.getMethod("validate",File.class,int.class,String.class);
        try {
            gpu.set(null,"13b5|Mali-G57 MC2");check((Boolean)Class.forName("a.i",true,loader).getMethod("q").invoke(null),"Stock driver menu/configuration gate permits a detected Mali GPU");
            Object driver=install.invoke(null,isolated,new java.io.ByteArrayInputStream(driverZip(metadata)));String id=(String)driver.getClass().getField("a").get(driver);
            File directory=new File(fixture,"gpu_drivers/"+id);check(new File(directory,"LICENSE-Mesa.txt").isFile(),"Driver import keeps the package's third-party notice");
            check(((String)driver.getClass().getField("c").get(driver)).contains("Experimental Mali"),"Imported PanVK remains explicitly experimental");
            Object same=install.invoke(null,isolated,new java.io.ByteArrayInputStream(driverZip(metadata)));check(id.equals(same.getClass().getField("a").get(same)),"Repeated identical imports reuse the installed package");
            write(new File(directory,"meta.json"),metadata.replace("\"minApi\":28","\"minApi\":\"invalid\""));
            try{validate.invoke(null,directory,37,"13b5|Mali-G57 MC2");throw new AssertionError("Malformed minimum API");}catch(java.lang.reflect.InvocationTargetException e){check(e.getCause() instanceof java.io.IOException,"Malformed Android requirements are rejected rather than silently defaulted");}finally{write(new File(directory,"meta.json"),metadata);}
            try{validate.invoke(null,directory,37,"5143|Adreno 750");throw new AssertionError("PanVK on Adreno");}catch(java.lang.reflect.InvocationTargetException e){check(e.getCause() instanceof java.io.IOException,"Wrong GPU family is rejected before native driver loading");}
            try{validate.invoke(null,directory,37,"13b5|Mali-G710");throw new AssertionError("Unsupported CSF");}catch(java.lang.reflect.InvocationTargetException e){check(e.getCause() instanceof java.io.IOException,"Linked PanVK release rejects its unsupported CSF generation");}
            String radv="{\"schemaVersion\":1,\"name\":\"RADV Xclipse (Mesa 27.0.0-devel-269238e)\",\"driverVersion\":\"Vulkan 1.4.363\",\"minApi\":34,\"libraryName\":\"vulkan.radeon.so\"}";
            File r=new File(fixture,"radv");r.mkdirs();java.nio.file.Files.copy(new File(directory,"libvulkan_panfrost.so").toPath(),new File(r,"vulkan.radeon.so").toPath());write(new File(r,"meta.json"),radv);
            Object rd=validate.invoke(null,r,34,"144d|Samsung Xclipse 920");check(((String)rd.getClass().getField("c").get(rd)).contains("Experimental Xclipse"),"Official RADV metadata and library name are supported for Xclipse");
            try{validate.invoke(null,r,33,"144d|Samsung Xclipse 920");throw new AssertionError("Old Android");}catch(java.lang.reflect.InvocationTargetException e){check(e.getCause() instanceof java.io.IOException,"RADV package minimum Android version is enforced");}
            try(java.io.RandomAccessFile lease=new java.io.RandomAccessFile(new File(fixture,"r2-game.lock"),"rw");java.nio.channels.FileLock lock=lease.getChannel().lock()){
                try{install.invoke(null,isolated,new java.io.ByteArrayInputStream(driverZip(metadata)));throw new AssertionError("Import while running");}catch(java.lang.reflect.InvocationTargetException e){check(e.getCause() instanceof java.io.IOException&&lock.isValid(),"Import is blocked while the engine/save lock is held and cannot release another owner's lock");}
            }
            java.util.List<String> stages=new java.util.ArrayList<>();for(File f:new File(fixture,"gpu_drivers").listFiles())if(f.getName().startsWith(".import-"))stages.add(f.getName());check(stages.isEmpty(),"Driver staging is cleaned after successful and refused imports");
            testDriverWriter(context,loader,support,install,metadata);
        }finally{gpu.set(null,previous);Class.forName("com.naruto.r2.DriverArchive",true,loader).getMethod("discard",File.class).invoke(null,fixture);}
    }
    private void testDriverWriter(Context context,ClassLoader loader,Class<?> support,Method install,String metadata)throws Exception {
        SharedPreferences prefs=context.getSharedPreferences("naruto",0);java.util.Map<String,?> previous=new java.util.HashMap<>(prefs.getAll());
        File ini=new File(context.getExternalFilesDir(null),"naruto.ini"),status=new File(context.getExternalFilesDir(null),"shadowzero.txt");
        byte[] oldIni=ini.isFile()?java.nio.file.Files.readAllBytes(ini.toPath()):null,oldStatus=status.isFile()?java.nio.file.Files.readAllBytes(status.toPath()):null;
        File empty=new File(context.getCacheDir(),"r24-empty-game");empty.mkdirs();File installed=null;Throwable[] failure={null};
        try{
            Object driver=install.invoke(null,context,new java.io.ByteArrayInputStream(driverZip(metadata)));String id=(String)driver.getClass().getField("a").get(driver);
            installed=new File(context.getFilesDir(),"gpu_drivers/"+id);write(status,"supported=0\nname=previous driver\nreason=old failure\n");prefs.edit().putString("gameDirectory",empty.getAbsolutePath()).commit();
            runOnMainSync(()->{try{launcher.getClass().getMethod("p",String.class).invoke(launcher,id);launcher.getClass().getMethod("z").invoke(launcher);}catch(Throwable e){failure[0]=e;}});
            if(failure[0]!=null)throw new RuntimeException(failure[0]);
            check(!status.exists(),"Selecting another driver clears only the old GPU compatibility result");
            String configuration=read(ini);check(configuration.contains("env.NARUTO_GPU_DRIVER_LIBRARY = libvulkan_panfrost.so")&&configuration.contains(id+"/"),"Stock configuration writer carries a Mali package into the existing native custom-driver environment");
            Field gpu=support.getDeclaredField("gpu");gpu.setAccessible(true);Object oldGpu=gpu.get(null);gpu.set(null,"144d|Samsung Xclipse 920");
            try{check((Boolean)Class.forName("a.i",true,loader).getMethod("q").invoke(null),"Stock driver configuration gate also permits detected Xclipse hardware");}finally{gpu.set(null,oldGpu);}
        }finally{
            SharedPreferences.Editor restore=prefs.edit().clear();for(java.util.Map.Entry<String,?> e:previous.entrySet()){Object v=e.getValue();if(v instanceof String)restore.putString(e.getKey(),(String)v);else if(v instanceof Integer)restore.putInt(e.getKey(),(Integer)v);else if(v instanceof Long)restore.putLong(e.getKey(),(Long)v);else if(v instanceof Float)restore.putFloat(e.getKey(),(Float)v);else if(v instanceof Boolean)restore.putBoolean(e.getKey(),(Boolean)v);else if(v instanceof java.util.Set)restore.putStringSet(e.getKey(),(java.util.Set<String>)v);}restore.commit();
            if(oldIni==null)ini.delete();else java.nio.file.Files.write(ini.toPath(),oldIni);if(oldStatus==null)status.delete();else java.nio.file.Files.write(status.toPath(),oldStatus);
            if(installed!=null)Class.forName("com.naruto.r2.DriverArchive",true,loader).getMethod("discard",File.class).invoke(null,installed);empty.delete();
        }
    }
    private void testNativeSampling(Context context,ClassLoader loader)throws Exception {
        Class<?> samples=Class.forName("com.naruto.r2.NativeSamples",true,loader),sourceType=Class.forName("com.naruto.r2.NativeSamples$Source",true,loader),game=Class.forName("com.naruto.port.NarutoActivity",true,loader),hooks=Class.forName("com.naruto.r2.GameHooks",true,loader),startup=Class.forName("com.naruto.r2.StartupFeedback",true,loader);
        Field source=samples.getDeclaredField("source");source.setAccessible(true);Object original=source.get(null);Object[] instance={null};Throwable[] failure={null};
        java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(1),release=new java.util.concurrent.CountDownLatch(1);java.util.concurrent.atomic.AtomicInteger reads=new java.util.concurrent.atomic.AtomicInteger();java.util.concurrent.atomic.AtomicBoolean offMain=new java.util.concurrent.atomic.AtomicBoolean();
        Object fake=java.lang.reflect.Proxy.newProxyInstance(loader,new Class<?>[]{sourceType},(proxy,m,args)->{reads.incrementAndGet();offMain.set(android.os.Looper.myLooper()!=android.os.Looper.getMainLooper());entered.countDown();release.await(5,java.util.concurrent.TimeUnit.SECONDS);return new float[]{30,20,2000,40,600,2,60};});
        runOnMainSync(()->{try{instance[0]=game.getConstructor().newInstance();Method attach=android.content.ContextWrapper.class.getDeclaredMethod("attachBaseContext",Context.class);attach.setAccessible(true);attach.invoke(instance[0],context);}catch(Throwable e){failure[0]=e;}});
        if(failure[0]!=null)throw new RuntimeException(failure[0]);
        try{
            source.set(null,fake);hooks.getField("active").setBoolean(null,true);
            runOnMainSync(()->{try{for(int i=0;i<5;i++)samples.getMethod("request",game).invoke(null,instance[0]);}catch(Throwable e){failure[0]=e;}});
            check(entered.await(5,java.util.concurrent.TimeUnit.SECONDS)&&offMain.get(),"Native statistics read executes on the dedicated worker rather than the UI thread");
            check(reads.get()==1,"Repeated UI polling cannot queue overlapping native samples");
            runOnMainSync(()->{try{samples.getMethod("cancel",game).invoke(null,instance[0]);}catch(Throwable e){failure[0]=e;}});release.countDown();SystemClock.sleep(200);waitForIdleSync();
            String observed=(String)samples.getMethod("observations",game).invoke(null,instance[0]);check(observed.contains("samples 0"),"Cancelled background samples cannot publish into a paused game");
            runOnMainSync(()->{try{startup.getMethod("begin",game).invoke(null,instance[0]);startup.getMethod("prepared",game).invoke(null,instance[0]);startup.getMethod("sample",game,float[].class).invoke(null,instance[0],new float[]{30});}catch(Throwable e){failure[0]=e;}});
            check(!(Boolean)startup.getMethod("waiting",game).invoke(null,instance[0]),"Startup feedback completes after the first valid game FPS observation");
            if(failure[0]!=null)throw new RuntimeException(failure[0]);
        }finally{release.countDown();runOnMainSync(()->{try{samples.getMethod("destroy",game).invoke(null,instance[0]);startup.getMethod("destroy",game).invoke(null,instance[0]);}catch(Exception e){throw new RuntimeException(e);}});source.set(null,original);}
    }
    @Override public void onStart() {
        Thread watchdog=new Thread(()->{try{Thread.sleep(120000);}catch(InterruptedException done){return;}if(completed)return;StringBuilder trace=new StringBuilder();for(java.util.Map.Entry<Thread,StackTraceElement[]> entry:Thread.getAllStackTraces().entrySet())if(entry.getKey().getName().equals("main")||entry.getKey().getName().contains("Instr")){trace.append(entry.getKey().getName()).append('\n');int n=0;for(StackTraceElement frame:entry.getValue()){trace.append(frame).append('\n');if(++n==12)break;}}Bundle status=new Bundle();status.putString("stream",trace+"R2_STALL_STAGE="+lastProgress+"\n");sendStatus(1,status);},"R2-watchdog");watchdog.setDaemon(true);watchdog.start();
        Bundle result=new Bundle(); Context context=getTargetContext();
        SharedPreferences prefs=context.getSharedPreferences("naruto", Context.MODE_PRIVATE);
        boolean originalLogging=prefs.getBoolean("logging_enabled",false);
        boolean originalPt=prefs.getBoolean("menu_ptbr",false);
        try {
            check("R1".equals(context.getPackageManager().getPackageInfo(context.getPackageName(),0).versionName),"Installed version is R1");
            check(!originalLogging,"Logging defaults to off on a fresh install");
            ClassLoader loader=context.getClassLoader();
            Class<?> control=Class.forName("com.naruto.port.LoggingControl",true,loader);
            Method initialize=control.getMethod("initialize",Context.class);
            check(Boolean.TRUE.equals(initialize.invoke(null,context)),"Actual ARM64 logging helper initializes on Android");
            Intent intent=new Intent(); intent.setClassName(context.getPackageName(),"com.naruto.port.LauncherActivity");
            intent.putExtra("trailer",false); intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            progress("Starting launcher with its existing trailer=false test hook");
            launcher=startActivitySync(intent);
            final List<View> views=new ArrayList<>();
            runOnMainSync(new Runnable(){public void run(){collect(launcher.getWindow().getDecorView(),views);}});
            check(hasText(views,"Logging"),"English Logging control is present");
            View stockLog=named(views,"Send log");check(stockLog!=null&&stockLog.getVisibility()==View.VISIBLE&&stockLog.hasOnClickListeners(),"Original Send log action remains visible and connected");
            check(hasText(views,"Modified and Translated by XHYN_PH"),"YouTube footer has the requested text");
            check(!hasText(views,"PT-BR")&&!hasText(views,"Portuguese text"),"PT-BR option is absent");
            verifyR2(context,loader,views);
            View logging=findLoggingSwitch(views);check(logging!=null,"Logging has an interactive switch");
            Field state=logging.getClass().getField("a");
            check(!state.getBoolean(logging),"Logging switch renders off initially");
            touch(logging);
            check(prefs.getBoolean("logging_enabled",false),"Turning Logging on persists its setting");
            check(state.getBoolean(logging),"Logging switch reflects its enabled state");
            Class<?> appLog=Class.forName("com.naruto.port.AppLog",true,loader);
            Method emit=appLog.getMethod("i",String.class,String.class);
            Method trace=appLog.getMethod("stackTrace",Throwable.class);
            Method stderr=appLog.getMethod("println",java.io.PrintStream.class,String.class);
            emit.invoke(null,"R1_NATIVE_TEST","R1_LOGGING_ON_MARKER");
            trace.invoke(null,new RuntimeException("R1_STACK_ON_MARKER"));
            stderr.invoke(null,System.err,"R1_STDERR_ON_MARKER");
            Class<?> sdl=Class.forName("org.libsdl.app.SDL",true,loader);
            Method load=sdl.getMethod("loadLibrary",String.class,Context.class);
            progress("Loading the actual upstream ffmpeg/revolution libraries without starting gameplay");
            load.invoke(null,"ffmpeg",context);load.invoke(null,"revolution",context);
            Method install=control.getDeclaredMethod("nativeInstall");install.setAccessible(true);
            check(Boolean.TRUE.equals(install.invoke(null)),"Logging hooks install against the actual ARM64 engine libraries");
            check(Boolean.TRUE.equals(install.invoke(null)),"Installing native logging hooks again is safe");
            touch(logging);
            check(!prefs.getBoolean("logging_enabled",true),"Turning Logging off persists its setting");
            Method earlyCutoff=control.getDeclaredMethod("nativeEarlyCutoff");earlyCutoff.setAccessible(true);
            check(Boolean.TRUE.equals(earlyCutoff.invoke(null)),"Logging off selects the early cutoff in the engine ABI");
            check(!state.getBoolean(logging),"Logging switch reflects its disabled state");
            emit.invoke(null,"R1_NATIVE_TEST","R1_LOGGING_OFF_MARKER");
            trace.invoke(null,new RuntimeException("R1_STACK_OFF_MARKER"));
            stderr.invoke(null,System.err,"R1_STDERR_OFF_MARKER");
            prefs.edit().putBoolean("menu_ptbr",true).commit();
            final Throwable[] writeError=new Throwable[1];
            runOnMainSync(new Runnable(){public void run(){
                try {launcher.getClass().getMethod("z").invoke(launcher);}catch(Throwable error){writeError[0]=error;}
            }});
            if(writeError[0]!=null)throw new RuntimeException(writeError[0]);
            String config=read(new File(context.getExternalFilesDir(null),"naruto.ini"));
            check(config.matches("(?s).*\\blanguage\\s*=\\s*en(?:\\r?\\n|$).*"),"Config writer forces English even with an old PT-BR preference");
            check(!config.matches("(?s).*\\blanguage\\s*=\\s*pt(?:\\r?\\n|$).*"),"Generated configuration contains no Portuguese language override");
            check(Boolean.TRUE.equals(initialize.invoke(null,context)),"Repeated logging initialization succeeds");
            testController(context,loader);
            result.putString("r1_result","PASS");result.putInt("assertions",assertions);
            result.putString("stream","R2_ANDROID_RESULT=PASS assertions="+assertions+"\n");
            finish(Activity.RESULT_OK,result);
        } catch(Throwable error) {
            StringWriter stack=new StringWriter();error.printStackTrace(new PrintWriter(stack));
            result.putString("r1_result","FAIL");result.putInt("assertions",assertions);
            result.putString("stream","R2_ANDROID_RESULT=FAIL\n"+stack.toString());
            finish(Activity.RESULT_CANCELED,result);
        } finally {
            completed=true;watchdog.interrupt();
            prefs.edit().putBoolean("logging_enabled",originalLogging).putBoolean("menu_ptbr",originalPt).commit();
        }
    }
}
