/* Copyright (c) 2026 XHYN_PH. SPDX-License-Identifier: MIT */
package com.naruto.r2;
import com.naruto.port.NarutoActivity;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;
import java.util.WeakHashMap;

/** Reuse the original preparation panel through the first game-frame observation. */
public final class StartupFeedback {
    private static final WeakHashMap<NarutoActivity,State> states=new WeakHashMap<>();
    private static final class State {boolean prepared,first;long started=SystemClock.uptimeMillis();}
    public static void begin(NarutoActivity a){states.put(a,new State());}
    public static boolean waiting(NarutoActivity a){State s=states.get(a);return s!=null&&!s.first&&GameHooks.prefs(a).getBoolean("menu_stats",true)&&SystemClock.uptimeMillis()-s.started<20000;}
    public static void expire(NarutoActivity a){State s=states.get(a);if(s!=null&&s.prepared&&!waiting(a))hide(a);}
    public static void prepared(NarutoActivity a){
        State s=states.get(a);if(s==null)return;s.prepared=true;
        if(!waiting(a)){hide(a);return;}
        if(a.preparePanel==null)return;
        // Pass input through while the game starts; this status never requires a button press.
        a.preparePanel.setClickable(false);
        if(a.preparePanel.getChildCount()>0&&a.preparePanel.getChildAt(0) instanceof TextView)((TextView)a.preparePanel.getChildAt(0)).setText("Loading game");
        if(a.preparePanel.getChildCount()>1&&a.preparePanel.getChildAt(1) instanceof TextView)((TextView)a.preparePanel.getChildAt(1)).setText("Shader preparation finished. Waiting for gameplay to start.");
        a.prepareBar.setIndeterminate(true);a.prepareCount.setText("Waiting for the first game frame…");a.preparePanel.setVisibility(View.VISIBLE);
    }
    public static void sample(NarutoActivity a,float[] values){State s=states.get(a);if(s==null)return;
        if(values!=null&&values.length>0&&Float.isFinite(values[0])&&values[0]>0)s.first=true;
        if(s.prepared&&(!waiting(a)))hide(a);
    }
    private static void hide(NarutoActivity a){if(a.preparePanel!=null)a.preparePanel.setVisibility(View.GONE);}
    public static void destroy(NarutoActivity a){states.remove(a);}
}
