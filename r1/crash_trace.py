# Copyright (c) 2026 XHYN_PH. SPDX-License-Identifier: MIT
"""Route the pinned engine's fatal-only exit to Android's crash reporter."""

import struct

ENGINE = 'lib/arm64-v8a/librevolution.so'
MARKER = 'assets/turnip-trace.txt'
VERSION = 'R1-turnip-trace1'

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


def apply(root):
    path = root / ENGINE
    engine = bytearray(path.read_bytes())
    route_fatal_exit(engine)
    path.write_bytes(engine)
    (root / MARKER).write_text(
        VERSION + '\n'
        'Android can record a backtrace when the game engine hits a fatal signal.\n'
        'Enable Logging before Play, then use Send log after the failure.\n'
    )
