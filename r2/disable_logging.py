# Copyright (c) 2026 XHYN_PH. SPDX-License-Identifier: MIT
"""Disable log capture and remove log sharing from the regular APK."""
import json
from pathlib import Path
import re

from startup_test import replace

PORT = Path('com/naruto/port')
RUNTIME = Path('com/naruto/r2')
INIT = '.method public static initialize(Landroid/content/Context;)Z'


def replace_once(source, old, new):
    if source.count(old) != 1:
        raise ValueError('Unexpected logging patch target: ' + old.strip().splitlines()[0])
    return source.replace(old, new, 1)


def remove_section(source, first, following, replacement=''):
    if source.count(first) != 1:
        raise ValueError('Unexpected logging section: ' + first.strip().splitlines()[0])
    start = source.index(first)
    end = source.index(following, start + len(first))
    return source[:start] + replacement + source[end:]


def apply(decoded):
    smali = decoded / 'smali'
    overrides = Path(__file__).resolve().parents[1] / 'r1/feature-overrides.json'
    toggle = next(item['new'] for item in json.loads(overrides.read_text())
                  if '"Capture app logs"' in item['new'])
    launcher = smali / PORT / 'LauncherActivity.smali'
    row_start = '''    new-instance v2, La/v0;

    iget-object v5, v7, Lcom/naruto/port/LauncherActivity;->c:Landroid/content/SharedPreferences;

'''
    row_end = '''

    const/4 v2, 0x3

    new-array v6, v2, [Landroid/view/View;

    aput-object v4, v6, v8

    aput-object v5, v6, v11

    const/4 v2, 0x2

    aput-object v1, v6, v2'''
    row = '''    const/4 v2, 0x2

    new-array v6, v2, [Landroid/view/View;

    aput-object v4, v6, v8

    aput-object v1, v6, v11'''
    source = replace_once(launcher.read_text(), row_start + toggle + row_end, row)
    source = replace(source, '.method public final r(Z)V', '')
    source = replace(source, '.method public final r2OriginalSendLog(Z)V', '')
    source = remove_section(source, '    const-string v3, "Send log"\n', '    new-instance v4, Landroid/widget/LinearLayout;')
    source = replace_once(source, '''    invoke-virtual {v4, v1}, Landroid/view/ViewGroup;->addView(Landroid/view/View;)V

    invoke-virtual {v4, v3}, Landroid/view/ViewGroup;->addView(Landroid/view/View;)V
''', '    invoke-virtual {v4, v1}, Landroid/view/ViewGroup;->addView(Landroid/view/View;)V\n')
    source = replace_once(source, r'\n\nUse Send log when reporting the issue; the log describes missing GPU features.', '')
    source = remove_section(source, '''    new-instance v2, Landroid/widget/TextView;

    invoke-direct {v2, p0}, Landroid/widget/TextView;-><init>(Landroid/content/Context;)V

    const-string v6, "SEND LOG"
''', '    new-instance v1, Landroid/widget/TextView;', '    const/16 v6, 0x11\n\n')
    source = replace_once(source, '''    new-instance v1, La/b;

    const/4 v3, 0x1

    invoke-direct {v1, p0, v0, v3}, La/b;-><init>(Landroid/app/Activity;Ljava/lang/Object;I)V

    invoke-virtual {v2, v1}, Landroid/view/View;->setOnClickListener(Landroid/view/View$OnClickListener;)V

''', '')
    source = replace_once(source, '"Abrir o grupo do Shadow: t.me/shadowll_Ports"', '"Open Shadow’s Telegram group"')
    source = replace_once(source, '"desconhecida"', '"unknown"')
    launcher.write_text(replace_once(source, '" (buffer device address, do Vulkan 1.2)"', '" (Vulkan 1.2 buffer device address)"'))

    footer_click = smali / 'a/k.smali'
    footer_click.write_text(replace_once(footer_click.read_text(), '''    :pswitch_1
    sget-object p0, Lcom/naruto/port/LauncherActivity;->w:[Ljava/lang/String;

    invoke-virtual {v3, v2}, Lcom/naruto/port/LauncherActivity;->r(Z)V

''', '    :pswitch_1\n'))
    unsupported_click = smali / 'a/b.smali'
    unsupported_click.write_text(remove_section(unsupported_click.read_text(), '''    :pswitch_0
    sget-object p0, Lcom/naruto/port/LauncherActivity;->w:[Ljava/lang/String;
''', '    :pswitch_1\n', '    :pswitch_0\n    return-void\n\n'))

    control = smali / PORT / 'LoggingControl.smali'
    source = replace(control.read_text(), INIT, INIT + '''
    .locals 1
    const/4 v0, 0x0
    sput-boolean v0, Lcom/naruto/port/AppLog;->capture:Z
    invoke-static {}, Lcom/naruto/port/LoggingControl;->ensureLibrary()Z
    move-result v0
    if-eqz v0, :failed
    const/4 v0, 0x0
    :try_start
    invoke-static {v0}, Lcom/naruto/port/LoggingControl;->nativeSetEnabled(Z)Z
    move-result v0
    :try_end
    .catch Ljava/lang/LinkageError; {:try_start .. :try_end} :missing
    return v0
    :missing
    move-exception v0
    :failed
    const/4 v0, 0x0
    return v0
.end method''')
    source = replace(source, '.method public static initializeJavaOnly(Landroid/content/Context;)V', '')
    control.write_text(source)
    (smali / PORT / 'LoggingToggleListener.smali').unlink()

    # This stock worker used to export Android exit history before each Play.
    worker = smali / 'a/o.smali'
    source, count = re.subn(r'(?ms)^    :pswitch_3\n.*?(?=^    :pswitch_4\n)',
                            '    :pswitch_3\n    return-void\n\n', worker.read_text())
    if count != 1 or 'ApplicationExitInfo;->getTraceInputStream()' in source or '"exit_reasons.txt"' in source:
        raise ValueError('Unexpected stock exit-history worker')
    worker.write_text(source)

    # The updater uses this provider for its single downloaded APK.
    provider = smali / PORT / 'LogProvider.smali'
    source = replace(provider.read_text(), '.method static constructor <clinit>()V', '')
    source = replace_once(source, '.field public static final a:[Ljava/lang/String;\n', '')
    source = remove_section(source, '    :cond_0\n    sget-object v1, Lcom/naruto/port/LogProvider;->a:[Ljava/lang/String;',
                            '    :cond_2\n    new-instance p0, Ljava/io/FileNotFoundException;', '    :cond_0\n')
    provider.write_text(replace_once(source, '    :cond_2\n    new-instance p0, Ljava/io/FileNotFoundException;', '    new-instance p0, Ljava/io/FileNotFoundException;'))
    updater = smali / 'a/C0.smali'
    updater.write_text(replace_once(updater.read_text(), '    sget-object p1, Lcom/naruto/port/LogProvider;->a:[Ljava/lang/String;\n\n', ''))

    for class_path, expected in [(RUNTIME / 'GameHooks.smali', 2), (RUNTIME / 'GameLoop.smali', 2), (PORT / 'NarutoActivity.smali', 1)]:
        path = smali / class_path
        source, count = re.subn(r'(?m)^    invoke-static(?:/range)? \{[^{}]+\}, Lcom/naruto/r2/SessionDetails;->[^\n]+\n',
                                '', path.read_text())
        if count != expected:
            raise ValueError('Unexpected session callbacks in ' + path.name)
        path.write_text(source)
    for name in ('SessionDetails', 'CrashTrace', 'NativeBacktrace', 'RendererReport'):
        for path in (smali / RUNTIME).glob(name + '*.smali'):
            path.unlink()
