#!/usr/bin/env python3
# Copyright (c) 2026 XHYN_PH. SPDX-License-Identifier: MIT
"""Run the actual game activity in :game through the native loader boundary."""
import base64,os,re,subprocess,time,xml.etree.ElementTree as ET
from pathlib import Path

result=Path(os.environ['RUNNER_TEMP'])/'r1-results'
tools=Path(os.environ['RUNNER_TEMP'])/'r1-tools'
adb=str(Path(os.environ['ANDROID_HOME'])/'platform-tools/adb')
package='com.naruto.storm4'
def command(*args,check=True,timeout=35):
    return subprocess.run([adb,*map(str,args)],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,check=check,timeout=timeout).stdout

def notice(title,text):
    for start in range(0,len(text),3000):
        part=text[start:start+3000].replace('%','%25').replace('\r','%0D').replace('\n','%0A')
        print(f'::notice title={title}::{part}',flush=True)

def read_log(version):
    log=command('logcat','-d','-b','all');(result/f'{version}-startup.log').write_text(log)
    return log

def assert_alive():
    pid=command('shell','pidof',package+':game',check=False).strip()
    if not pid:raise AssertionError('The actual game process exited during startup/menu use.')
    return pid

def dump_menu(name):
    remote='/sdcard/r2-real-menu.xml'
    command('shell','uiautomator','dump',remote,timeout=45)
    path=result/name;command('pull',remote,path)
    return ET.parse(path).getroot()

command('root');command('wait-for-device')
for version,file in [('R2','R2-ci-only.apk')]:
    log=''
    try:
        print(command('install','--no-incremental','-r',result/file,timeout=90),flush=True)
        command('shell','am','force-stop',package)
        command('shell','appops','set',package,'MANAGE_EXTERNAL_STORAGE','allow')
        command('shell','mkdir','-p',f'/data/data/{package}/shared_prefs',f'/sdcard/Android/data/{package}/files/r2-empty-game')
        prefs=tools/'naruto.xml';prefs.write_text("<?xml version='1.0' encoding='utf-8' standalone='yes' ?><map><boolean name='logging_enabled' value='true'/><boolean name='menu_stats' value='true'/><int name='menu_screen_hz' value='0'/><int name='framegen_playing' value='1'/><float name='shadow_emblem_x_v2' value='24.0'/><float name='shadow_emblem_y_v2' value='24.0'/></map>")
        command('push',prefs,f'/data/data/{package}/shared_prefs/naruto.xml')
        uid=command('shell','stat','-c','%u',f'/data/data/{package}').strip()
        if not uid.isdigit():raise AssertionError('Cannot read isolated CI app UID')
        command('shell','chown','-R',f'{uid}:{uid}',f'/data/data/{package}/shared_prefs')
        command('shell','restorecon','-RF',f'/data/data/{package}/shared_prefs',check=False)
        command('logcat','-c')
        start=command('shell','am','start','-W','-n',package+'/com.naruto.port.NarutoActivity','--es','module','revolution','--es','gameDirectory',f'/sdcard/Android/data/{package}/files/r2-empty-game')
        (result/f'{version}-start.txt').write_text(start);notice(version+' actual activity',start)
        time.sleep(5)
        pid=assert_alive();log=read_log(version)
        assert 'Running main function SDL_main' in log,'The native SDL entry point did not start.'
        if os.environ.get('STARTUP_TEST')=='true':
            assert pid.isdigit(),'Unexpected game process identity'
            maps=command('shell','cat',f'/proc/{pid}/maps')
            assert 'librevolution.so' in maps,'The actual Revolution engine was not mapped.'
            assert 'libxhynlog.so' not in maps,'Diagnostic game process loaded the native log-interception helper.'
            tasks=command('shell','ls',f'/proc/{pid}/task').split()
            names=[]
            for task in tasks:
                if task.isdigit():names.append(command('shell','cat',f'/proc/{pid}/task/{task}/comm',check=False).strip())
            assert not any(name.startswith('STORM native st') for name in names),'Diagnostic game process started the added native-statistics worker.'
            notice('Original startup profile','PASS: actual Revolution engine loaded; native log-interception library and added JNI worker absent from :game.')
        if version=='R2':
            hierarchy=dump_menu('R2-native-loader.xml')
            messages=[node.get('text','') for node in hierarchy.iter('node')]
            assert any('missing default.xex' in text for text in messages),'The native loader did not reach the expected empty-fixture boundary.'
            # Capture a real native-engine dialog in :game, not a launcher-hosted mock.
            remote='/sdcard/r2-native-dialog.png'
            command('shell','screencap','-p',remote)
            picture=result/'native-dialog.png';command('pull',remote,picture)
            if picture.stat().st_size<=180000:
                encoded=base64.b64encode(picture.read_bytes()).decode()
                parts=[encoded[i:i+4000] for i in range(0,len(encoded),4000)]
                for index,part in enumerate(parts,1):notice(f'R2 preview native-dialog {index:02d}/{len(parts):02d}',part)
            session=command('shell','cat',f'/data/data/{package}/files/r2-last-session.txt')
            assert 'App: STORM R R1' in session and 'Last foreground Android display rate:' in session,'Real game activity did not capture the new session metadata.'
            (result/'R2-session-details.txt').write_text(session)
            notice('R2 session evidence','PASS: real game-activity session version and display snapshot recorded.')
            assert_alive();log=read_log(version)
        assert not re.search(r'AndroidRuntime: Process: com\.naruto\.storm4:game',log),'AndroidRuntime reported an uncaught game exception.'
        notice(version+' startup result','PASS: actual :game process alive, SDL native main reached'+(', native missing-game-file dialog reached without the Java startup crash' if version=='R2' else ''))
    except Exception as error:
        log=read_log(version)
        relevant='\n'.join(line for line in log.splitlines() if 'AndroidRuntime' in line or 'F libc' in line or 'F DEBUG' in line)
        notice(version+' startup failure',str(error)+'\n'+(relevant[-12000:] or log[-3000:]))
        raise
    finally:
        command('shell','am','force-stop',package,check=False)
