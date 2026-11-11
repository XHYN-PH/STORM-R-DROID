#!/usr/bin/env python3
# Copyright (c) 2026 XHYN_PH. SPDX-License-Identifier: MIT
"""Developer-only Android system trace capture. No in-app diagnostics exporter."""
import argparse
from pathlib import Path
import subprocess
from datetime import datetime, timezone

def capture(adb, output, seconds):
    if output.exists(): raise ValueError('Output already exists: '+str(output))
    subprocess.run([adb,'get-state'],check=True)
    game=subprocess.run([adb,'shell','pidof','com.naruto.storm4:game'],capture_output=True,text=True,check=False)
    if not game.stdout.strip(): raise RuntimeError('Start the game on the connected phone before recording.')
    def context(phase):
        lines=[phase+' UTC: '+datetime.now(timezone.utc).isoformat()]
        for args in [('getprop','ro.product.model'),('getprop','ro.build.version.sdk'),('dumpsys','thermalservice'),('dumpsys','display')]:
            try:
                result=subprocess.run([adb,'shell',*args],capture_output=True,text=True,timeout=15,check=False)
            except subprocess.TimeoutExpired:
                lines.append(' '.join(args)+'\nUnavailable: command timed out');continue
            text=result.stdout
            if args[0]=='dumpsys':
                words=('thermal status','temperature{','mactive','mcommitted','refresh','modeid','supportedmodes','displaydeviceinfo')
                text='\n'.join(line for line in text.splitlines() if any(word in line.lower() for word in words))
            lines.append(' '.join(args)+'\n'+text[:16000])
        return '\n'.join(lines)+'\n'
    before=context('Before trace')
    config='''buffers { size_kb: 32768 fill_policy: RING_BUFFER }
duration_ms: SECONDS
data_sources { config { name: "linux.ftrace" ftrace_config {
  ftrace_events: "sched/sched_switch"
  ftrace_events: "sched/sched_waking"
  ftrace_events: "power/cpu_frequency"
  ftrace_events: "power/cpu_idle"
  ftrace_events: "power/gpu_frequency"
  atrace_categories: "gfx"
  atrace_categories: "view"
  atrace_categories: "input"
  atrace_categories: "dalvik"
  atrace_categories: "am"
  atrace_categories: "wm"
  atrace_apps: "com.naruto.storm4"
} } }
data_sources { config { name: "linux.process_stats" process_stats_config { scan_all_processes_on_start: true } } }
data_sources { config { name: "android.surfaceflinger.frametimeline" } }
'''.replace('SECONDS',str(seconds*1000))
    remote='/data/misc/perfetto-traces/stormr-stall.perfetto-trace'
    print(f'Recording {seconds} seconds. Reproduce the pause in the same fight or scene.',flush=True)
    subprocess.run([adb,'shell','perfetto','--txt','-c','-','-o',remote],input=config,text=True,check=True)
    output.parent.mkdir(parents=True,exist_ok=True)
    subprocess.run([adb,'pull',remote,str(output)],check=True)
    output.with_suffix(output.suffix+'.context.txt').write_text(before+'\n'+context('After trace'),encoding='utf-8')
    print('Trace saved locally:',output)
    print('Display/thermal context:',output.with_suffix(output.suffix+'.context.txt'))

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('output',type=Path)
    parser.add_argument('--adb',default='adb')
    parser.add_argument('--seconds',type=int,default=45,choices=range(10,61),metavar='10..60')
    args=parser.parse_args()
    capture(args.adb,args.output,args.seconds)
