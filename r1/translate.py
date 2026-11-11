#!/usr/bin/env python3
"""Customize a freshly decoded v1.1.11 APK tree; never build or sign an APK."""
import argparse
import hashlib
from collections import Counter
import json
from pathlib import Path
import re
import shutil

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('decoded_apk', type=Path)
args = parser.parse_args()
root = args.decoded_apk
here = Path(__file__).resolve().parent
translations = json.loads((here/'translations.json').read_text())
native_input = root/'lib/arm64-v8a/librevolution.so'
if hashlib.sha256(native_input.read_bytes()).hexdigest() != '94d6294c16adfe140d4c6667afe349fef15c7a4e3fd639799c7700191d5b8721':
    raise ValueError('Requires a freshly decoded, unmodified upstream v1.1.11 APK tree')
hits = Counter()
pattern = re.compile(r'"(?:[^"\\]|\\.)*"')

def translate(match):
    try:
        original = json.loads(match.group())
    except json.JSONDecodeError:
        return match.group()
    if original not in translations:
        return match.group()
    hits[original] += 1
    return json.dumps(translations[original], ensure_ascii=True)

for smali in root.glob('smali*/**/*.smali'):
    text = smali.read_text()
    translated = pattern.sub(translate, text)
    if translated != text:
        smali.write_text(translated)

# Remote release notes are Portuguese and are not part of the static string table.
# Keep update detection, but replace their displayed contents with an English notice.
for override in json.loads((here/'ui-overrides.json').read_text()):
    path = root/override['file']
    source = path.read_text()
    if source.count(override['old']) != 1:
        raise ValueError('Unexpected updater UI: ' + override['file'])
    path.write_text(source.replace(override['old'], override['new']))

# Disable decorative click effects without changing option callbacks.
for override in json.loads((here/'smali-method-overrides.json').read_text()):
    path = root/override['file']
    source = path.read_text()
    if source.count(override['old']) != 1:
        raise ValueError('Unexpected effect handler: ' + override['signature'])
    path.write_text(source.replace(override['old'], override['new'], 1))

# User-requested launcher changes, separate from the translated strings.
for override in json.loads((here/'feature-overrides.json').read_text()):
    path = root/override['file']
    source = path.read_text()
    if source.count(override['old']) != 1:
        raise ValueError('Unexpected feature patch target: ' + override['reason'])
    path.write_text(source.replace(override['old'], override['new'], 1))
for added in (here/'extra-smali').rglob('*.smali'):
    destination = root/'smali'/added.relative_to(here/'extra-smali')
    if destination.exists():
        raise ValueError('Refusing to overwrite extra class: ' + str(destination))
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(added, destination)

# Gate every app/SDL Java log-output call independently of native ABI bridging.
for source in (root/'smali').rglob('*.smali'):
    if source.name == 'AppLog.smali':
        continue
    text = source.read_text()
    updated = re.sub(r'Landroid/util/Log;->([vdiwe]\([^\n]*\)I)', r'Lcom/naruto/port/AppLog;->\1', text)
    updated = re.sub(r'invoke-virtual (\{[^}]+\}), Ljava/lang/Throwable;->printStackTrace\(\)V',
                     r'invoke-static \1, Lcom/naruto/port/AppLog;->stackTrace(Ljava/lang/Throwable;)V', updated)
    # The upstream PrintStream calls are SDL's System.err diagnostics only.
    if source.name == 'SDLActivity.smali':
        updated = re.sub(r'invoke-virtual (\{[^}]+\}), Ljava/io/PrintStream;->println\(Ljava/lang/String;\)V',
                         r'invoke-static \1, Lcom/naruto/port/AppLog;->println(Ljava/io/PrintStream;Ljava/lang/String;)V', updated)
    if updated != text:
        source.write_text(updated)

native = root/'lib/arm64-v8a/librevolution.so'
data = bytearray(native.read_bytes())
native_hits = {}
for original, english in json.loads((here/'native-translations.json').read_text()).items():
    old, new = original.encode(), english.encode()
    if len(new) > len(old):
        raise ValueError(f'Native translation is too long: {original}')
    # Keep the original binary layout and format placeholders intact.
    formats = re.compile(rb'%(?:[-+ #0]*\d*)?(?:\.\d+)?(?:l)?[difsuXx%]')
    if formats.findall(old) != formats.findall(new):
        raise ValueError(f'Format placeholders changed: {original}')
    needle = old
    count = data.count(needle)
    if count != 1:
        raise ValueError(f'Expected one native string, got {count}: {original}')
    offset = data.index(needle)
    data[offset:offset+len(old)] = new.ljust(len(old), b' ')
    native_hits[original] = offset
native.write_bytes(data)

licenses = root/'assets/licenses.txt'
text = licenses.read_text()
separator = '=' * 60
original_header, rest = text.split(separator, 1)
expected_start = 'Componentes de código aberto usados neste app'
if not original_header.startswith(expected_start):
    raise ValueError('Unexpected license file header')
english_header = '''Open-source components used in this app

FFmpeg (libavcodec, libavutil) - GNU Lesser General Public License 2.1 or later.
FFmpeg is a separate library (lib/arm64-v8a/libffmpeg.so inside the APK) and can be
replaced with another version compiled from FFmpeg source (https://ffmpeg.org).
This app uses the version adapted by Xenia (https://github.com/xenia-canary/FFmpeg).
The license text is at the end of this file.

'''
# Translate explanatory prose only; preserve the license texts and copyright notices.
rest = rest.replace('(O linkernsbypass, parte da libadrenotools, tem a mesma licença e o mesmo autor.)',
                    '(linkernsbypass, part of libadrenotools, has the same license and author.)')
licenses.write_text(english_header + separator + rest)
report = {'java_string_occurrences': sum(hits.values()),
          'unique_java_strings': len(hits), 'native_strings': native_hits,
          'unmatched_translation_keys': sorted(set(translations) - set(hits)),
          'upstream_release_notes_replaced_with_english_notice': True,
          'particle_effects_disabled': True,
          'minimal_bounce_and_press_animations_retained': True,
          'game_language_forced_english': True,
          'logging_control_staged': True,
          'youtube_footer': {'text': 'Modified and Translated by XHYN_PH', 'url': 'https://youtube.com/@xhyn_ph'},
          'native_logging_helper_compiled': False,
          'built': False, 'signed': False}
(here/'translation-report.json').write_text(json.dumps(report, indent=2, ensure_ascii=False))
print(json.dumps(report, indent=2, ensure_ascii=False))
