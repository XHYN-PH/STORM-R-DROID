/* Copyright (c) 2026 XHYN_PH. SPDX-License-Identifier: MIT */
package com.naruto.r2;

import a.g0;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ActivityManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.system.Os;
import android.system.OsConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Stock panel appearance and gestures, with adjustable scale and asynchronous sampling. */
public final class StatsOverlay {
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "STORM statistics"); thread.setDaemon(true); return thread;
    });
    public static final class State {
        long generation;
        boolean busy;
        Labels labels;
        float[] latest;
        public int memorySamples,redraws;
        final float[] hsv = new float[3];
    }
    private static final class Labels {
        FpsText fps;
        boolean full;
        String cpu="CPU -- MHz",gpu="GPU -- MHz",ram="RAM --";
        final String[] percentages = {"--","--","--"};
        final float[] usage = {-1,-1,-1};
        boolean same(Labels other,boolean compact) {
            if(other==null)return false;
            if(compact)return fps.compact.equals(other.fps.compact);
            if(full!=other.full||!fps.heading.equals(other.fps.heading)||!fps.detail.equals(other.fps.detail)||!cpu.equals(other.cpu)||!gpu.equals(other.gpu)||!ram.equals(other.ram))return false;
            for(int i=0;i<3;i++)if(Float.floatToIntBits(usage[i])!=Float.floatToIntBits(other.usage[i])||!percentages[i].equals(other.percentages[i]))return false;
            return true;
        }
    }
    private static State state(g0 view) {
        if (view.r2StatsState == null) view.r2StatsState = new State();
        return view.r2StatsState;
    }
    public static float scale(float value) { return Float.isFinite(value) ? Math.max(.5f, Math.min(1.25f, value)) : .7f; }
    public static void sizeDialog(Activity host, g0 panel) {
        LinearLayout body = ModernUi.body(host);
        TextView label = ModernUi.text(host,"",15,ModernUi.TEXT); body.addView(label);
        SeekBar bar = new SeekBar(host); bar.setMax(75);
        int initial = Math.round(scale(GameHooks.prefs(host).getFloat("r2_stats_scale",.7f))*100);
        label.setText(initial+"%"); bar.setProgress(initial-50);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onStartTrackingTouch(SeekBar b) {}
            public void onStopTrackingTouch(SeekBar b) {}
            public void onProgressChanged(SeekBar b,int progress,boolean user) {
                int percent = progress+50; label.setText(percent+"%");
                if (user) { GameHooks.prefs(host).edit().putFloat("r2_stats_scale",percent/100f).apply(); if (panel!=null) resize(panel); }
            }
        });
        body.addView(bar);
        ModernUi.show(new AlertDialog.Builder(host).setTitle("FPS / statistics size").setView(ModernUi.scroll(host,body))
            .setNeutralButton("Reset",(dialog,which)-> { GameHooks.prefs(host).edit().remove("r2_stats_scale").apply(); if(panel!=null)resize(panel); })
            .setPositiveButton("Done",null));
    }
    public static void resize(g0 view) {
        float scale = scale(view.f.getFloat("r2_stats_scale", .7f));
        view.setPivotX(0); view.setPivotY(0);
        view.setScaleX(scale); view.setScaleY(scale);
        view.post(() -> clamp(view));
    }
    public static void clamp(g0 view) {
        if (!(view.getParent() instanceof ViewGroup)) return;
        ViewGroup parent = (ViewGroup)view.getParent();
        if (parent.getWidth() <= 0 || parent.getHeight() <= 0) return;
        view.setX(Math.max(0, Math.min(view.getX(), parent.getWidth() - view.getWidth()*view.getScaleX())));
        view.setY(Math.max(0, Math.min(view.getY(), parent.getHeight() - view.getHeight()*view.getScaleY())));
        parent.requestTransparentRegion(view);
    }
    public static void cancel(g0 view) {
        if (view != null) { state(view).generation++;state(view).latest=null; }
    }
    public static void layoutChanged(g0 view) {State state=state(view);state.generation++;schedule(view);}
    public static void update(g0 view, float[] values) {
        State state = state(view);
        if (values == null) {
            state.generation++; state.labels = null; state.latest=null; view.a = null;
            view.setVisibility(View.GONE); return;
        }
        if (values.length < 5) return;
        state.latest=values.clone();
        if(view.i&&state.labels!=null&&state.labels.fps.sameReadout(values)){view.a=state.latest;return;}
        schedule(view);
    }
    private static void schedule(g0 view) {
        State state=state(view);if(state.busy||state.latest==null)return;
        if(view.i&&state.labels!=null&&state.labels.fps.sameReadout(state.latest))return;
        state.busy = true;
        final long generation = state.generation;
        final float[] snapshot = state.latest;
        final boolean compact=view.i;
        final WeakReference<g0> reference = new WeakReference<>(view);
        final ActivityManager manager = view.d;
        final String resolution = view.v;
        worker.execute(() -> {
            float ramPercent = -1, ramMb = -1;
            try {
                if (!compact&&manager != null) {
                    ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
                    manager.getMemoryInfo(memory);
                    if (memory.totalMem > 0) ramPercent = (memory.totalMem-memory.availMem)*100f/memory.totalMem;
                }
                if(!compact)try (BufferedReader reader = new BufferedReader(new FileReader("/proc/self/statm"), 128)) {
                    String line = reader.readLine();
                    if (line != null) ramMb = residentPages(line) * (float)Os.sysconf(OsConstants._SC_PAGESIZE) / 1048576f;
                }
            } catch (IOException | RuntimeException ignored) { }
            Labels labels = labels(snapshot, ramPercent, ramMb, resolution,!compact);
            final float memoryPercent = ramPercent, memoryMb = ramMb;
            main.post(() -> {
                g0 target = reference.get();
                if (target == null) return;
                state.busy = false;
                if (state.generation != generation||target.i!=compact) {schedule(target);return;}
                if(!compact)state.memorySamples++;
                boolean changed=!labels.same(state.labels,compact)||target.getVisibility()!=View.VISIBLE;
                state.labels = labels; target.a = snapshot;
                target.b = memoryPercent; target.c = memoryMb;
                if(changed){target.setVisibility(View.VISIBLE);target.invalidate();state.redraws++;}
                if(state.latest!=snapshot)schedule(target);
            });
        });
    }
    static long residentPages(String line) {
        int index = 0;
        while (index < line.length() && !Character.isWhitespace(line.charAt(index))) index++;
        while (index < line.length() && Character.isWhitespace(line.charAt(index))) index++;
        long result = 0;
        while (index < line.length() && Character.isDigit(line.charAt(index))) result = result*10 + line.charAt(index++)-'0';
        return result;
    }
    private static String format(String pattern, Object... args) { return String.format(Locale.ROOT, pattern, args); }
    private static String clock(String name, float value) {
        return !Float.isFinite(value)||value <= 0 ? name+" -- MHz" : value >= 1000 ? format("%s %.2f GHz",name,value/1000) : format("%s %.0f MHz",name,value);
    }
    private static Labels labels(float[] values, float ramPercent, float ramMb, String resolution,boolean full) {
        Labels result = new Labels();
        result.fps=new FpsText(values,resolution);result.full=full;if(!full)return result;
        result.cpu = clock("CPU",values[2]); result.gpu = clock("GPU",values[4]);
        result.ram = ramMb >= 1024 ? format("RAM %.1f GB",ramMb/1024) : ramMb >= 0 ? format("RAM %.0f MB",ramMb) : "RAM --";
        result.usage[0] = values[1]; result.usage[1] = values[3]; result.usage[2] = ramPercent;
        for (int i=0;i<3;i++) result.percentages[i] = result.usage[i] >= 0 ? format("%.0f%%",result.usage[i]) : "--";
        return result;
    }
    public static void draw(g0 view, Canvas canvas) {
        State state = state(view); Labels labels = state.labels;
        if (labels == null) return;
        float w=view.getWidth(), h=view.getHeight(), d=view.g;
        RectF rect=view.o; rect.set(0,0,w,h);
        Paint text=view.m;
        if (view.i) {
            canvas.drawRoundRect(rect,h/2,h/2,view.j); fit(text,labels.fps.compact,13*d,w-8*d);
            canvas.drawText(labels.fps.compact,w/2,h/2-(text.ascent()+text.descent())/2,text); return;
        }
        canvas.drawRoundRect(rect,12*d,12*d,view.j); fit(text,labels.fps.heading,13*d,w-42*d);
        canvas.drawText(labels.fps.heading,(w-22*d)/2,16*d,text);
        text.setTextSize(9.5f*d);canvas.drawText(labels.fps.detail,w/2,29*d,text);
        canvas.drawCircle(w-16*d,14*d,9*d,view.n); text.setTextSize(14*d);
        canvas.drawText("–",w-16*d,14*d-(text.ascent()+text.descent())/2,text);
        float y=56*d;
        gauge(view,canvas,state,labels,0,w*.18f,y,labels.cpu);
        gauge(view,canvas,state,labels,1,w*.5f,y,labels.gpu);
        gauge(view,canvas,state,labels,2,w*.82f,y,labels.ram);
    }
    private static void fit(Paint paint,String text,float size,float width){paint.setTextSize(size);float measured=paint.measureText(text);if(measured>width&&width>0)paint.setTextSize(size*width/measured);}
    private static void gauge(g0 view,Canvas canvas,State state,Labels labels,int index,float x,float y,String label) {
        float d=view.g, r=18*d; RectF rect=view.o; rect.set(x-r,y-r,x+r,y+r);
        view.k.setStrokeWidth(5*d); view.l.setStrokeWidth(5*d);
        canvas.drawOval(rect,view.k);
        float usage=labels.usage[index];
        if (usage >= 0) {
            state.hsv[0]=(1-Math.max(0,Math.min(1,usage/100)))*120; state.hsv[1]=.85f; state.hsv[2]=1;
            view.l.setColor(Color.HSVToColor(state.hsv)); canvas.drawArc(rect,-90,Math.min(usage,100)*3.6f,false,view.l);
        }
        Paint text=view.m; text.setTextSize(12*d);
        canvas.drawText(labels.percentages[index],x,y-(text.ascent()+text.descent())/2,text);
        text.setTextSize(9.5f*d); canvas.drawText(label,x,y+r+13*d,text);
    }
}
