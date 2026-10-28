#!/usr/bin/env python3
"""Turns a decoded TizenTube Cobalt release into a side-by-side test build with home screen rows.

Usage: patch_apk.py <apktool dir>

- New app id (io.gh.reisxd.tizentube.cobalt.hometest) and label, so it installs beside the real app.
- Manifest: WRITE_EPG_DATA permission and the card-removed receiver.
- BaseStarboardBridge: ShowAlertDialog("tizentube:home", json) is routed to HomeChannels.publish,
  and HasSystemFeature("tizentube.home_screen") answers true, standing in for the PublishHomeScreen
  IDL method until a real build has it.
- libchrobalt.so: the userscript URL (same length) points at the test script.
"""
import glob, os, re, sys

d = sys.argv[1]
OLD_ID = 'io.gh.reisxd.tizentube.cobalt'
NEW_ID = OLD_ID + '.hometest'
OLD_URL = b'https://cdn.jsdelivr.net/npm/@foxreis/tizentube/dist/userScript.js?v='
NEW_URL = b'https://cdn.jsdelivr.net/gh/1OO1O11O/tuberows@tt/userScriptTest.js?v='
assert len(OLD_URL) == len(NEW_URL), (len(OLD_URL), len(NEW_URL))

def edit(path, fn):
    with open(path, encoding='utf-8') as f:
        s = f.read()
    t = fn(s)
    assert t != s, path
    with open(path, 'w', encoding='utf-8') as f:
        f.write(t)

# ---- manifest
def manifest(s):
    ns = re.search(r'xmlns:(\w+)="http://schemas.android.com/apk/res/android"', s).group(1)
    # Authorities and package-scoped permission names follow the new id; class names don't use it.
    s = s.replace('"' + OLD_ID + '.', '"' + NEW_ID + '.')
    s = re.sub(r'(<application[^>]*?\b%s:label=")[^"]*"' % ns, r'\1TizenTube Home Test"', s, count=1)
    perm = '    <uses-permission %s:name="com.android.providers.tv.permission.WRITE_EPG_DATA" />\n' % ns
    s = s.replace('<application', perm + '    <application', 1)
    recv = ('        <receiver {n}:name="dev.cobalt.coat.HomeChannelsReceiver" {n}:exported="true">\n'
            '            <intent-filter>\n'
            '                <action {n}:name="android.media.tv.action.PREVIEW_PROGRAM_BROWSABLE_DISABLED" />\n'
            '                <action {n}:name="android.media.tv.action.WATCH_NEXT_PROGRAM_BROWSABLE_DISABLED" />\n'
            '            </intent-filter>\n'
            '        </receiver>\n').format(n=ns)
    s = s.replace('</application>', recv + '    </application>', 1)
    return s
edit(os.path.join(d, 'AndroidManifest.xml'), manifest)

def yml(s):
    if 'renameManifestPackage' in s:
        return re.sub(r'renameManifestPackage:.*', 'renameManifestPackage: ' + NEW_ID, s)
    return s.rstrip('\n') + '\nrenameManifestPackage: ' + NEW_ID + '\n'
edit(os.path.join(d, 'apktool.yml'), yml)

# ---- smali
bridge = glob.glob(os.path.join(d, 'smali*', 'dev', 'cobalt', 'coat', 'BaseStarboardBridge.smali'))
assert len(bridge) == 1, bridge
B = 'Ldev/cobalt/coat/BaseStarboardBridge;'

ALERT = '''
    const-string v0, "tizentube:home"
    invoke-virtual {v0, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v0
    if-eqz v0, :tt_home_normal
    iget-object v0, p0, %s->mAppContext:Landroid/content/Context;
    invoke-static {v0, p2}, Ldev/cobalt/coat/HomeChannels;->publish(Landroid/content/Context;Ljava/lang/String;)V
    return-void
    :tt_home_normal
''' % B
FEATURE = '''
    const-string v0, "tizentube.home_screen"
    invoke-virtual {v0, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v0
    if-eqz v0, :tt_home_feature
    const/4 v0, 0x1
    return v0
    :tt_home_feature
'''

def inject(s, signature, code):
    start = s.index('.method ' + signature)
    end = s.index('.end method', start)
    body = s[start:end]
    # After the parameter annotations, before the first instruction.
    anchor = body.rindex('.end annotation') + len('.end annotation') if '.end annotation' in body else body.index('\n', body.index('.locals')) 
    return s[:start] + body[:anchor] + '\n' + code + body[anchor:] + s[end:]

def smali(s):
    s = inject(s, 'protected showAlertDialog(Ljava/lang/String;Ljava/lang/String;)V', ALERT)
    s = inject(s, 'protected hasSystemFeature(Ljava/lang/String;)Z', FEATURE)
    return s
edit(bridge[0], smali)

# ---- userscript URL in the native library
libs = glob.glob(os.path.join(d, 'lib', '*', 'libchrobalt.so'))
assert libs, 'no libchrobalt.so'
for lib in libs:
    with open(lib, 'rb') as f:
        data = f.read()
    n = data.count(OLD_URL)
    assert n == 1, (lib, n)
    i = data.index(OLD_URL)
    print('url context:', data[max(0, i - 200):i + 120].decode('latin-1').replace('\x00', '|'))
    with open(lib, 'wb') as f:
        f.write(data.replace(OLD_URL, NEW_URL))
print('patched', d)
