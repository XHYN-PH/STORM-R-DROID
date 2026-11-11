#!/usr/bin/env python3
# Copyright (c) 2026 XHYN_PH. SPDX-License-Identifier: MIT
"""Validate sources and isolated save operations before assembling an R2 APK."""
import argparse,subprocess,sys,shutil,re,hashlib
from pathlib import Path
ROOT=Path(__file__).resolve().parent
p=argparse.ArgumentParser(description=__doc__)
for key in ['android-jar','build-tools','upstream','apktool']:p.add_argument('--'+key,type=Path,required=True)
p.add_argument('--r1-decoded',type=Path)
a=p.parse_args();work=ROOT/'.build';work.mkdir(exist_ok=True)
def run(*parts):subprocess.run([str(x) for x in parts],check=True)
run(sys.executable,ROOT/'tests/test_linkage.py')
# Each fixture is new and outside any real app save tree.
run('java','--module','jdk.compiler/com.sun.tools.javac.Main','-d',work/'save-tests',ROOT/'src/com/naruto/r2/SaveFiles.java',ROOT/'tests/SaveFilesTest.java')
run('java','-cp',work/'save-tests','SaveFilesTest')
run('java','--module','jdk.compiler/com.sun.tools.javac.Main','-d',work/'session-tests',ROOT/'src/com/naruto/r2/SessionFiles.java',ROOT/'tests/SessionFilesTest.java')
run('java','-cp',work/'session-tests','SessionFilesTest')
run('java','--module','jdk.compiler/com.sun.tools.javac.Main','-d',work/'fps-tests',ROOT/'src/com/naruto/r2/FpsText.java',ROOT/'tests/FpsTextTest.java')
run('java','-cp',work/'fps-tests','FpsTextTest')
run('java','--module','jdk.compiler/com.sun.tools.javac.Main','-d',work/'driver-tests',ROOT/'src/com/naruto/r2/DriverPolicy.java',ROOT/'src/com/naruto/r2/DriverArchive.java',ROOT/'tests/DriverTest.java')
run('java','-cp',work/'driver-tests','DriverTest')
run('java','--module','jdk.compiler/com.sun.tools.javac.Main','-d',work/'report-tests',ROOT/'src/com/naruto/r2/RendererReport.java',ROOT/'tests/RendererReportTest.java')
run('java','-cp',work/'report-tests','RendererReportTest')
run(sys.executable,ROOT/'compile_runtime.py','--android-jar',a.android_jar,'--build-tools',a.build_tools,'--upstream',a.upstream,'--apktool',a.apktool)
run('java','--module','jdk.compiler/com.sun.tools.javac.Main','-cp',str(work/'runtime/classes')+':'+str(a.android_jar),'-d',work/'runtime-tests',ROOT/'tests/RuntimeMathTest.java')
run('java','-cp',str(work/'runtime-tests')+':'+str(work/'runtime/classes')+':'+str(a.android_jar),'RuntimeMathTest')
if a.r1_decoded is None:
 print('PASS: R2 host/source checks complete; decoded source checks run before APK assembly in build_r1.py.');sys.exit(0)
decoded=work/'preflight-decoded';shutil.rmtree(decoded,ignore_errors=True);shutil.copytree(a.r1_decoded,decoded)
run(sys.executable,ROOT/'customize.py',decoded)
# Parameters in these stock methods are deliberately reused; their hooks must run before that reuse.
for relative,signature,needle in [
 ('com/naruto/port/NarutoActivity.smali','.method private applyStretch(Z)V','GameHooks;->stockScreen'),
 ('com/naruto/port/NarutoActivity.smali','.method private applyControllerMode()V','GameMenu;->applyTouch')]:
 source=(decoded/'smali'/relative).read_text();body=re.search(re.escape(signature)+r'\n.*?^\.end method$',source,re.M|re.S).group()
 before_hook=body.split(needle,1)[0]
 if re.search(r'^    (?:iget[^ ]*|move-result[^ ]*|const[^ ]*) p[01](?:,|$)',before_hook,re.M):raise AssertionError('Hook uses a clobbered stock parameter: '+relative)
run(sys.executable,ROOT/'verify_linkage.py',decoded)
run(sys.executable,ROOT.parent/'r1/verify_sources.py','--original',a.upstream,'--decoded',decoded,'--version-name','R1','--version-code','122')
# No excluded feature should be exposed under another label.
code='\n'.join(f.read_text() for f in (ROOT/'src').rglob('*.java'))
for unsupported in ['shader_prewarm','controller_profile','layout_profile','settings_backup','Export diagnostics','Performance history','class RightInput','class Haptics','class ControllerUi','class FrameGraph','class ControllerInput']:
 if unsupported in code:raise AssertionError('Excluded feature found: '+unsupported)
# Rollbacks are byte-for-byte equal to the verified R1 source.
for relative in ['com/naruto/port/ControllerMapActivity.smali','org/libsdl/app/SDLControllerManager.smali']:
 if (decoded/'smali'/relative).read_bytes()!=(a.r1_decoded/'smali'/relative).read_bytes():raise AssertionError('Stock rollback changed: '+relative)
import overlay_patches
overlay_patches.verify_stock(a.r1_decoded,decoded)
print('PASS: R2 source preflight complete. No distributable APK has been assembled by this command.')
