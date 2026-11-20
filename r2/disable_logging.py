# Copyright (c) 2026 XHYN_PH. SPDX-License-Identifier: MIT
"""Disable automatic log capture in the regular APK."""
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
    source = replace_once(source, '.method public final r2OriginalSendLog(Z)V', '.method public final r(Z)V')
    launcher.write_text(replace_once(source, 'No log yet: launch the game once and try again.', 'There are no saved logs to share.'))

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

    provider = smali / PORT / 'LogProvider.smali'
    source = replace_once(provider.read_text(), '    const-string v7, "tombstone.pb"\n\n', '')
    source = replace_once(source, '    const-string v8, "session-details.txt"\n\n', '')
    provider.write_text(replace_once(source, '{v0 .. v8}, [Ljava/lang/String;', '{v0 .. v6}, [Ljava/lang/String;'))

    for name in ('GameHooks', 'GameLoop'):
        path = smali / RUNTIME / (name + '.smali')
        source, count = re.subn(r'(?m)^    invoke-static(?:/range)? \{[^{}]+\}, Lcom/naruto/r2/SessionDetails;->[^\n]+\n',
                                '', path.read_text())
        if count != 2:
            raise ValueError('Unexpected session callbacks in ' + name)
        path.write_text(source)
    for name in ('SessionDetails', 'CrashTrace', 'NativeBacktrace', 'RendererReport'):
        for path in (smali / RUNTIME).glob(name + '*.smali'):
            path.unlink()
