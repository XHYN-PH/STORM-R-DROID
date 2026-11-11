/* Copyright (c) 2026 XHYN_PH. SPDX-License-Identifier: MIT */
package com.naruto.r2;
import com.naruto.port.NarutoActivity;
import android.os.*;
import java.lang.ref.WeakReference;
import java.util.WeakHashMap;
import java.util.concurrent.*;

/** The JNI getter samples clocks/engine counters; only publication belongs on the UI thread. */
public final class NativeSamples {
    private static final Handler main=new Handler(Looper.getMainLooper());
    private static final ExecutorService worker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"STORM native statistics");t.setDaemon(true);return t;});
    private static final WeakHashMap<NarutoActivity,State> states=new WeakHashMap<>();
    private static final class State {long generation;boolean busy;int samples;long longest;}
    interface Source {float[] read();}
    private static Source source=NarutoActivity::r2StatsValues;
    public static void request(NarutoActivity a){
        State existing=states.get(a);if(existing==null){existing=new State();states.put(a,existing);}final State state=existing;
        if(state.busy)return;state.busy=true;final long generation=state.generation;
        WeakReference<NarutoActivity> owner=new WeakReference<>(a);
        worker.execute(()->{
            float[] result=null;long start=SystemClock.elapsedRealtimeNanos();
            try{result=source.read();}catch(LinkageError|RuntimeException unavailable){}
            final long elapsed=SystemClock.elapsedRealtimeNanos()-start;final float[] values=result;
            main.post(()->{
                state.busy=false;NarutoActivity game=owner.get();
                if(game==null||states.get(game)!=state||generation!=state.generation||!GameHooks.active||game.isFinishing()||game.isDestroyed())return;
                state.samples++;state.longest=Math.max(state.longest,elapsed);
                StartupFeedback.sample(game,values);
                if(game.statsView!=null&&GameHooks.prefs(game).getBoolean("menu_stats",true))game.statsView.setValues(values);
            });
        });
    }
    public static void cancel(NarutoActivity a){State state=states.get(a);if(state!=null)state.generation++;}
    public static void destroy(NarutoActivity a){cancel(a);states.remove(a);}
    public static String observations(NarutoActivity a){State s=states.get(a);return s==null?"Not sampled":"Background samples "+s.samples+", longest "+String.format(java.util.Locale.ROOT,"%.3f",s.longest/1000000.0)+" ms";}
}
