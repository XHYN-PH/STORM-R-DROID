/* Copyright (c) 2026 XHYN_PH. SPDX-License-Identifier: MIT */
import com.naruto.r2.*;
import java.util.*;
public class RuntimeMathTest {
 static int checks;
 static void check(boolean value,String text){checks++;if(!value)throw new AssertionError(text);}
 static boolean near(float a,float b){return Math.abs(a-b)<.0001f;}
 public static void main(String[] args){
  check(near(GameHooks.cropScale(2400,1080,100),1.25f),"20:9 crop fills display with uniform scale");
  check(GameHooks.cropScale(1920,1080,100)==1,"16:9 fit is unchanged");
  check(GameHooks.cropScale(2400,1080,0)==1,"Zero crop returns original image");
  check(near(GameHooks.cropScale(2400,1080,50),1.125f),"Crop strength interpolates correctly");
  check(GameHooks.cropScale(0,0,100)==1,"Unlaid-out surfaces remain safe");
  Map<String,String> values=new LinkedHashMap<>();values.put("xnr.renut_brightness_gamma","1.25");
  String text=GameHooks.merge("language = en\nxnr.renut_brightness_gamma = 1\nxnr.renut_brightness_gamma=2\ncustom = 7\n",values);
  check(text.contains("language = en")&&text.contains("custom = 7"),"Config writer preserves unrelated entries");
  check(text.indexOf("xnr.renut_brightness_gamma")==text.lastIndexOf("xnr.renut_brightness_gamma"),"Owned config values cannot duplicate");
  check(text.contains("xnr.renut_brightness_gamma = 1.25"),"Chosen settings replace launcher defaults");
  check(Performance.targetRefresh(1)==60,"Native 30 FPS uses a 60 Hz display target");
  check(Performance.targetRefresh(2)==60,"2x frame generation targets 60 Hz");
  check(Performance.targetRefresh(3)==90&&Performance.targetRefresh(4)==120,"3x and 4x match output rates");
  check(Performance.modeScore(60,60)<Performance.modeScore(120,60),"Prefer lower matching refresh to save display power");
  check(Performance.modeScore(120,60)<Performance.modeScore(90,60),"Fallback prefers an even cadence");
  check(Performance.modeScore(59.94f,60)<Performance.modeScore(90,60),"Fractional 60 Hz modes remain valid");
  check(Performance.modeScore(Float.NaN,60)==Float.MAX_VALUE,"Invalid display rates are rejected");
  check(Performance.recoveryAllowed(true,true,false,0),"Foreground gameplay can recover its selected display mode");
  check(!Performance.recoveryAllowed(false,true,false,0)&&!Performance.recoveryAllowed(true,false,false,0),"Background or unfocused windows cannot force a display change");
  check(!Performance.recoveryAllowed(true,true,true,0),"Closing activities cannot request a display change");
  check(!Performance.recoveryAllowed(true,true,false,2),"Display recovery has a bounded retry budget");
  check(Performance.rateMatches(60,59.94f),"Fractional display rates do not trigger recovery loops");
  check(!Performance.rateMatches(60,120),"A changed refresh can be detected even when the mode ID stays the same");
  check(!Performance.rateMatches(Float.NaN,60),"Unavailable refresh is not a valid match");
  check(Performance.retryBudget(2,29999)==2,"Repeated display requests stay bounded within the retry window");
  check(Performance.retryBudget(2,30000)==0,"A later independent mode change gets a fresh recovery budget");
  System.out.println("PASS: "+checks+" display/configuration/performance assertions");
 }
}
