#!/usr/bin/env python3
# Copyright (c) 2026 XHYN_PH. SPDX-License-Identifier: MIT
"""A reversible diagnostic build with the original engine-facing startup path."""
from pathlib import Path
import copy
import re
from xml.etree import ElementTree as ET

GAME = Path('smali/com/naruto/port/NarutoActivity.smali')
CREATE = '.method public onCreate(Landroid/os/Bundle;)V'
DESTROY = '.method public onDestroy()V'
REFRESH = '.method private requestRefreshRate(II)V'
LOCK = '''    invoke-static/range {p0 .. p0}, Lcom/naruto/r2/GameHooks;->gameStart(Landroid/app/Activity;)V
    invoke-virtual/range {p0 .. p0}, Landroid/app/Activity;->isFinishing()Z
    move-result v0
    if-eqz v0, :startup_test_lock_ready
    return-void
    :startup_test_lock_ready'''
METADATA = '    invoke-static/range {p0 .. p0}, Lcom/naruto/r2/SessionDetails;->start(Lcom/naruto/port/NarutoActivity;)V'
STOP = '    invoke-static/range {p0 .. p0}, Lcom/naruto/r2/GameHooks;->gameStop(Landroid/app/Activity;)V'


def methods(text):
    return {m.group(1): m.group() for m in re.finditer(r'^(\.method [^\n]+)\n.*?^\.end method$', text, re.M | re.S)}


def replace(text, signature, body):
    pattern = re.compile(r'^' + re.escape(signature) + r'\n.*?^\.end method$', re.M | re.S)
    text, count = pattern.subn(lambda _: body, text)
    if count != 1:
        raise ValueError('Unexpected startup test method: ' + signature)
    return text


def java_logging_only(create):
    old = '    invoke-static/range {p0 .. p0}, Lcom/naruto/port/LoggingControl;->initialize(Landroid/content/Context;)Z'
    new = '    invoke-static/range {p0 .. p0}, Lcom/naruto/port/LoggingControl;->initializeJavaOnly(Landroid/content/Context;)V'
    if create.count(old) != 1:
        raise ValueError('Original logging initialization changed')
    create = create.replace(old, new)
    native = '    invoke-static/range {p0 .. p0}, Lcom/naruto/port/LoggingControl;->gameReady(Landroid/content/Context;)V\n\n'
    if create.count(native) != 1:
        raise ValueError('Original logging installation changed')
    return create.replace(native, '')


def apply(reference, decoded):
    original = methods((reference / GAME).read_text())
    game = (decoded / GAME).read_text()
    create = java_logging_only(original[CREATE])
    super_call = '    invoke-super {p0, p1}, Lorg/libsdl/app/SDLActivity;->onCreate(Landroid/os/Bundle;)V'
    if create.count(super_call) != 1:
        raise ValueError('Original SDL creation changed')
    create = create.replace(super_call, super_call + '\n' + LOCK)
    # The only post-create extension records Java-side metadata; it never samples JNI.
    create = create.replace('    return-void', METADATA + '\n    return-void')
    # Avoid recording a failed save-lock attempt as a running game session.
    create = create.replace(METADATA + '\n    return-void\n    :startup_test_lock_ready', '    return-void\n    :startup_test_lock_ready')
    game = replace(game, CREATE, create)
    game = replace(game, DESTROY, original[DESTROY].replace('    return-void', STOP + '\n    return-void'))
    game = replace(game, REFRESH, original[REFRESH])
    for name, helper in [('onResume', 'resume'), ('onPause', 'pause')]:
        signature = '.method public ' + name + '()V'
        body = signature + '\n    .locals 0\n'
        body += '    invoke-super {p0}, Lorg/libsdl/app/SDLActivity;->' + name + '()V\n'
        body += '    invoke-static {p0}, Lcom/naruto/r2/SessionDetails;->' + helper + '(Lcom/naruto/port/NarutoActivity;)V\n'
        body += '    return-void\n.end method'
        game = replace(game, signature, body)
    game = replace(game, '.method public onWindowFocusChanged(Z)V', '''.method public onWindowFocusChanged(Z)V
    .locals 0
    invoke-super {p0, p1}, Lorg/libsdl/app/SDLActivity;->onWindowFocusChanged(Z)V
    invoke-static {p0, p1}, Lcom/naruto/r2/SessionDetails;->focus(Lcom/naruto/port/NarutoActivity;Z)V
    return-void
.end method''')
    game = replace(game, '.method private applyStretch(Z)V', original['.method private applyStretch(Z)V'])
    game = replace(game, '.method private applyControllerMode()V', original['.method private applyControllerMode()V'])
    (decoded / GAME).write_text(game)

    # Keep the original main-thread statistics/preparation scheduling and SDL lookup.
    for name in ['smali/a/H.smali', 'smali/org/libsdl/app/SDLJoystickHandler_API16.smali']:
        (decoded / name).write_bytes((reference / name).read_bytes())

    # Retain all original touch rendering and editing, with unused typed bridges for menus.
    touch = (reference / 'smali/a/l0.smali').read_text()
    touch += '\n.field public r2RenderState:Lcom/naruto/r2/TouchRender$State;\n'
    touch += '''
.method public final r2DrawButton(Landroid/graphics/Canvas;La/i0;Z)V
    .locals 0
    invoke-virtual {p0, p1, p2, p3}, La/l0;->d(Landroid/graphics/Canvas;La/i0;Z)V
    return-void
.end method
.method public final r2DrawStick(Landroid/graphics/Canvas;)V
    .locals 0
    invoke-virtual {p0, p1}, La/l0;->g(Landroid/graphics/Canvas;)V
    return-void
.end method
'''
    (decoded / 'smali/a/l0.smali').write_text(touch)
    stats = (reference / 'smali/a/g0.smali').read_text()
    ctor = '.method public constructor <init>(Lcom/naruto/port/NarutoActivity;Landroid/content/SharedPreferences;)V'
    if stats.count(ctor) != 1:
        raise ValueError('Original statistics constructor changed')
    stats = stats.replace(ctor, '.method public constructor <init>(Landroid/app/Activity;Landroid/content/SharedPreferences;)V')
    stats += '\n.field public r2StatsState:Lcom/naruto/r2/StatsOverlay$State;\n'
    stats += ctor + '''
    .locals 0
    invoke-direct {p0, p1, p2}, La/g0;-><init>(Landroid/app/Activity;Landroid/content/SharedPreferences;)V
    invoke-static {p0}, Lcom/naruto/r2/StatsOverlay;->resize(La/g0;)V
    return-void
.end method
'''
    (decoded / 'smali/a/g0.smali').write_text(stats)

    # Leave the original configuration writer's engine options authoritative.
    launcher = decoded / 'smali/com/naruto/port/LauncherActivity.smali'
    text = launcher.read_text()
    signature = '.method public final z()V'
    stock_writer = methods((reference / 'smali/com/naruto/port/LauncherActivity.smali').read_text())[signature]
    launcher.write_text(replace(text, signature, stock_writer))

    android = '{http://schemas.android.com/apk/res/android}'
    ET.register_namespace('android', android[1:-1])
    manifest = ET.parse(decoded / 'AndroidManifest.xml')
    for activity in manifest.getroot().find('application').findall('activity'):
        if activity.get(android + 'name') == 'com.naruto.port.NarutoActivity':
            activity.set(android + 'theme', '@style/StockGameTheme')
    manifest.write(decoded / 'AndroidManifest.xml', encoding='utf-8', xml_declaration=True)
    styles = ET.parse(decoded / 'res/values/styles.xml')
    stock = ET.parse(reference / 'res/values/styles.xml').getroot().find("style[@name='AppTheme']")
    stock = copy.deepcopy(stock)
    stock.set('name', 'StockGameTheme')
    styles.getroot().append(stock)
    styles.write(decoded / 'res/values/styles.xml', encoding='utf-8', xml_declaration=True)
    (decoded / 'assets/startup-test.txt').write_text(
        'STORM R original-startup compatibility test 1\n'
        'Original game JNI polling, refresh selection, rendering and window theme.\n'
        'Native file-log interception and additional engine configuration overrides are bypassed.\n'
        'Native logging follows the original app in this diagnostic build.\n'
        'Not a verified gameplay fix. No save or preference data is deleted.\n')
    verify(reference, decoded)


def verify(reference, decoded):
    original = methods((reference / GAME).read_text())
    game = methods((decoded / GAME).read_text())
    body = game[CREATE].replace('\n' + LOCK, '').replace(METADATA + '\n', '')
    if body != java_logging_only(original[CREATE]):
        raise AssertionError('Startup test changed original game initialization')
    if game[REFRESH] != original[REFRESH]:
        raise AssertionError('Startup test changed original refresh selection')
    if game[DESTROY].replace(STOP + '\n', '') != original[DESTROY]:
        raise AssertionError('Startup test changed original SDL shutdown')
    signature = '.method public final z()V'
    launcher = Path('smali/com/naruto/port/LauncherActivity.smali')
    if methods((decoded / launcher).read_text())[signature] != methods((reference / launcher).read_text())[signature]:
        raise AssertionError('Startup test changed original engine configuration writer')
    for name in ['smali/a/H.smali', 'smali/org/libsdl/app/SDLJoystickHandler_API16.smali']:
        if (decoded / name).read_bytes() != (reference / name).read_bytes():
            raise AssertionError('Startup test changed original scheduler/input: ' + name)
    for name in ['smali/a/l0.smali', 'smali/a/g0.smali']:
        before = methods((reference / name).read_text())
        after = methods((decoded / name).read_text())
        for signature, source in before.items():
            if name.endswith('g0.smali') and 'constructor <init>' in signature:
                generalized = signature.replace('Lcom/naruto/port/NarutoActivity;', 'Landroid/app/Activity;')
                if after.get(generalized) != source.replace(signature, generalized):
                    raise AssertionError('Statistics constructor drifted')
            elif after.get(signature) != source:
                raise AssertionError('Original draw/input method drifted: ' + name + ' ' + signature)
    print('PASS: compatibility profile preserves original engine startup, JNI scheduler, refresh, SDL input and drawing methods.')
