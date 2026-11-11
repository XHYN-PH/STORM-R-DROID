#!/usr/bin/env python3
# Copyright (c) 2026 XHYN_PH. SPDX-License-Identifier: MIT
"""Check real APK member access, rather than trusting compile-only Java stubs."""
import argparse,re
from pathlib import Path

def check(root):
    classes={}
    for path in root.glob('smali*/**/*.smali'):
        source=path.read_text();header=re.search(r'^\.class (.*?) (L[^;]+;)$',source,re.M)
        if not header:continue
        parent=re.search(r'^\.super (L[^;]+;)$',source,re.M)
        methods={m.group(2):set(m.group(1).split()) for m in re.finditer(r'^\.method (.*?) ([^\s]+\([^\n]*\)[^\s]+)$',source,re.M)}
        fields={m.group(2):set(m.group(1).split()) for m in re.finditer(r'^\.field (.*?) ([^\s:]+:[^\s=]+)',source,re.M)}
        classes[header.group(2)]={'flags':set(header.group(1).split()),'parent':parent.group(1) if parent else None,'interfaces':re.findall(r'^\.implements (L[^;]+;)$',source,re.M),'methods':methods,'fields':fields,'path':path,'source':source}
    errors=[];checked=0
    def package(name):return name.rsplit('/',1)[0]
    def accessible(flags,caller,owner):return 'public' in flags or caller==owner or ('private' not in flags and package(caller)==package(owner))
    def resolve(owner,kind,member):
        seen=set()
        while owner in classes and owner not in seen:
            seen.add(owner);data=classes[owner]
            if member in data[kind]:return owner,data[kind][member]
            owner=data['parent']
        return None,None
    for caller,data in classes.items():
        added=caller.startswith('Lcom/naruto/r2/') or caller=='Lcom/naruto/port/LoggingToggleListener;'
        if not added:continue
        # A compiled stub can name the wrong callback and still produce valid DEX.
        # Check concrete implementations against the real APK interface contracts.
        if 'abstract' not in data['flags'] and 'interface' not in data['flags']:
            pending=list(data['interfaces']);seen=set()
            while pending:
                interface=pending.pop()
                if interface in seen or interface not in classes:continue
                seen.add(interface);contract=classes[interface];pending.extend(contract['interfaces'])
                for member,required in contract['methods'].items():
                    if 'abstract' not in required:continue
                    owner,actual=resolve(caller,'methods',member)
                    if actual is None or 'abstract' in actual or 'static' in actual or 'public' not in actual:
                        errors.append(f'{data["path"]}: Missing concrete interface callback {interface}->{member}')
                    else:checked+=1
        for number,line in enumerate(data['source'].splitlines(),1):
            match=re.search(r'^\s*(invoke-[\w/-]+)\s+\{[^}]*\}, (L[^;]+;)->([^\s]+\([^)]*\)[^\s]+)',line)
            kind='methods'
            if not match:
                match=re.search(r'^\s*([is](?:get|put)[\w/-]*)\s+.*?, (L[^;]+;)->([^\s:]+:[^\s]+)',line)
                kind='fields'
            if not match:continue
            operation,owner,member=match.groups()
            if not (owner.startswith('Lcom/naruto/port/') or owner.startswith('La/')):continue
            prefix=f'{data["path"]}:{number}: '
            if owner not in classes:errors.append(prefix+'Missing APK class '+owner);continue
            if not accessible(classes[owner]['flags'],caller,owner):errors.append(prefix+'Inaccessible APK class '+owner);continue
            declaring,flags=resolve(owner,kind,member)
            if flags is None:
                # Framework methods inherited by Activity/View remain in android.jar.
                if member.startswith(('r2','native')):errors.append(prefix+'Missing APK member '+owner+'->'+member)
                continue
            checked+=1
            if not accessible(flags,caller,declaring):errors.append(prefix+'Inaccessible APK member '+owner+'->'+member)
            actual_static='static' in flags
            expected_static=operation.startswith('invoke-static') if kind=='methods' else operation.startswith('s')
            if actual_static!=expected_static:errors.append(prefix+'Static/instance mismatch '+owner+'->'+member)
    if errors:raise ValueError('\n'.join(errors))
    return checked

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('decoded',type=Path);args=parser.parse_args()
    count=check(args.decoded);print(f'PASS: {count} cross-package APK member references resolve with valid access and invocation kind.')
