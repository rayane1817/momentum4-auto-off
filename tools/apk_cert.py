"""Print the SHA-256 of the signing certificate(s) in an APK v2/v3 signature block (no apksigner needed).

Usage: python tools/apk_cert.py APK [APK ...]   (expected for v0.9.1+: 285fe924...4a78)
"""
import hashlib, struct, sys

def u32(b, o): return struct.unpack_from('<I', b, o)[0]
def u64(b, o): return struct.unpack_from('<Q', b, o)[0]

def lp(b, o):
    n = u32(b, o)
    return b[o + 4:o + 4 + n], o + 4 + n

def certs(path):
    d = open(path, 'rb').read()
    eocd = d.rfind(b'PK\x05\x06')
    cd_off = u32(d, eocd + 16)
    if d[cd_off - 16:cd_off] != b'APK Sig Block 42':
        return {}
    size = u64(d, cd_off - 24)
    start = cd_off - size - 8
    o, end, out = start + 8, cd_off - 24, {}
    while o < end:
        ln = u64(d, o); bid = u32(d, o + 8); val = d[o + 12:o + 8 + ln]; o += 8 + ln
        name = {0x7109871a: 'v2', 0xf05368c0: 'v3', 0x1b93ad61: 'v3.1'}.get(bid)
        if not name:
            continue
        signers, _ = lp(val, 0)
        so = 0
        while so < len(signers):
            signer, so = lp(signers, so)
            signed, _ = lp(signer, 0)
            _digests, co = lp(signed, 0)
            cs, _ = lp(signed, co)
            cc = 0
            while cc < len(cs):
                c, cc = lp(cs, cc)
                out.setdefault(name, []).append(hashlib.sha256(c).hexdigest())
    return out

for p in sys.argv[1:]:
    print(p)
    for k, v in certs(p).items():
        for h in v:
            print(f'  {k}: {h}')
