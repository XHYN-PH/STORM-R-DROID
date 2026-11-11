/* Copyright (c) 2026 XHYN_PH. SPDX-License-Identifier: MIT */
package com.naruto.r2;
import android.animation.Animator;
import android.app.Activity;
import android.content.Context;
import android.view.*;
import java.util.*;
public final class LauncherMotion {
 private static boolean active=true;
 private static final Map<Context,List<Animator>> animations=new WeakHashMap<>();
 public static void register(Context c,Animator animator){List<Animator> list=animations.get(c);if(list==null){list=new ArrayList<>();animations.put(c,list);}list.add(animator);}
 public static void registerAnimator(Animator animator){if(animator instanceof android.animation.ObjectAnimator){Object target=((android.animation.ObjectAnimator)animator).getTarget();if(target instanceof View)register(((View)target).getContext(),animator);}}
 public static void registerView(View v,Animator animator){register(v.getContext(),animator);}
 public static void schedule(View v){if(active&&v.isShown()&&v.getWindowVisibility()==View.VISIBLE)v.postInvalidateOnAnimation();}
 private static void invalidate(View v){v.invalidate();if(v instanceof ViewGroup){ViewGroup group=(ViewGroup)v;for(int i=0;i<group.getChildCount();i++)invalidate(group.getChildAt(i));}}
 public static void pause(Activity a){active=false;List<Animator> list=animations.get(a);if(list!=null)for(Animator animator:list)animator.pause();}
 public static void resume(Activity a){GameHooks.refreshPrefs(a);active=true;List<Animator> list=animations.get(a);if(list!=null)for(Animator animator:list)animator.resume();invalidate(a.getWindow().getDecorView());ExitFeedback.watch(a);}
 public static void destroy(Activity a){ExitFeedback.destroy(a);List<Animator> list=animations.remove(a);if(list!=null)for(Animator animator:list)animator.cancel();}
}
