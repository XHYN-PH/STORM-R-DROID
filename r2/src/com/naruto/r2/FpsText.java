/* Copyright (c) 2026 XHYN_PH. SPDX-License-Identifier: MIT */
package com.naruto.r2;

/** Keep game speed distinct from the renderer's generated-output metric. */
public final class FpsText {
    public final String heading,compact,detail;
    private final long key;
    public static long key(float[] values) {
        float game=values.length>0?values[0]:-1,multiplier=values.length>5?values[5]:0,output=values.length>6?values[6]:0;
        boolean g=Float.isFinite(game)&&game>=0,o=Float.isFinite(output)&&output>0,fg=Float.isFinite(multiplier)&&multiplier>1;
        long h=g?Math.round(game):-1;h=h*10007+(fg?(o?Math.round(output):g?Math.round(game*multiplier):0):0);h=h*31+(fg?Math.round(multiplier):0);
        return h*16+(g?1:0)+(o?2:0)+(fg?4:0)+(multiplier<0?8:0);
    }
    public boolean sameReadout(float[] values){return key==key(values);}
    public FpsText(float[] values,String resolution) {
        key=key(values);float game=values[0],multiplier=values.length>5?values[5]:0,output=values.length>6?values[6]:0;
        boolean known=Float.isFinite(game)&&game>=0,measured=Float.isFinite(output)&&output>0,fg=Float.isFinite(multiplier)&&multiplier>1;
        String g=known?Integer.toString(Math.round(game)):"--";
        if(fg) {
            String o=measured?Integer.toString(Math.round(output)):known?"~"+Math.round(game*multiplier):"--";
            heading="Game "+g+" FPS · Output "+o+" FPS";compact="G "+g+" / O "+o;
            detail=Math.round(multiplier)+"× FG · "+resolution;
        } else {
            heading="Game "+g+" FPS";compact=g+" FPS"+(multiplier<0?" !":"");
            detail=(multiplier<0?"FG unavailable":"FG Off")+" · "+resolution;
        }
    }
}
