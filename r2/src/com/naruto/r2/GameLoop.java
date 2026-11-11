/* Copyright (c) 2026 XHYN_PH. SPDX-License-Identifier: MIT */
package com.naruto.r2;
import android.os.SystemClock;
import android.view.*;
import com.naruto.port.NarutoActivity;

/** Poll only while useful; lifecycle hooks stop and resume the existing tasks. */
public final class GameLoop {
 public static boolean tick(int kind,KeyEvent.Callback owner){if(kind!=0&&kind!=1)return false;NarutoActivity a=(NarutoActivity)owner;if(a.isFinishing()||a.isDestroyed()||!GameHooks.active)return true;
  if(kind==1){StartupFeedback.expire(a);boolean stats=GameHooks.prefs(a).getBoolean("menu_stats",true);if(stats||StartupFeedback.waiting(a)){NativeSamples.request(a);a.statsHandler.postDelayed(a.statsUpdater,StartupFeedback.waiting(a)?250:1000);}if(!stats&&a.statsView!=null)a.statsView.setValues(null);return true;}
  try{int[] state=NarutoActivity.r2ShaderPrepare();int done=state!=null&&state.length>=2?state[0]:0,total=state!=null&&state.length>=2?state[1]:0;
   if(total>0&&done<total){a.prepareSeen=true;a.preparePanel.setVisibility(View.VISIBLE);a.prepareBar.setMax(total);a.prepareBar.setProgress(Math.max(0,done));a.prepareCount.setText(done+" of "+total+"  ("+(Math.max(0,done)*100L/total)+"%)");}
   else if((total>0&&done>=total)||a.prepareSeen||SystemClock.uptimeMillis()-a.prepareStarted>20000){GameHooks.preparationDone=true;StartupFeedback.prepared(a);return true;}
   a.statsHandler.postDelayed(a.prepareUpdater,250);
  }catch(UnsatisfiedLinkError ignored){GameHooks.preparationDone=true;}
  return true;
 }
 public static void resume(NarutoActivity a){GameHooks.refreshPrefs(a);GameHooks.active=true;SessionDetails.resume(a);Performance.resume(a);if(a.statsView==null)return;a.statsHandler.removeCallbacks(a.statsUpdater);if(GameHooks.prefs(a).getBoolean("menu_stats",true)||StartupFeedback.waiting(a))a.statsHandler.post(a.statsUpdater);else a.statsView.setValues(null);a.statsHandler.removeCallbacks(a.prepareUpdater);if(!GameHooks.preparationDone&&a.preparePanel!=null)a.statsHandler.post(a.prepareUpdater);}
 public static void destroy(NarutoActivity a){pause(a);Performance.destroy(a);NativeSamples.destroy(a);StartupFeedback.destroy(a);a.statsHandler.removeCallbacksAndMessages(null);}
 public static void pause(NarutoActivity a){GameHooks.active=false;a.statsHandler.removeCallbacks(a.statsUpdater);a.statsHandler.removeCallbacks(a.prepareUpdater);StatsOverlay.cancel(a.statsView);NativeSamples.cancel(a);SessionDetails.pause(a);Performance.pause(a);}
}
