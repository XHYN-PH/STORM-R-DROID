#!/usr/bin/env python3
# Copyright (c) 2026 XHYN_PH. SPDX-License-Identifier: MIT
"""A reversible diagnostic build with the original engine-facing startup path."""
from pathlib import Path
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
PRESENTATION = '    invoke-static/range {p0 .. p0}, Lcom/naruto/r2/GameHooks;->presentationReady(Landroid/app/Activity;)V'
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
    # Restore presentation settings without starting added native polling/refresh hooks.
    create = create.replace('    return-void', PRESENTATION + '\n' + METADATA + '\n    return-void')
    # Avoid recording a failed save-lock attempt as a running game session.
    create = create.replace(PRESENTATION + '\n' + METADATA + '\n    return-void\n    :startup_test_lock_ready', '    return-void\n    :startup_test_lock_ready')
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
    # Keep the existing wrappers that apply Crop and touch visibility from modern menus.
    (decoded / GAME).write_text(game)

    # Keep the original main-thread statistics/preparation scheduling and SDL lookup.
    for name in ['smali/a/H.smali', 'smali/org/libsdl/app/SDLJoystickHandler_API16.smali']:
        (decoded / name).write_bytes((reference / name).read_bytes())
    menu = decoded / 'smali/com/naruto/r2/GameMenu$1.smali'
    text = menu.read_text()
    resume = 'Lcom/naruto/r2/GameLoop;->resume(Lcom/naruto/port/NarutoActivity;)V'
    if text.count(resume) != 1:
        raise ValueError('Game-menu statistics callback changed')
    menu.write_text(text.replace(resume, 'Lcom/naruto/r2/GameHooks;->resumeOriginalStats(Lcom/naruto/port/NarutoActivity;)V'))

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
    # Retain the shared statistics UI, configuration writer and Material theme from
    # customize.py. Test 1 unnecessarily reverted these visible, working features.
    (decoded / 'assets/startup-test.txt').write_text(
        'STORM R original-startup compatibility test 2\n'
        'Original game JNI polling, refresh selection and touch drawing.\n'
        'STORM 2/3 menu styling, adjustable statistics and display settings retained.\n'
        'Native file-log interception is bypassed.\n'
        'Native logging follows the original app in this diagnostic build.\n'
        'Not a verified gameplay fix. No save or preference data is deleted.\n')
    verify(reference, decoded)


def verify(reference, decoded):
    original = methods((reference / GAME).read_text())
    game = methods((decoded / GAME).read_text())
    body = game[CREATE].replace('\n' + LOCK, '').replace(PRESENTATION + '\n', '').replace(METADATA + '\n', '')
    if body != java_logging_only(original[CREATE]):
        raise AssertionError('Startup test changed original game initialization')
    if game[REFRESH] != original[REFRESH]:
        raise AssertionError('Startup test changed original refresh selection')
    if game[DESTROY].replace(STOP + '\n', '') != original[DESTROY]:
        raise AssertionError('Startup test changed original SDL shutdown')
    stretch_hook = '    invoke-static/range {p0 .. p1}, Lcom/naruto/r2/GameHooks;->stockScreen(Landroid/app/Activity;Z)V\n\n'
    if game['.method private applyStretch(Z)V'].replace(stretch_hook, '') != original['.method private applyStretch(Z)V']:
        raise AssertionError('Unexpected native stretch change')
    controller = '.method private r2OriginalControllerMode()V'
    if game[controller].replace(controller, '.method private applyControllerMode()V') != original['.method private applyControllerMode()V']:
        raise AssertionError('Original controller visibility behavior changed')
    signature = '.method public final z()V'
    launcher = Path('smali/com/naruto/port/LauncherActivity.smali')
    config = '    invoke-static/range {p0 .. p0}, Lcom/naruto/r2/GameHooks;->config(Landroid/app/Activity;)V\n\n'
    if methods((decoded / launcher).read_text())[signature].replace(config, '') != methods((reference / launcher).read_text())[signature]:
        raise AssertionError('Unexpected change to the original configuration writer')
    for name in ['smali/a/H.smali', 'smali/org/libsdl/app/SDLJoystickHandler_API16.smali']:
        if (decoded / name).read_bytes() != (reference / name).read_bytes():
            raise AssertionError('Startup test changed original scheduler/input: ' + name)
    menu = (decoded / 'smali/com/naruto/r2/GameMenu$1.smali').read_text()
    if 'GameLoop;->resume' in menu or 'GameHooks;->resumeOriginalStats' not in menu:
        raise AssertionError('FPS toggle can reactivate the added JNI worker')
    name = 'smali/a/l0.smali'
    before = methods((reference / name).read_text())
    after = methods((decoded / name).read_text())
    for signature, source in before.items():
        if after.get(signature) != source:
            raise AssertionError('Original draw/input method drifted: ' + name + ' ' + signature)
    android = '{http://schemas.android.com/apk/res/android}'
    app = ET.parse(decoded / 'AndroidManifest.xml').getroot().find('application')
    game_activity = next(a for a in app.findall('activity') if a.get(android+'name') == 'com.naruto.port.NarutoActivity')
    if game_activity.get(android+'theme', app.get(android+'theme')) != '@style/AppTheme':
        raise AssertionError('Game dialogs do not share the launcher Material theme')
    stats = methods((decoded/'smali/a/g0.smali').read_text())
    for signature, helper in [('.method public setValues([F)V', 'update'), ('.method public final onDraw(Landroid/graphics/Canvas;)V', 'draw')]:
        if 'Lcom/naruto/r2/StatsOverlay;->'+helper not in stats[signature]:
            raise AssertionError('Shared statistics UI was reverted')
    allowed = {'.method public constructor <init>(Lcom/naruto/port/NarutoActivity;Landroid/content/SharedPreferences;)V',
               '.method public final a()V', '.method public setValues([F)V', '.method public final onDraw(Landroid/graphics/Canvas;)V'}
    for signature, source in methods((reference/'smali/a/g0.smali').read_text()).items():
        if signature == '.method private setCollapsed(Z)V':
            current = stats['.method private r2OriginalSetCollapsed(Z)V'].replace('r2OriginalSetCollapsed(', 'setCollapsed(')
        elif signature in allowed:
            continue
        else:
            current = stats.get(signature)
        if current != source:
            raise AssertionError('Original statistics gesture changed: ' + signature)
    print('PASS: original startup/JNI scheduling and touch input retained; shared theme, statistics and settings remain active.')
