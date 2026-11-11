/* Copyright (c) 2026 XHYN_PH. SPDX-License-Identifier: MIT */
package com.naruto.r2;
import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;

/** Shared, compact styling for launcher and game dialogs. */
public final class ModernUi {
 public static final class Builder extends AlertDialog.Builder {public Builder(Context c){super(c);}@Override public AlertDialog show(){return ModernUi.show(this);}}
 public static final int TEXT=0xfff4f0e8,MUTED=0xffb6bcb8,ACCENT=0xffe7b96b,PANEL=0xff141c1a;
 public static int dp(Context c,float value){return Math.round(c.getResources().getDisplayMetrics().density*value);}
 public static GradientDrawable background(Context c,int color,int radius){GradientDrawable bg=new GradientDrawable();bg.setColor(color);bg.setCornerRadius(dp(c,radius));return bg;}
 public static LinearLayout body(Context c){LinearLayout v=new LinearLayout(c);v.setOrientation(1);v.setPadding(dp(c,20),dp(c,8),dp(c,20),dp(c,8));return v;}
 public static TextView text(Context c,String value,int size,int color){TextView t=new TextView(c);t.setText(value);t.setTextSize(size);t.setTextColor(color);return t;}
 public static View action(Context c,String title,String detail,Runnable run){LinearLayout row=body(c);row.setPadding(dp(c,14),dp(c,12),dp(c,14),dp(c,12));row.setMinimumHeight(dp(c,52));TextView name=text(c,title,15,TEXT);row.addView(name);if(detail!=null&&!detail.isEmpty()){TextView subtitle=text(c,detail,12,MUTED);subtitle.setPadding(0,dp(c,4),0,0);row.addView(subtitle);}android.graphics.drawable.RippleDrawable ripple=new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(0x30e7b96b),background(c,0xff1c2722,12),null);row.setBackground(ripple);row.setFocusable(true);row.setOnClickListener(v->run.run());LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(c,6);row.setLayoutParams(lp);return row;}
 public static View toggle(Context c,String title,String detail,boolean checked,android.widget.CompoundButton.OnCheckedChangeListener listener){LinearLayout row=new LinearLayout(c);row.setGravity(Gravity.CENTER_VERTICAL);row.setMinimumHeight(dp(c,48));row.setPadding(dp(c,8),dp(c,8),dp(c,8),dp(c,8));LinearLayout words=new LinearLayout(c);words.setOrientation(1);words.addView(text(c,title,15,TEXT));if(detail!=null)words.addView(text(c,detail,12,MUTED));row.addView(words,new LinearLayout.LayoutParams(0,-2,1));Switch sw=new Switch(c);sw.setChecked(checked);sw.setThumbTintList(ColorStateList.valueOf(ACCENT));sw.setOnCheckedChangeListener(listener);row.addView(sw);row.setOnClickListener(v->sw.setChecked(!sw.isChecked()));return row;}
 public static ScrollView scroll(Context c,View body){ScrollView scroll=new ScrollView(c){@Override protected void onMeasure(int w,int h){super.onMeasure(w,View.MeasureSpec.makeMeasureSpec(Math.min(View.MeasureSpec.getSize(h)>0?View.MeasureSpec.getSize(h):Integer.MAX_VALUE,(int)(getResources().getDisplayMetrics().heightPixels*.62f)),View.MeasureSpec.AT_MOST));}};scroll.setFillViewport(false);scroll.addView(body);return scroll;}
 public static AlertDialog show(AlertDialog.Builder builder){AlertDialog dialog=builder.create();dialog.setOnShowListener(d->{Window w=dialog.getWindow();if(w!=null){w.setBackgroundDrawable(background(dialog.getContext(),PANEL,22));int width=dialog.getContext().getResources().getDisplayMetrics().widthPixels;w.setLayout(Math.min(width-dp(dialog.getContext(),40),dp(dialog.getContext(),540)),-2);w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams lp=w.getAttributes();lp.dimAmount=.55f;w.setAttributes(lp);}for(int id:new int[]{-1,-2,-3}){Button b=dialog.getButton(id);if(b!=null){b.setTextColor(ACCENT);b.setAllCaps(false);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);}}});dialog.show();return dialog;}
}
