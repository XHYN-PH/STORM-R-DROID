#!/usr/bin/env python3
# Copyright (c) 2026 XHYN_PH. SPDX-License-Identifier: MIT
"""Apply R2 to the verified English R1 decoded tree; no APK assembly/signing."""
import re,shutil,argparse
import overlay_patches
from pathlib import Path
ROOT=Path(__file__).resolve().parent

def change(path,signature,fn):
 source=path.read_text();pattern=re.compile(r'^'+re.escape(signature)+r'\n.*?^\.end method$',re.M|re.S)
 matches=list(pattern.finditer(source))
 if len(matches)!=1:raise ValueError('Unexpected method '+str(path)+' '+signature)
 new=fn(matches[0].group());source=source[:matches[0].start()]+new+source[matches[0].end():];path.write_text(source)
def start(body,call):
 pattern=r'(\n    \.locals \d+\n)'
 updated,n=re.subn(pattern,r'\1\n'+call+'\n',body,count=1)
 if n!=1:raise ValueError('Method locals not found')
 return updated
def returns(body,call):return body.replace('    return-void',call+'\n\n    return-void')
def call(method,signature,last=0):return f'    invoke-static/range {{p0 .. p{last}}}, Lcom/naruto/r2/{method};->{signature}'
def customize(root):
 smali=root/'smali';runtime=ROOT/'.build/runtime/decoded/smali/com/naruto/r2'
 if not runtime.is_dir():raise ValueError('Compile the R2 runtime first')
 shutil.copytree(runtime,smali/'com/naruto/r2',dirs_exist_ok=False)
 launcher=smali/'com/naruto/port/LauncherActivity.smali';game=smali/'com/naruto/port/NarutoActivity.smali';touch=smali/'a/l0.smali';controller=smali/'com/naruto/port/ControllerMapActivity.smali'
 change(launcher,'.method public final onCreate(Landroid/os/Bundle;)V',lambda b:returns(start(b,call('GameHooks','beforeLauncher(Landroid/app/Activity;)V')),call('LauncherUi','install(Lcom/naruto/port/LauncherActivity;)V')))
 change(launcher,'.method public final z()V',lambda b:returns(b,call('GameHooks','config(Landroid/app/Activity;)V')))
 # Extend the stock driver flow instead of introducing another picker or loader.
 change(smali/'a/i.smali','.method public static q()Z',lambda b:'.method public static q()Z\n    .locals 1\n    invoke-static {}, Lcom/naruto/r2/DriverSupport;->available()Z\n    move-result v0\n    return v0\n.end method')
 change(smali/'a/i.smali','.method public static k(Landroid/content/Context;Ljava/io/InputStream;)La/h;',lambda b:'.method public static k(Landroid/content/Context;Ljava/io/InputStream;)La/h;\n    .locals 1\n    invoke-static {p0, p1}, Lcom/naruto/r2/DriverSupport;->importDriver(Landroid/content/Context;Ljava/io/InputStream;)La/h;\n    move-result-object v0\n    return-object v0\n.end method')
 change(launcher,'.method public final q()La/h;',lambda b:'.method public final q()La/h;\n    .locals 1\n    invoke-static {p0}, Lcom/naruto/r2/DriverSupport;->selected(Lcom/naruto/port/LauncherActivity;)La/h;\n    move-result-object v0\n    return-object v0\n.end method')
 source=launcher.read_text().replace('.method public final p(Ljava/lang/String;)V','.method public final r2OriginalSelectDriver(Ljava/lang/String;)V')
 source+='\n.method public final p(Ljava/lang/String;)V\n    .locals 0\n    invoke-static {p0, p1}, Lcom/naruto/r2/DriverSupport;->select(Lcom/naruto/port/LauncherActivity;Ljava/lang/String;)V\n    return-void\n.end method\n'
 launcher.write_text(source)
 # Preserve all eight stock attachments and the original sharing implementation.
 source=launcher.read_text().replace('.method public final r(Z)V','.method public final r2OriginalSendLog(Z)V')
 source+='\n.method public final r(Z)V\n    .locals 0\n    invoke-static {p0, p1}, Lcom/naruto/r2/SessionDetails;->share(Lcom/naruto/port/LauncherActivity;Z)V\n    return-void\n.end method\n'
 needle='    const/16 v6, 0x8\n\n    if-ge v5, v6, :cond_1'
 if source.count(needle)!=1:raise ValueError('Stock share attachment loop changed')
 launcher.write_text(source.replace(needle,'    array-length v6, v3\n\n    if-ge v5, v6, :cond_1'))
 provider=smali/'com/naruto/port/LogProvider.smali';source=provider.read_text()
 needle='    filled-new-array/range {v0 .. v7}, [Ljava/lang/String;'
 if source.count(needle)!=1:raise ValueError('Stock log whitelist changed')
 source=source.replace('    .locals 8','    .locals 9',1).replace(needle,'    const-string v8, "session-details.txt"\n\n    filled-new-array/range {v0 .. v8}, [Ljava/lang/String;')
 needle='    const/16 v4, 0x8\n\n    if-ge v3, v4, :cond_2'
 if source.count(needle)!=1:raise ValueError('Stock provider attachment loop changed')
 provider.write_text(source.replace(needle,'    array-length v4, v1\n\n    if-ge v3, v4, :cond_2'))
 # New buttons reuse this launcher's own button builder and colors.
 with launcher.open('a') as out:out.write('\n.method public r2Button(Ljava/lang/String;II)Landroid/widget/TextView;\n    .locals 1\n    invoke-virtual {p0, p1}, Lcom/naruto/port/LauncherActivity;->j(Ljava/lang/String;)Landroid/widget/TextView;\n    move-result-object v0\n    return-object v0\n.end method\n')
 change(launcher,'.method public final onActivityResult(IILandroid/content/Intent;)V',lambda b:start(b,'''    invoke-static/range {p0 .. p3}, Lcom/naruto/r2/LauncherUi;->activityResult(Lcom/naruto/port/LauncherActivity;IILandroid/content/Intent;)Z

    move-result v0

    if-eqz v0, :r2_original_result

    return-void

    :r2_original_result'''))
 def game_create(body):
  needle='    invoke-super {p0, p1}, Lorg/libsdl/app/SDLActivity;->onCreate(Landroid/os/Bundle;)V'
  if body.count(needle)!=1:raise ValueError('Game framework creation changed')
  hook=needle+'\n'+call('GameHooks','gameStart(Landroid/app/Activity;)V')+'\n    invoke-virtual/range {p0 .. p0}, Landroid/app/Activity;->isFinishing()Z\n    move-result v0\n    if-eqz v0, :r2_game_lock_ok\n    return-void\n    :r2_game_lock_ok'
  return returns(body.replace(needle,hook),call('GameHooks','gameReady(Landroid/app/Activity;)V'))
 change(game,'.method public onCreate(Landroid/os/Bundle;)V',game_create)
 change(game,'.method public onDestroy()V',lambda b:returns(b,call('GameHooks','gameStop(Landroid/app/Activity;)V')))
 # Keep the stock in-game Fit/Stretch menu authoritative when used.
 change(game,'.method private applyStretch(Z)V',lambda b:start(b,call('GameHooks','stockScreen(Landroid/app/Activity;Z)V',1)))
 # Original physical axes, bindings and mapping UI remain untouched.
 overlay_patches.apply(smali)
 # Stock SDL already caches motion ranges; avoid an Iterator allocation on each lookup.
 joystick=smali/'org/libsdl/app/SDLJoystickHandler_API16.smali'
 change(joystick,'.method public getJoystick(I)Lorg/libsdl/app/SDLJoystickHandler_API16$SDLJoystick;',lambda b:'.method public getJoystick(I)Lorg/libsdl/app/SDLJoystickHandler_API16$SDLJoystick;\n    .locals 4\n    iget-object v0, p0, Lorg/libsdl/app/SDLJoystickHandler_API16;->mJoysticks:Ljava/util/ArrayList;\n    const/4 v1, 0x0\n    :r2_lookup\n    invoke-virtual {v0}, Ljava/util/ArrayList;->size()I\n    move-result v2\n    if-ge v1, v2, :r2_missing\n    invoke-virtual {v0, v1}, Ljava/util/ArrayList;->get(I)Ljava/lang/Object;\n    move-result-object v2\n    check-cast v2, Lorg/libsdl/app/SDLJoystickHandler_API16$SDLJoystick;\n    iget v3, v2, Lorg/libsdl/app/SDLJoystickHandler_API16$SDLJoystick;->device_id:I\n    if-eq v3, p1, :r2_found\n    add-int/lit8 v1, v1, 0x1\n    goto :r2_lookup\n    :r2_found\n    return-object v2\n    :r2_missing\n    const/4 v2, 0x0\n    return-object v2\n.end method')
 # Bridge private methods without changing their dispatch kind at existing call sites.
 source=game.read_text()
 fields=['controls','preferences','prepareBar','prepareCount','preparePanel','prepareSeen','prepareStarted','prepareUpdater','shadowPanel','statsHandler','statsUpdater','statsView','surfaceFrameRate']
 for field in fields:
  source,n=re.subn(r'^\.field private (final )?('+field+r':)',r'.field public \1\2',source,flags=re.M)
  if n!=1:raise ValueError('Missing game field '+field)
 # Keep native methods private and call them through typed public bridges in their own class.
 native_bridges=[('r2ShaderPrepare','nativeShaderPrepare','','[I'),('r2StatsValues','nativeStatsValues','','[F'),('r2SetTouch','nativeSetTouch','IIIIIII','V'),('r2SetShowStats','nativeSetShowStats','Z','V'),('r2ShadowSet','nativeShadowSet','Ljava/lang/String;Ljava/lang/String;','Z')]
 for bridge,target,types,result in native_bridges:
  count=types.count(';') if types.startswith('L') else len(types)
  registers='{}' if count==0 else '{p0 .. p'+str(count-1)+'}'
  instruction='invoke-static' if count==0 else 'invoke-static/range'
  ending='    return-void' if result=='V' else ('    move-result-object v0\n    return-object v0' if result.startswith('[') else '    move-result v0\n    return v0')
  source+=f"\n.method public static {bridge}({types}){result}\n    .locals {0 if result=='V' else 1}\n    {instruction} {registers}, Lcom/naruto/port/NarutoActivity;->{target}({types}){result}\n{ending}\n.end method\n"
 for method,types in [('applyStretch','Z'),('applyControllerMode','')]:
  source+=f"\n.method public r2{method[0].upper()+method[1:]}({types})V\n    .locals 0\n    invoke-direct/range {{p0 .. p{len(types)}}}, Lcom/naruto/port/NarutoActivity;->{method}({types})V\n    return-void\n.end method\n"
 source+='\n.method public r2GameThread()Ljava/lang/Thread;\n    .locals 1\n    sget-object v0, Lorg/libsdl/app/SDLActivity;->mSDLThread:Ljava/lang/Thread;\n    return-object v0\n.end method\n.method public onPause()V\n    .locals 0\n    invoke-static {p0}, Lcom/naruto/r2/GameLoop;->pause(Lcom/naruto/port/NarutoActivity;)V\n    invoke-super {p0}, Lorg/libsdl/app/SDLActivity;->onPause()V\n    return-void\n.end method\n.method public onResume()V\n    .locals 0\n    invoke-super {p0}, Lorg/libsdl/app/SDLActivity;->onResume()V\n    invoke-static {p0}, Lcom/naruto/r2/GameLoop;->resume(Lcom/naruto/port/NarutoActivity;)V\n    return-void\n.end method\n'
 game.write_text(source)
 with game.open('a') as out:out.write('\n.method public onWindowFocusChanged(Z)V\n    .locals 0\n    invoke-super {p0, p1}, Lorg/libsdl/app/SDLActivity;->onWindowFocusChanged(Z)V\n    invoke-static {p0, p1}, Lcom/naruto/r2/SessionDetails;->focus(Lcom/naruto/port/NarutoActivity;Z)V\n    invoke-static {p0, p1}, Lcom/naruto/r2/Performance;->focus(Lcom/naruto/port/NarutoActivity;Z)V\n    return-void\n.end method\n')
 change(game,'.method private requestRefreshRate(II)V',lambda b:'.method private requestRefreshRate(II)V\n    .locals 0\n'+call('Performance','refresh(Lcom/naruto/port/NarutoActivity;II)V',2)+'\n    return-void\n.end method')
 change(game,'.method public onBackPressed()V',lambda b:'.method public onBackPressed()V\n    .locals 0\n'+call('GameMenu','back(Lcom/naruto/port/NarutoActivity;)V')+'\n    return-void\n.end method')
 # The original method reuses p0 for the emblem. Keep the Activity in a wrapper.
 source=game.read_text().replace('.method private applyControllerMode()V','.method private r2OriginalControllerMode()V')
 source+='\n.method private applyControllerMode()V\n    .locals 0\n    invoke-direct {p0}, Lcom/naruto/port/NarutoActivity;->r2OriginalControllerMode()V\n    invoke-static {p0}, Lcom/naruto/r2/GameMenu;->applyTouch(Lcom/naruto/port/NarutoActivity;)V\n    return-void\n.end method\n'
 game.write_text(source)
 change(game,'.method public onDestroy()V',lambda b:start(b,call('GameLoop','destroy(Lcom/naruto/port/NarutoActivity;)V')))
 change(smali/'a/e0.smali','.method public final onTouch(Landroid/view/View;Landroid/view/MotionEvent;)Z',lambda b:'.method public final onTouch(Landroid/view/View;Landroid/view/MotionEvent;)Z\n    .locals 1\n    iget-object v0, p0, La/e0;->f:La/f0;\n    invoke-static {v0, p1, p2}, Lcom/naruto/r2/GameMenu;->touch(La/f0;Landroid/view/View;Landroid/view/MotionEvent;)Z\n    move-result v0\n    return v0\n.end method')
 change(smali/'a/H.smali','.method public final run()V',lambda b:start(b,'    iget v0, p0, La/H;->a:I\n    iget-object v1, p0, La/H;->b:Lcom/naruto/port/NarutoActivity;\n    invoke-static {v0, v1}, Lcom/naruto/r2/GameLoop;->tick(ILandroid/view/KeyEvent$Callback;)Z\n    move-result v0\n    if-eqz v0, :r2_stock_tick\n    return-void\n    :r2_stock_tick'))
 # Track the stock repeating Play-button animation, preserving its appearance.
 source=launcher.read_text();needle='    invoke-virtual {v1}, Landroid/animation/ValueAnimator;->start()V'
 if source.count(needle)!=1:raise ValueError('Play animator changed')
 launcher.write_text(source.replace(needle,needle+'\n    invoke-static {v7, v1}, Lcom/naruto/r2/LauncherMotion;->register(Landroid/content/Context;Landroid/animation/Animator;)V'))
 for method,target in [('onPause','pause'),('onResume','resume')]:
  change(launcher,'.method public final '+method+'()V',lambda b,t=target:start(b,call('LauncherMotion',t+'(Landroid/app/Activity;)V')))
 with launcher.open('a') as out:out.write('\n.method public onDestroy()V\n    .locals 0\n    invoke-static {p0}, Lcom/naruto/r2/LauncherMotion;->destroy(Landroid/app/Activity;)V\n    invoke-super {p0}, Landroid/app/Activity;->onDestroy()V\n    return-void\n.end method\n')
 # Pause the original ambient drawing loops while the launcher is hidden.
 for name in ['C','s0','M','V','S','W']:
  path=smali/('a/'+name+'.smali');source=path.read_text()
  source,n=re.subn(r'invoke-virtual(?:/range)? \{p0(?: \.\. p0)?\}, Landroid/view/View;->postInvalidateOnAnimation\(\)V','invoke-static/range {p0 .. p0}, Lcom/naruto/r2/LauncherMotion;->schedule(Landroid/view/View;)V',source)
  if n<1:raise ValueError('Drawing loop changed: '+name)
  path.write_text(source)
 with launcher.open('a') as out:out.write('\n.method public onWindowFocusChanged(Z)V\n    .locals 0\n    invoke-super {p0, p1}, Landroid/app/Activity;->onWindowFocusChanged(Z)V\n    if-eqz p1, :r2_launcher_blurred\n    invoke-static {p0}, Lcom/naruto/r2/LauncherMotion;->resume(Landroid/app/Activity;)V\n    return-void\n    :r2_launcher_blurred\n    invoke-static {p0}, Lcom/naruto/r2/LauncherMotion;->pause(Landroid/app/Activity;)V\n    return-void\n.end method\n')
 # Replace the legacy platform theme for all original and added dialogs/popups.
 (root/'res/values/styles.xml').write_text((ROOT/'assets/styles.xml').read_text())
 (root/'res/drawable').mkdir(exist_ok=True)
 shutil.copyfile(ROOT/'assets/dialog_panel.xml',root/'res/drawable/dialog_panel.xml')
 strings=root/'res/values/strings.xml';text=strings.read_text();text,n=re.subn(r'(<string name="app_name">)[^<]*(</string>)',r'\g<1>STORM R\2',text);assert n==1;strings.write_text(text)
 image=root/'res/drawable/stormr_icon.png';image.parent.mkdir(exist_ok=True);shutil.copyfile(ROOT/'assets/stormr_icon.png',image)
 manifest=root/'AndroidManifest.xml';text=manifest.read_text().replace('android:icon="@mipmap/ic_app"','android:icon="@drawable/stormr_icon"').replace('android:roundIcon="@mipmap/ic_app"','android:roundIcon="@drawable/stormr_icon"');manifest.write_text(text)
 yaml=root/'apktool.yml';text=yaml.read_text().replace('  versionCode: 122','  versionCode: 122').replace('  versionName: R1','  versionName: R1');yaml.write_text(text)
 print('PASS: refined R2 menus, performance/lifecycle hooks, stock controls, app name/icon and version applied.')
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('decoded',type=Path);customize(p.parse_args().decoded)
