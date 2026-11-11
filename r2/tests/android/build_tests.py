#!/usr/bin/env python3
# Copyright (c) 2026 XHYN_PH. SPDX-License-Identifier: MIT
"""Build the separate, test-only instrumentation APK. Never included in R1."""
import argparse
from pathlib import Path
import shutil,subprocess
from zipfile import ZipFile,ZIP_DEFLATED
root=Path(__file__).resolve().parent
p=argparse.ArgumentParser(description=__doc__)
for name in ['android-jar','build-tools','keystore','password-file','output']:p.add_argument('--'+name,type=Path,required=True)
p.add_argument('--alias',default='xhyn-r1');args=p.parse_args()
work=root.parents[1]/'.build/android-tests';work.mkdir(parents=True,exist_ok=True)
classes=work/'classes';dex=work/'dex'
for directory in [classes,dex]:
 if directory.exists():shutil.rmtree(directory)
 directory.mkdir()
def run(*parts):subprocess.run([str(x) for x in parts],check=True)
run('java','--module','jdk.compiler/com.sun.tools.javac.Main','-source','8','-target','8','-Xlint:-options','-classpath',args.android_jar,'-d',classes,*sorted((root/'src').rglob('*.java')))
run(args.build_tools/'d8','--min-api','30','--lib',args.android_jar,'--output',dex,*sorted(classes.rglob('*.class')))
raw=work/'tests-unsigned.apk';aligned=work/'tests-aligned.apk'
run(args.build_tools/'aapt','package','-f','-M',root/'AndroidManifest.xml','-I',args.android_jar,'-F',raw)
with ZipFile(raw,'a',compression=ZIP_DEFLATED) as apk:apk.write(dex/'classes.dex','classes.dex')
run(args.build_tools/'zipalign','-f','4',raw,aligned)
args.output.parent.mkdir(parents=True,exist_ok=True)
run(args.build_tools/'apksigner','sign','--ks',args.keystore,'--ks-key-alias',args.alias,'--ks-pass','file:'+str(args.password_file.resolve()),'--out',args.output,aligned)
run(args.build_tools/'apksigner','verify',args.output)
print('Built test-only instrumentation APK:',args.output)
