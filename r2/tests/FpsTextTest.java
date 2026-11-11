/* Copyright (c) 2026 XHYN_PH. SPDX-License-Identifier: MIT */
import com.naruto.r2.FpsText;
public final class FpsTextTest {
 static int checks;
 static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
 public static void main(String[] args){
  FpsText measured=new FpsText(new float[]{30,20,2000,40,600,2,60},"900p");
  check(measured.heading.equals("Game 30 FPS · Output 60 FPS"),"Generated output is never labelled as game FPS");
  check(measured.compact.equals("G 30 / O 60"),"Compact panel distinguishes game and output");
  check(measured.detail.equals("2× FG · 900p"),"Multiplier and resolution have their own label");
  FpsText estimated=new FpsText(new float[]{30,0,0,0,0,2},"720p");
  check(estimated.heading.contains("Output ~60 FPS"),"Calculated output is explicitly estimated");
  check(!measured.sameReadout(new float[]{30,0,0,0,0,2}),"Measured and estimated output cannot share the same cached label");
  check(measured.sameReadout(new float[]{30.1f,90,3000,80,900,2,60.1f}),"Hidden CPU/GPU data and sub-rounded FPS changes do not redraw collapsed mode");
  FpsText unknown=new FpsText(new float[]{-1,0,0,0,0,2,0},"900p");
  check(unknown.heading.contains("Game -- FPS")&&unknown.heading.contains("Output -- FPS"),"Unavailable values remain unavailable");
  check(new FpsText(new float[]{30,0,0,0,0,0,0},"900p").heading.equals("Game 30 FPS"),"FG off does not invent generated frames");
  check(new FpsText(new float[]{30,0,0,0,0,-1,0},"900p").detail.contains("unavailable"),"FG failure is distinguishable from off");
  FpsText fractional=new FpsText(new float[]{29.6f,0,0,0,0,4,0},"900p");
  check(!fractional.sameReadout(new float[]{30.1f,0,0,0,0,4,0}),"Estimated output changes are detected even when rounded game FPS is unchanged");
  System.out.println("PASS: "+checks+" game/output FPS assertions");
 }
}
