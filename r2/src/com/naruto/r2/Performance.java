/* Copyright (c) 2026 XHYN_PH. SPDX-License-Identifier: MIT */
package com.naruto.r2;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import com.naruto.port.NarutoActivity;
import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

/** Reaffirm the selected display mode only while gameplay owns foreground focus. */
public final class Performance {
 private static final WeakHashMap<NarutoActivity,State> states=new WeakHashMap<>();
 private static final class State {
  int multiplier,explicit,desiredMode,attempts,modeEvents,recoveries;
  float desiredHz;
  long budgetStarted;
  boolean scheduled;
  final Handler handler=new Handler(Looper.getMainLooper());
  DisplayManager manager;DisplayManager.DisplayListener listener;Runnable recovery;
  WeakReference<SurfaceHolder> holder;SurfaceHolder.Callback callback;
 }
 public static int targetRefresh(int multiplier){return 30*Math.max(2,Math.min(4,multiplier));}
 public static float modeScore(float hz,int target){if(!Float.isFinite(hz)||hz<=0)return Float.MAX_VALUE;float cycles=hz/target;float cadence=Math.abs(cycles-Math.round(cycles));return cadence*1000+Math.abs(hz-target);}
 public static boolean recoveryAllowed(boolean active,boolean focused,boolean finishing,int attempts){return active&&focused&&!finishing&&attempts<2;}
 public static boolean rateMatches(float expected,float observed){return Float.isFinite(expected)&&Float.isFinite(observed)&&expected>0&&observed>0&&Math.abs(expected-observed)<1f;}
 public static int retryBudget(int attempts,long elapsed){return elapsed>=30000?0:attempts;}
 public static void refresh(NarutoActivity a,int multiplier,int explicit){
  State state=states.get(a);if(state==null){state=new State();states.put(a,state);}state.multiplier=multiplier;state.explicit=explicit;apply(a,state);
 }
 private static void apply(NarutoActivity a,State state){
  int target=state.explicit>0?state.explicit:targetRefresh(state.multiplier);a.surfaceFrameRate=target;
  try{Display display=a.getDisplay();if(display==null)return;Display.Mode current=display.getMode(),best=current;float score=Float.MAX_VALUE;
   for(Display.Mode mode:display.getSupportedModes()){if(mode.getPhysicalWidth()!=current.getPhysicalWidth()||mode.getPhysicalHeight()!=current.getPhysicalHeight())continue;float s=state.explicit>0?Math.abs(mode.getRefreshRate()-target):modeScore(mode.getRefreshRate(),target);if(s<score){score=s;best=mode;}}
   WindowManager.LayoutParams lp=a.getWindow().getAttributes();if(lp.preferredDisplayModeId!=best.getModeId()||!rateMatches(best.getRefreshRate(),lp.preferredRefreshRate)){lp.preferredDisplayModeId=best.getModeId();lp.preferredRefreshRate=best.getRefreshRate();a.getWindow().setAttributes(lp);}state.desiredMode=best.getModeId();state.desiredHz=best.getRefreshRate();
   a.surfaceFrameRate=Math.round(best.getRefreshRate());
   View view=GameHooks.findSurface(a.getWindow().getDecorView());
   if(view instanceof SurfaceView){SurfaceHolder holder=((SurfaceView)view).getHolder();watch(a,state,holder);Surface surface=holder.getSurface();if(surface!=null&&surface.isValid())surface.setFrameRate(best.getRefreshRate(),Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE);}
  }catch(RuntimeException ignored){}
 }
 private static void watch(NarutoActivity a,State state,SurfaceHolder holder){
  if(state.holder!=null&&state.holder.get()==holder)return;
  if(state.holder!=null&&state.holder.get()!=null&&state.callback!=null)state.holder.get().removeCallback(state.callback);
  state.holder=new WeakReference<>(holder);WeakReference<NarutoActivity> owner=new WeakReference<>(a);
  state.callback=new SurfaceHolder.Callback(){
   private void ready(){NarutoActivity game=owner.get();if(game!=null&&GameHooks.active&&game.hasWindowFocus()&&!game.isFinishing())apply(game,state);}
   public void surfaceCreated(SurfaceHolder h){ready();}public void surfaceChanged(SurfaceHolder h,int format,int width,int height){ready();}public void surfaceDestroyed(SurfaceHolder h){}
  };holder.addCallback(state.callback);
 }
 public static void resume(NarutoActivity a){
  State state=states.get(a);if(state==null)return;state.attempts=0;state.budgetStarted=android.os.SystemClock.uptimeMillis();
  if(state.listener==null){state.manager=(DisplayManager)a.getSystemService(android.content.Context.DISPLAY_SERVICE);WeakReference<NarutoActivity> owner=new WeakReference<>(a);
   state.listener=new DisplayManager.DisplayListener(){public void onDisplayAdded(int id){}public void onDisplayRemoved(int id){}public void onDisplayChanged(int id){NarutoActivity game=owner.get();if(game!=null&&game.getDisplay()!=null&&game.getDisplay().getDisplayId()==id)changed(game,state);}};
   if(state.manager!=null)state.manager.registerDisplayListener(state.listener,state.handler);
  }
  if(a.hasWindowFocus()&&GameHooks.active)apply(a,state);
 }
 private static void changed(NarutoActivity a,State state){
  if(!GameHooks.active||!a.hasWindowFocus()||a.isFinishing()||a.isDestroyed())return;
  state.modeEvents++;
  long now=android.os.SystemClock.uptimeMillis();int budget=retryBudget(state.attempts,now-state.budgetStarted);if(budget!=state.attempts){state.attempts=budget;state.budgetStarted=now;}
  if(!recoveryAllowed(true,true,false,state.attempts)||state.scheduled)return;
  try{if(a.getDisplay().getMode().getModeId()==state.desiredMode&&rateMatches(state.desiredHz,a.getDisplay().getRefreshRate()))return;}catch(RuntimeException unavailable){return;}
  state.scheduled=true;WeakReference<NarutoActivity> owner=new WeakReference<>(a);
  state.recovery=()->{state.scheduled=false;NarutoActivity game=owner.get();if(game==null||!recoveryAllowed(GameHooks.active,game.hasWindowFocus(),game.isFinishing()||game.isDestroyed(),state.attempts))return;state.attempts++;state.recoveries++;apply(game,state);};
  state.handler.postDelayed(state.recovery,300);
 }
 public static void focus(NarutoActivity a,boolean focused){State state=states.get(a);if(state==null)return;if(focused&&GameHooks.active&&!a.isFinishing()){state.attempts=0;state.budgetStarted=android.os.SystemClock.uptimeMillis();apply(a,state);}else if(state.recovery!=null){state.handler.removeCallbacks(state.recovery);state.scheduled=false;}}
 public static void pause(NarutoActivity a){State state=states.get(a);if(state==null)return;if(state.manager!=null&&state.listener!=null)state.manager.unregisterDisplayListener(state.listener);state.manager=null;state.listener=null;if(state.recovery!=null)state.handler.removeCallbacks(state.recovery);state.scheduled=false;}
 public static String observations(NarutoActivity a){State s=states.get(a);return s==null?"Not initialized":"target "+s.desiredHz+" Hz, foreground display events "+s.modeEvents+", recoveries "+s.recoveries+", retries in current 30-second window "+s.attempts+"/2";}
 public static void destroy(NarutoActivity a){pause(a);State s=states.remove(a);if(s!=null&&s.holder!=null&&s.holder.get()!=null&&s.callback!=null)s.holder.get().removeCallback(s.callback);}
}
