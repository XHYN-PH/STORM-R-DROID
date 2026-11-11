/* Copyright (c) 2026 XHYN_PH. SPDX-License-Identifier: MIT */
package com.naruto.r2;
import a.f0;
import com.naruto.port.NarutoActivity;
import android.app.*;
import android.content.*;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import java.util.WeakHashMap;

public final class GameMenu {
 private static final WeakHashMap<View,Drag> drags=new WeakHashMap<>();
 private static final class Drag {float x,y,startX,startY;boolean moved;}
 public static boolean touch(f0 z,View view,MotionEvent event){int action=event.getActionMasked();Drag drag=drags.get(view);if(drag==null){drag=new Drag();drags.put(view,drag);}
  if(action==MotionEvent.ACTION_DOWN){drag.x=event.getRawX();drag.y=event.getRawY();drag.startX=view.getX();drag.startY=view.getY();drag.moved=false;z.b();}
  else if(action==MotionEvent.ACTION_MOVE){float dx=event.getRawX()-drag.x,dy=event.getRawY()-drag.y;if(Math.abs(dx)+Math.abs(dy)>ModernUi.dp(view.getContext(),8))drag.moved=true;if(drag.moved){view.setX(Math.max(0,Math.min(drag.startX+dx,z.getWidth()-view.getWidth())));view.setY(Math.max(0,Math.min(drag.startY+dy,z.getHeight()-view.getHeight())));z.requestTransparentRegion(view);}}
  else if(action==MotionEvent.ACTION_UP){if(drag.moved)GameHooks.prefs(z.getContext()).edit().putFloat("shadow_emblem_x_v2",view.getX()).putFloat("shadow_emblem_y_v2",view.getY()).apply();else if(GameHooks.prefs(z.getContext()).getBoolean("shadow_emblem_hidden",false))GameHooks.prefs(z.getContext()).edit().putBoolean("shadow_emblem_hidden",false).apply();else show((NarutoActivity)z.getContext());z.b();}
  return true;
 }
 private static void neutral(NarutoActivity a){if(a.controls!=null){long now=SystemClock.uptimeMillis();MotionEvent cancel=MotionEvent.obtain(now,now,MotionEvent.ACTION_CANCEL,0,0,0);a.controls.onTouchEvent(cancel);cancel.recycle();}try{NarutoActivity.r2SetTouch(0,0,0,0,0,0,0);}catch(UnsatisfiedLinkError ignored){}}
 public static void back(NarutoActivity a){if(a.controls==null){exit(a);return;}if(a.controls.o)a.controls.setEditing(false);else show(a);}
 public static void applyTouch(NarutoActivity a){if(a.controls!=null&&GameHooks.prefs(a).getBoolean("r2_hide_touch",false)){a.controls.setEditing(false);a.controls.setVisibility(View.GONE);}}
 public interface Actions {void display();void stats(boolean on);void touch(boolean on);void controller(boolean on);void opacity();void brightness();void edit();void hide();void leave();}
 public static void show(NarutoActivity a){if(a.isFinishing()||a.isDestroyed()||a.controls==null)return;neutral(a);SharedPreferences p=GameHooks.prefs(a);open(a,new Actions(){
  public void display(){screen(a);}public void stats(boolean on){p.edit().putBoolean("menu_stats",on).apply();try{NarutoActivity.r2SetShowStats(on);}catch(UnsatisfiedLinkError ignored){}GameLoop.resume(a);}
  public void touch(boolean on){neutral(a);p.edit().putBoolean("r2_hide_touch",!on).apply();a.r2ApplyControllerMode();}
  public void controller(boolean on){neutral(a);p.edit().putBoolean("touch_with_controller",on).apply();a.r2ApplyControllerMode();}
  public void opacity(){GameMenu.opacity(a);}public void brightness(){GameMenu.brightness(a);}
  public void edit(){p.edit().putBoolean("r2_hide_touch",false).apply();a.r2ApplyControllerMode();a.controls.setVisibility(View.VISIBLE);a.controls.setEditing(true);}
  public void hide(){p.edit().putBoolean("shadow_emblem_hidden",true).apply();a.shadowPanel.b();}public void leave(){confirmExit(a);}
 });}
 public static AlertDialog open(Activity host,Actions actions){SharedPreferences p=GameHooks.prefs(host);LinearLayout body=ModernUi.body(host);final AlertDialog[] owner=new AlertDialog[1];
  body.addView(ModernUi.action(host,"Display mode",screenName(p.getInt("r2_screen",p.getInt("menu_screen_v3",0))),()->{owner[0].dismiss();actions.display();}));
  body.addView(ModernUi.toggle(host,"Show FPS",null,p.getBoolean("menu_stats",true),(v,on)->actions.stats(on)));
  body.addView(ModernUi.action(host,"FPS / statistics size",null,()->{owner[0].dismiss();StatsOverlay.sizeDialog(host,host instanceof NarutoActivity?((NarutoActivity)host).statsView:null);}));
  body.addView(ModernUi.toggle(host,"Touch controls",null,!p.getBoolean("r2_hide_touch",false),(v,on)->actions.touch(on)));
  body.addView(ModernUi.toggle(host,"Touch with controller","Keep touch buttons visible with a controller connected",p.getBoolean("touch_with_controller",false),(v,on)->actions.controller(on)));
  body.addView(ModernUi.action(host,"Control opacity",null,()->{owner[0].dismiss();actions.opacity();}));
  body.addView(ModernUi.action(host,"Brightness",null,()->{owner[0].dismiss();actions.brightness();}));
  body.addView(ModernUi.action(host,"Edit touch layout",null,()->{owner[0].dismiss();actions.edit();}));
  body.addView(ModernUi.action(host,"Hide menu button","Tap its corner to bring it back",()->{owner[0].dismiss();actions.hide();}));
  owner[0]=ModernUi.show(new AlertDialog.Builder(host).setTitle("Game options").setView(ModernUi.scroll(host,body)).setNeutralButton("Return to launcher",(d,w)->actions.leave()).setPositiveButton("Resume",null));return owner[0];
 }
 public static String screenName(int mode){return mode==2?"Crop to fill":mode==1?"Stretch":"Fit · original proportions";}
 private static void screen(NarutoActivity a){int selected=GameHooks.prefs(a).getInt("r2_screen",GameHooks.prefs(a).getInt("menu_screen_v3",0));ModernUi.show(new AlertDialog.Builder(a).setTitle("Display mode").setSingleChoiceItems(new String[]{"Fit · show the full image","Stretch · fill the display","Crop · fill with correct proportions"},Math.max(0,Math.min(2,selected)),(d,w)->{a.r2ApplyStretch(w==1);GameHooks.prefs(a).edit().putInt("r2_screen",w).apply();GameHooks.updateCrop(a);d.dismiss();}).setNegativeButton("Cancel",null));}
 private interface FloatChange{void set(float v);}
 private static void adjust(NarutoActivity a,String title,float initial,float min,float max,FloatChange change){LinearLayout body=ModernUi.body(a);TextView value=ModernUi.text(a,"",15,ModernUi.TEXT);body.addView(value);SeekBar bar=new SeekBar(a);bar.setMax(100);bar.setProgress(Math.round((initial-min)/(max-min)*100));value.setText(Math.round(initial*100)+"%");bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}public void onProgressChanged(SeekBar b,int progress,boolean user){float v=min+(max-min)*progress/100f;value.setText(Math.round(v*100)+"%");if(user)change.set(v);}});body.addView(bar);ModernUi.show(new AlertDialog.Builder(a).setTitle(title).setView(ModernUi.scroll(a,body)).setPositiveButton("Done",null));}
 private static void opacity(NarutoActivity a){adjust(a,"Control opacity",a.controls.getOpacity(),.15f,1,v->a.controls.setOpacity(v));}
 private static void brightness(NarutoActivity a){adjust(a,"Brightness",GameHooks.prefs(a).getFloat("r2_gamma",1),.5f,2,v->{try{if(NarutoActivity.r2ShadowSet("renut_brightness_gamma",Float.toString(v)))GameHooks.prefs(a).edit().putFloat("r2_gamma",v).apply();}catch(UnsatisfiedLinkError ignored){}});}
 public static void confirmExit(NarutoActivity a){confirmExit(a,()->exit(a));}
 public static AlertDialog confirmExit(Activity host,Runnable leave){return ModernUi.show(new AlertDialog.Builder(host).setTitle("Return to launcher?").setMessage("Wait for the game's save indicator to finish before leaving. Unsaved progress will be lost.").setNegativeButton("Keep playing",null).setPositiveButton("Return to launcher",(d,w)->leave.run()));}
 public static void exit(NarutoActivity a){if(a.isFinishing())return;neutral(a);GameLoop.pause(a);GameHooks.returnToLauncher=true;ExitFeedback.begin(a);a.finish();}
}
