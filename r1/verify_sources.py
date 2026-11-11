#!/usr/bin/env python3
# Copyright (c) 2026 XHYN_PH. SPDX-License-Identifier: MIT
"""Verify that R1 keeps upstream engine code, data and notices intact."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import struct
from xml.etree import ElementTree as ET
from zipfile import ZipFile

ROOT=Path(__file__).resolve().parent

def require(value, message):
    if not value: raise AssertionError(message)

def elf_sections(data):
    require(data[:6]==b'\x7fELF\x02\x01','Expected little-endian ELF64')
    offset=struct.unpack_from('<Q',data,40)[0]
    entry_size,count,names_index=struct.unpack_from('<HHH',data,58)
    headers=[struct.unpack_from('<IIQQQQIIQQ',data,offset+i*entry_size) for i in range(count)]
    names=data[headers[names_index][4]:headers[names_index][4]+headers[names_index][5]]
    result={}
    for header in headers:
        end=names.find(b'\0',header[0]);name=names[header[0]:end].decode()
        if header[1]!=8:result[name]=data[header[4]:header[4]+header[5]]
    return result

def method(text, signature):
    found=re.search(r'^'+re.escape(signature)+r'\n.*?^\.end method$',text,re.M|re.S)
    require(found is not None,signature)
    return found.group()

def verify(original, decoded, version_name="R1", version_code=122):
    with ZipFile(original) as apk:
        for name in apk.namelist():
            if not name.startswith(('lib/','assets/')) or name.endswith('/') or name=='assets/licenses.txt':continue
            before=apk.read(name);after=(decoded/name).read_bytes()
            if name=='lib/arm64-v8a/librevolution.so':
                expected=bytearray(before)
                for source,destination in json.loads((ROOT/'native-translations.json').read_text()).items():
                    old,new=source.encode(),destination.encode()
                    require(len(new)<=len(old) and before.count(old)==1,'Native string bounds')
                    index=before.index(old);expected[index:index+len(old)]=new.ljust(len(old),b' ')
                require(after==bytes(expected),'Unexpected engine binary changes')
                a,b=elf_sections(before),elf_sections(after)
                for section in ['.text','.plt','.dynsym','.rela.plt','.rela.dyn']:
                    require(a[section]==b[section],'Changed engine executable/linkage section: '+section)
            else:require(before==after,'Changed upstream asset/library: '+name)
        old_notices=apk.read('assets/licenses.txt').decode().replace('\r\n','\n').split('='*60,1)[1]
        new_notices=(decoded/'assets/licenses.txt').read_text().split('='*60,1)[1]
        old_notices=old_notices.replace('(O linkernsbypass, parte da libadrenotools, tem a mesma licença e o mesmo autor.)',
                                      '(linkernsbypass, part of libadrenotools, has the same license and author.)')
        require(old_notices==new_notices,'Changed license text or copyright notice')
    public=ET.parse(decoded/'res/values/public.xml').getroot()
    declared_styles={element.get('name') for element in public if element.get('type')=='style'}
    defined_styles={element.get('name') for file in (decoded/'res/values').glob('*.xml') for element in ET.parse(file).getroot() if element.tag=='style'}
    require(declared_styles<=defined_styles,'Missing original public style resource')
    smali=decoded/'smali'
    launcher=(smali/'com/naruto/port/LauncherActivity.smali').read_text()
    require('"menu_ptbr"' not in launcher,'PT-BR control still reachable')
    require('\"logging_enabled\"' in launcher,'Missing logging switch')
    require('"Modified and Translated by XHYN_PH"' in launcher,'Missing R1 author footer')
    combined='\n'.join(p.read_text() for p in smali.rglob('*.smali'))
    require('"https://youtube.com/@xhyn_ph"' in combined,'Incorrect YouTube URL')
    require('https://api.github.com/repos/Masterllgod/' not in combined,'Updater still points at upstream')
    require('\"XHYN-PH/\"' in combined and '\"STORM-R-DROID\"' in combined and '\"Masterllgod/\"' not in combined,'Updater not pointed at fork')
    require('ViewPropertyAnimator;->scaleX' in method(launcher,'.method public static k(Landroid/view/View;)V'),'Card bounce removed')
    require('ViewPropertyAnimator;->scaleX' in (smali/'a/e.smali').read_text(),'Press animation removed')
    effect=method((smali/'a/C.smali').read_text(),'.method public final e(IFF)V')
    require(effect=='.method public final e(IFF)V\n    .locals 0\n\n    return-void\n.end method','Particle effects remain')
    for p in smali.rglob('*.smali'):
        for match in re.finditer(r'^\.method (.*?)\n(.*?)^\.end method',p.read_text(),re.M|re.S):
            body=re.sub(r'"(?:[^"\\]|\\.)*"','""',match.group(2))
            labels=set(re.findall(r'^\s*(:\w+)\s*$',body,re.M));refs=set(re.findall(r'(?<![\w]):[a-z_]\w*',body))
            require(refs<=labels,'Missing smali branch label in '+str(p))
            locals_match=re.search(r'^\s*\.locals (\d+)',body,re.M)
            if not locals_match:continue
            local_count=int(locals_match.group(1))
            for call in re.finditer(r'^\s*invoke-(?![^\s]*range)([^\s]+)\s+\{([^}]*)\}',body,re.M):
                regs=re.findall(r'([pv])(\d+)',call.group(2));require(len(regs)<=5,'Too many invocation registers')
                require(all(int(num)+(local_count if kind=='p' else 0)<16 for kind,num in regs),'Invocation register overflow')
    manifest=ET.parse(decoded/'AndroidManifest.xml').getroot();android='{http://schemas.android.com/apk/res/android}'
    require(manifest.get('package')=='com.naruto.storm4','Unexpected package change')
    require(manifest.find('application').get(android+'debuggable') not in ['true','1'],'Debuggable release')
    helper=decoded/'lib/arm64-v8a/libxhynlog.so';data=helper.read_bytes()
    require(struct.unpack_from('<H',data,18)[0]==183,'Native helper is not ARM64')
    for symbol in ['Java_com_naruto_port_LoggingControl_nativeSetEnabled','Java_com_naruto_port_LoggingControl_nativeInstall']:
        require(symbol.encode()+b'\0' in data,'Missing JNI method: '+symbol)
    yaml=(decoded/'apktool.yml').read_text()
    require(f'  versionCode: {version_code}\n' in yaml and f'  versionName: {version_name}\n' in yaml,'Incorrect release version')
    print('PASS: engine code/linkage, assets, license bodies, English language, footer, fork updater, animations, smali labels/registers, manifest and ARM64 helper checks.')

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--original',type=Path,required=True)
    parser.add_argument('--decoded',type=Path,required=True)
    parser.add_argument('--version-name',default='R1');parser.add_argument('--version-code',type=int,default=122)
    args=parser.parse_args();verify(args.original,args.decoded,args.version_name,args.version_code)
