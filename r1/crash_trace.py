# Copyright (c) 2026 XHYN_PH. SPDX-License-Identifier: MIT
"""Route the pinned engine's fatal-only exit to Android's crash reporter."""

import re
import struct

ENGINE = 'lib/arm64-v8a/librevolution.so'
MARKER = 'assets/turnip-trace.txt'
VERSION = 'R1-turnip-trace2'

# Verified against the pinned v1.1.11 ELF. The recoverable SIGSEGV branch
# returns before this call. Both callees are libc functions that never return.
EXIT_CALL = 0x24F6B88
TEXT_OFFSET = 0x96F850
ABORT_PLT = 0x27FAB10
EXIT_PLT = 0x27FAB40


def branch_link(offset, target):
    return 0x94000000 | (((target - offset) // 4) & 0x3FFFFFF)


def route_fatal_exit(engine):
    """Replace only BL _exit at the end of the original signal handler."""
    if len(engine) <= EXIT_PLT + 16 or engine[:6] != b'\x7fELF\x02\x01' or struct.unpack_from('<H', engine, 18)[0] != 183:
        raise ValueError('Expected the pinned ARM64 engine')
    expected = {
        EXIT_CALL - 4: (0x52800060, branch_link(EXIT_CALL, EXIT_PLT)),
        ABORT_PLT: (0xF0000170, 0xF9412E11, 0x91096210, 0xD61F0220),
        EXIT_PLT: (0xF0000170, 0xF9413A11, 0x9109C210, 0xD61F0220),
    }
    for offset, words in expected.items():
        if struct.unpack_from('<' + 'I' * len(words), engine, offset) != words:
            raise ValueError(f'Unexpected original crash handler or PLT at {offset:#x}')
    struct.pack_into('<I', engine, EXIT_CALL, branch_link(EXIT_CALL, ABORT_PLT))


def share_crash_summary(root):
    """Keep Send log's normal attachments and omit the raw Android dump in this profile."""
    provider = root / 'smali/com/naruto/port/LogProvider.smali'
    source = provider.read_text()
    old = '''    const-string v7, "tombstone.pb"

'''
    attachment = '''    const-string v8, "session-details.txt"

    filled-new-array/range {v0 .. v8}, [Ljava/lang/String;'''
    if source.count(old) != 1 or source.count(attachment) != 1:
        raise ValueError('Unexpected log attachment list')
    provider.write_text(source.replace(old, '').replace(attachment, attachment.replace('v8', 'v7')))

    worker = root / 'smali/a/o.smali'
    source = worker.read_text()
    # Preserve the exit-history list and its exception range while removing the
    # subsequent first-native-crash copy and its separate exception handlers.
    raw_copy = r'(?ms)^    if-nez v8, :cond_3\n\n    invoke-virtual \{v9\}, Landroid/app/ApplicationExitInfo;->getReason\(\)I\n.*?(?=^    :catch_1\n)'
    source, count = re.subn(raw_copy, '''    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_1

    goto/16 :goto_1

''', source)
    if count != 1 or '"tombstone.pb"' in source or '->getTraceInputStream()' in source:
        raise ValueError('Unexpected exit-history worker')
    start = '    move v8, v5\n\n    :cond_3\n    :goto_1'
    save_history = '    :cond_5\n    new-instance v0, Ljava/io/File;'
    if source.count(start) != 1 or source.count(save_history) != 1:
        raise ValueError('Unexpected exit-history loop')
    old_legend = (r'reason: 2 = exit(), 4 = native crash, 5 = ANR, 6 = signaled, '
                  r'3 = low memory (kernel) / 12 = excessive resource use / 13 = dependency died, 10 = user requested\n')
    new_legend = (r'reason: 1 = self exit, 2 = signaled, 3 = low memory, 4 = Java crash, 5 = native crash, '
                  r'6 = ANR, 9 = excessive resource use, 10 = user requested, 12 = dependency died\n')
    if source.count(old_legend) != 1:
        raise ValueError('Unexpected exit-history legend')
    source = source.replace(start, '    :goto_1').replace(old_legend, new_legend)
    worker.write_text(source.replace(save_history, save_history.replace('    :cond_5\n', '    :cond_5\n    :try_start_5\n')))


def apply(root):
    path = root / ENGINE
    engine = bytearray(path.read_bytes())
    route_fatal_exit(engine)
    path.write_bytes(engine)
    share_crash_summary(root)
    (root / MARKER).write_text(
        VERSION + '\n'
        'Android can record a backtrace when the game engine hits a fatal signal.\n'
        'Send log includes a crash summary without the raw Android crash dump.\n'
        'Enable Logging before Play, then use Send log after the failure.\n'
    )
