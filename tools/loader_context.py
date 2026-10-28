import sys, re, hashlib
data = open(sys.argv[1], 'rb').read()
i = data.find(b'JSDelivr is not reachable, TizenTube')
start = data.rfind(b'\x00', 0, i - 1)
end = data.find(b'\x00', i)
script = data[start + 1:end]
print('loader script bytes', len(script))
print(script.decode('latin-1'))
for enc in [script, script.strip()]:
    print('sha256-' + __import__('base64').b64encode(hashlib.sha256(enc).digest()).decode())
for pat in [rb'Content-Security-Policy', rb'unsafe-inline', rb"script-src", rb'sha256-[A-Za-z0-9+/=]{20,}', rb'bypass[_-]?csp', rb'disable[_-]?csp', rb'BypassCSP', rb'csp']:
    hits = [m.start() for m in re.finditer(pat, data)][:5]
    print(pat, len(re.findall(pat, data)), [data[h-60:h+100].decode('latin-1').replace('\x00','|') for h in hits[:3]])
print('=== around ?v= url')
j = data.find(b'dist/userScript.js?v=')
print(data[j-1500:j+800].decode('latin-1').replace('\x00', '|'))
print('=== userscript-ish strings')
for m in sorted(set(re.findall(rb'[\x20-\x7e]{0,80}(?:[Uu]ser[Ss]cript|tizentube|TizenTube)[\x20-\x7e]{0,120}', data)))[:60]:
    print(m.decode('latin-1'))
