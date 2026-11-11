/* Copyright (c) 2026 XHYN_PH. SPDX-License-Identifier: MIT */
import com.naruto.r2.RendererReport;
public class RendererReportTest {
 public static void main(String[] args){
  String text="10.0 game thread: from Swap to Swap (most 262.97 ms)\nScan-out: FSR pass, upscaler EASU\n"
   +"AMD optical FG: 2x (asked 2x, screen 60 Hz), lead 5 vsyncs, 3 dropped, 77 shown late\n"
   +"AMD optical FG: 2x (asked 2x, screen 121 Hz), lead 5 vsyncs, 5 dropped, 2 shown late\n"
   +"AMD optical FG: 2x (asked 2x, screen 60 Hz), lead 5 vsyncs, 3 dropped, 79 shown late\nScan-out: FSR pass, upscaler bilinear\n";
  String report=RendererReport.describe(text);
  if(!report.contains("262.97 ms"))throw new AssertionError("Frame maximum");
  if(!report.contains("upscaler path: bilinear"))throw new AssertionError("Effective path");
  if(!report.contains("one FG report: 79 (not a total"))throw new AssertionError("Non-cumulative FG counters");
  if(!report.contains("transitions observed: 2"))throw new AssertionError("Refresh transitions");
  if(!report.contains("not an end-to-end input-latency measurement"))throw new AssertionError("Lead is not latency");
  if(!RendererReport.describe("").contains("frame interval: Unavailable"))throw new AssertionError("Missing metrics");
  System.out.println("PASS: 6 renderer-report assertions");
 }
}
