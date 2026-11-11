/* Copyright (c) 2026 XHYN_PH. SPDX-License-Identifier: MIT */
package com.naruto.r2;
import java.util.regex.*;

/** Summarize a bounded excerpt on explicit Send log; counters are never summed as rates. */
public final class RendererReport {
    public static String describe(String text){
        double worst=0;int late=0,lead=0,changes=0,last=-1,reports=0;String upscaler="Unavailable";
        Matcher m=Pattern.compile("from Swap to Swap \\(most ([0-9.]+) ms\\)").matcher(text);
        while(m.find())try{worst=Math.max(worst,Double.parseDouble(m.group(1)));}catch(NumberFormatException ignored){}
        m=Pattern.compile("AMD optical FG: \\d+x \\(asked \\d+x, screen (\\d+) Hz\\).*?lead (\\d+) vsyncs, \\d+ dropped, (\\d+) shown late").matcher(text);
        while(m.find())try{reports++;int hz=Integer.parseInt(m.group(1));lead=Integer.parseInt(m.group(2));late=Math.max(late,Integer.parseInt(m.group(3)));if(hz>0){if(last>0&&last!=hz)changes++;last=hz;}}catch(NumberFormatException ignored){}
        m=Pattern.compile("Scan-out: FSR pass, upscaler ([A-Za-z0-9_-]+)").matcher(text);while(m.find())upscaler=m.group(1);
        return "\nRenderer observations in bounded log excerpt\nLast reported upscaler path: "+upscaler
            +"\nLargest reported frame interval: "+(worst>0?worst+" ms":"Unavailable")
            +"\nLargest shown-late counter in one FG report: "+(reports>0?Integer.toString(late):"Unavailable")+" (not a total or measured rate)"
            +"\nLast reported FG lead: "+(reports>0?Integer.toString(lead):"Unavailable")+" vsyncs (not an end-to-end input-latency measurement)"
            +"\nNative screen-rate transitions observed: "+(reports>0?Integer.toString(changes):"Unavailable")
            +"\nThe native 'no frame presented' latency message does not track the separate FG presenter and does not establish zero input delay.\n";
    }
}
