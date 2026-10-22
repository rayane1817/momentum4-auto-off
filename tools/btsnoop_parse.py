#!/usr/bin/env python3
"""Minimal btsnoop (H4, datalink 1002) parser for Bluetooth Classic RFCOMM + BLE ATT.

Usage: python3 btsnoop_parse.py capture.cfa [--summary] [--mac AA:BB:..]
Prints connections, L2CAP channels and RFCOMM/ATT payloads with local timestamps.
"""
import struct, sys, datetime, collections

EPOCH_DELTA = 0x00DCDDB30F2F8000  # btsnoop timestamp (us since 0 AD) -> unix us


def records(path):
    with open(path, 'rb') as f:
        hdr = f.read(16)
        assert hdr[:8] == b'btsnoop\x00', 'not a btsnoop file'
        dl = struct.unpack('>I', hdr[12:16])[0]
        assert dl == 1002, f'unsupported datalink {dl}'
        while True:
            rh = f.read(24)
            if len(rh) < 24:
                return
            olen, ilen, flags, drops, ts = struct.unpack('>IIIIq', rh)
            data = f.read(ilen)
            sent = not (flags & 1)  # bit0: 0 = sent (host->controller), 1 = received
            t = datetime.datetime.fromtimestamp((ts - EPOCH_DELTA) / 1e6)
            yield t, sent, data


def mac(b):
    return ':'.join(f'{x:02X}' for x in reversed(b))


class State:
    def __init__(self):
        self.handles = {}           # acl handle -> bdaddr
        self.names = {}             # bdaddr -> name
        self.frag = {}              # (handle, dir) -> bytearray
        self.chan = {}              # (handle, local/remote cid) -> psm
        self.pending = {}           # (handle, scid) -> psm
        self.rfcomm = []            # (t, dir, addr, dlci, payload)
        self.att = []
        self.events = []


def h(b):
    return b.hex(' ')


def handle_l2cap(st, t, sent, handle, pkt):
    if len(pkt) < 4:
        return
    ln, cid = struct.unpack('<HH', pkt[:4])
    payload = pkt[4:4 + ln]
    addr = st.handles.get(handle, f'h{handle:#x}')
    d = '->' if sent else '<-'
    if cid == 1:  # signalling
        i = 0
        while i + 4 <= len(payload):
            code, ident, l = payload[i], payload[i + 1], struct.unpack('<H', payload[i + 2:i + 4])[0]
            body = payload[i + 4:i + 4 + l]
            if code == 0x02 and len(body) >= 4:  # conn req: psm, scid
                psm, scid = struct.unpack('<HH', body[:4])
                st.pending[(handle, ident)] = (psm, scid, sent)
            elif code == 0x03 and len(body) >= 8:  # conn rsp: dcid, scid, result
                dcid, scid, res, _ = struct.unpack('<HHHH', body[:8])
                p = st.pending.get((handle, ident))
                if p and res == 0:
                    psm = p[0]
                    st.chan[(handle, dcid)] = psm
                    st.chan[(handle, scid)] = psm
                    st.events.append((t, f'L2CAP channel psm={psm:#06x} cids {scid:#x}/{dcid:#x} {addr}'))
            i += 4 + l
        return
    if cid == 4:  # ATT (BLE)
        st.att.append((t, d, addr, payload))
        return
    psm = st.chan.get((handle, cid))
    if psm == 0x0003:  # RFCOMM
        if len(payload) < 3:
            return
        a, ctrl = payload[0], payload[1]
        dlci = a >> 2
        if payload[2] & 1:
            plen, off = payload[2] >> 1, 3
        else:
            plen, off = (payload[2] >> 1) | (payload[3] << 7), 4
        ftype = ctrl & 0xEF
        if ftype == 0xEF:  # UIH
            if ctrl & 0x10 and dlci != 0:  # credit byte present
                off += 1
            data = payload[off:off + plen]
            if dlci != 0 and data:
                st.rfcomm.append((t, d, addr, dlci, bytes(data)))
            elif dlci == 0:
                st.events.append((t, f'RFCOMM mux ctrl {d} {h(data[:12])}'))
        else:
            names = {0x2F: 'SABM', 0x63: 'UA', 0x0F: 'DM', 0x43: 'DISC'}
            st.events.append((t, f'RFCOMM {names.get(ftype, hex(ftype))} dlci={dlci} {d} {addr}'))


def run(path):
    st = State()
    for t, sent, data in records(path):
        if not data:
            continue
        typ, p = data[0], data[1:]
        if typ == 4 and len(p) >= 2:  # HCI event
            code = p[0]
            ev = p[2:]
            if code == 0x03 and len(ev) >= 9 and ev[0] == 0:  # conn complete
                hd = struct.unpack('<H', ev[1:3])[0] & 0x0FFF
                st.handles[hd] = mac(ev[3:9])
                st.events.append((t, f'ACL connect handle={hd:#x} {mac(ev[3:9])}'))
            elif code == 0x05 and len(ev) >= 4:  # disconn complete
                hd = struct.unpack('<H', ev[1:3])[0] & 0x0FFF
                st.events.append((t, f'ACL disconnect handle={hd:#x} {st.handles.get(hd)} reason={ev[3]:#x}'))
            elif code == 0x07 and len(ev) >= 7:  # remote name
                name = ev[7:].split(b'\x00')[0].decode('utf-8', 'replace')
                st.names[mac(ev[1:7])] = name
            elif code == 0x3E and len(ev) >= 1 and ev[0] in (0x01, 0x0A):  # LE conn complete
                hd = struct.unpack('<H', ev[2:4])[0] & 0x0FFF
                st.handles[hd] = mac(ev[6:12])
                st.events.append((t, f'LE connect handle={hd:#x} {mac(ev[6:12])}'))
        elif typ == 2 and len(p) >= 4:  # ACL
            hf, ln = struct.unpack('<HH', p[:4])
            hd, pb = hf & 0x0FFF, (hf >> 12) & 3
            frag = p[4:4 + ln]
            key = (hd, sent)
            if pb in (0, 2):  # start
                st.frag[key] = bytearray(frag)
            else:
                st.frag.setdefault(key, bytearray()).extend(frag)
            buf = st.frag[key]
            if len(buf) >= 4 and len(buf) >= 4 + struct.unpack('<H', buf[:2])[0]:
                handle_l2cap(st, t, sent, hd, bytes(buf))
                st.frag[key] = bytearray()
    return st


if __name__ == '__main__':
    path = sys.argv[1]
    st = run(path)
    print('# names:', st.names)
    print('# handles:', st.handles)
    for t, e in st.events:
        print(t.strftime('%H:%M:%S.%f')[:-3], e)
    cnt = collections.Counter((a, dl) for _, _, a, dl, _ in st.rfcomm)
    print('# RFCOMM payload counts per (addr, dlci):', dict(cnt))
    print('# ATT packets:', len(st.att))
    if '--summary' in sys.argv:
        sys.exit()
    want = None
    if '--mac' in sys.argv:
        want = sys.argv[sys.argv.index('--mac') + 1].upper()
    for t, d, a, dl, data in st.rfcomm:
        if want and a != want:
            continue
        print(t.strftime('%H:%M:%S.%f')[:-3], d, a, f'dlci={dl}', h(data))
