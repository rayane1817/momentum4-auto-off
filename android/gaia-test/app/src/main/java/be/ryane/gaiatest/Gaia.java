package be.ryane.gaiatest;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * GAIA v3 framing as seen in the MOMENTUM 4 capture (docs/protocol.md):
 * FF 03 | length (u16 BE) | vendor (u16 BE) | command (u16 BE) | payload
 * command = feature << 9 | type << 7 | cmd, type 0 command, 1 notification, 2 response, 3 error.
 */
final class Gaia {
    static final UUID SERVICE_UUID = UUID.fromString("a2129ff3-081b-4c45-8afe-469d9c4842ec");
    static final int SENNHEISER = 0x0495;
    static final int QUALCOMM = 0x001D;

    // Confirmed on fw 3.38.3 (tests T8-T10): remote true power-off, empty payload.
    static final int CMD_POWER_OFF = 0x0605;
    static final int CMD_PHYSICAL_STATE_GET = 0x0402;   // resp 0x0502, [headset/left, right]: 1 in case, 2 off head, 3 on head
    static final int CMD_BATTERY_GET = 0x0603;
    static final int CMD_AUDIO_STREAM_NOTIF = 0x0880;   // payload[0]: FF = no stream, else active (e.g. 02)

    static byte[] powerOff() {
        return build(SENNHEISER, CMD_POWER_OFF);
    }

    static final class Frame {
        final int version, vendor, command;
        final byte[] payload;

        Frame(int version, int vendor, int command, byte[] payload) {
            this.version = version;
            this.vendor = vendor;
            this.command = command;
            this.payload = payload;
        }

        int type() {
            return (command >> 7) & 3;        // 0 cmd, 1 notif, 2 resp, 3 error
        }

        int base() {
            return command & ~(3 << 7);       // command id with the type bits cleared
        }
    }

    static byte[] build(int vendor, int command, byte... payload) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0xFF);
        out.write(0x03);
        out.write((payload.length >> 8) & 0xFF);
        out.write(payload.length & 0xFF);
        out.write((vendor >> 8) & 0xFF);
        out.write(vendor & 0xFF);
        out.write((command >> 8) & 0xFF);
        out.write(command & 0xFF);
        out.write(payload, 0, payload.length);
        return out.toByteArray();
    }

    /** Commands that must never be sent: they can brick, wipe or unpair the headset. */
    static String blockedReason(byte[] frame) {
        if (frame.length < 8 || (frame[0] & 0xFF) != 0xFF) return null;
        int vendor = u16(frame, 4), cmd = u16(frame, 6);
        int feature = cmd >> 9;
        if (vendor == SENNHEISER) {
            if (cmd == 0x0040) return "factory reset";
            if (feature == 1) return "feature 1 = firmware update / DFU mode";
            if (cmd == 0x1405 || cmd == 0x1406) return "deletes paired devices";
        }
        if (vendor == QUALCOMM && feature == 6) return "Qualcomm upgrade feature";
        return null;
    }

    /** Pulls complete frames out of a growing receive buffer. Drops junk before an 0xFF. */
    static final class Parser {
        private final ByteArrayOutputStream buf = new ByteArrayOutputStream();

        List<Frame> feed(byte[] data, int len) {
            buf.write(data, 0, len);
            List<Frame> frames = new ArrayList<>();
            byte[] b = buf.toByteArray();
            int i = 0;
            while (true) {
                while (i < b.length && (b[i] & 0xFF) != 0xFF) i++;
                if (b.length - i < 8) break;
                int plen = u16(b, i + 2);
                if (b.length - i < 8 + plen) break;
                byte[] payload = new byte[plen];
                System.arraycopy(b, i + 8, payload, 0, plen);
                frames.add(new Frame(b[i + 1] & 0xFF, u16(b, i + 4), u16(b, i + 6), payload));
                i += 8 + plen;
            }
            buf.reset();
            buf.write(b, i, b.length - i);
            return frames;
        }
    }

    static String describe(Frame f) {
        int type = (f.command >> 7) & 3;
        int base = f.command & ~(3 << 7);
        String t = new String[]{"cmd", "notif", "resp", "ERROR"}[type];
        StringBuilder s = new StringBuilder(String.format("%04X:%04X %s [%s]", f.vendor, f.command, t, hex(f.payload)));
        if (f.vendor == SENNHEISER && base == 0x0601 && type == 2 && f.payload.length >= 3) {
            int timer = f.payload[0] & 0xFF, secs = u16(f.payload, 1);
            String name = timer == 0 ? "auto power-off" : timer == 1 ? "LIMBO timeout" : "timer " + timer;
            s.append(String.format("  => %s = %d s (%s)", name, secs, secs == 0 ? "never/disabled" : (secs / 60.0) + " min"));
        } else if (f.vendor == SENNHEISER && base == 0x0600 && type == 2) {
            s.append("  => timer set OK");
        } else if (f.vendor == SENNHEISER && base == 0x0603 && type != 3 && f.payload.length >= 1) {
            s.append("  => battery ").append(f.payload[f.payload.length - 1] & 0xFF).append(" %");
        } else if (f.vendor == SENNHEISER && type != 3) {
            String d = decodeConfig(base, type, f.payload);
            if (d != null) s.append("  => ").append(d);
        }
        if (type == 3) {
            s.append("  => refused (").append(f.payload.length >= 1 ? errName(f.payload[0] & 0xFF) : "?").append(")");
        }
        return s.toString();
    }

    private static String errName(int code) {
        if (code == 1) return "not supported";
        if (code == 5) return "invalid parameter";
        return "code " + code;
    }

    /** Human-readable decode of config GET responses / notifications. base = command with type bits cleared. */
    private static String decodeConfig(int base, int type, byte[] p) {
        int b0 = p.length >= 1 ? p[0] & 0xFF : -1;
        switch (base) {
            case 0x1A05: return "ANC " + onOff(b0);                                  // ANC on/off
            case 0x1A03: return "transparency level = " + b0;                        // 0x28 = 40 seen
            case 0x1805: return "transparent hearing " + onOff(b0);
            case 0x1A01:                                                             // combined mode: (mode,state) triples
                StringBuilder m = new StringBuilder("noise mode:");
                for (int i = 0; i + 1 < p.length; i += 2) {
                    m.append(' ').append(modeName(p[i] & 0xFF)).append('=').append(p[i + 1] & 0xFF);
                }
                return m.toString();
            case 0x0806: return "sidetone " + onOff(b0);
            case 0x080D: return "smart pause " + onOff(b0);
            case 0x080B: return "auto-answer calls " + onOff(b0);
            case 0x0815: return "comfort call " + onOff(b0);
            case 0x0818: return "low latency " + onOff(b0);
            case 0x0802: return "voice/tone prompts mode = " + b0;
            case 0x0800:                                                            // 0800 codec (resp) shares a base with 0880 audio-stream notif
                return type == 1 ? "audio stream " + (b0 == 0xFF ? "stopped" : "active") : "codec = " + b0;
            case 0x1200: return "hw revision = " + (p.length >= 2 ? u16(p, 0) : b0);   // value in the u16, e.g. 00 04 = 4
            case 0x1201:                                                            // firmware: 3 x u16 BE, e.g. 0003 0026 0003 = 3.38.3
            case 0x1202:
                if (p.length >= 6) return "firmware = " + u16(p, 0) + "." + u16(p, 2) + "." + u16(p, 4);
                return "firmware = " + hex(p);
            case 0x1206: return "model = " + ascii(p);
            default: return null;
        }
    }

    private static String onOff(int v) { return v == 1 ? "ON" : v == 0 ? "off" : "?(" + v + ")"; }

    private static String modeName(int m) {
        switch (m) { case 1: return "antiWind"; case 2: return "transparency"; case 3: return "adaptive"; default: return "m" + m; }
    }

    private static String ascii(byte[] p) {
        StringBuilder s = new StringBuilder();
        for (byte x : p) { if (x == 0) break; if (x >= 32 && x < 127) s.append((char) x); }
        return s.toString();
    }

    static byte[] parseHex(String text) {
        String clean = text.replaceAll("[^0-9a-fA-F]", "");
        if (clean.length() % 2 != 0) throw new IllegalArgumentException("odd number of hex digits");
        byte[] out = new byte[clean.length() / 2];
        for (int i = 0; i < out.length; i++) out[i] = (byte) Integer.parseInt(clean.substring(2 * i, 2 * i + 2), 16);
        return out;
    }

    static String hex(byte[] b) {
        StringBuilder s = new StringBuilder();
        for (byte x : b) s.append(s.length() == 0 ? "" : " ").append(String.format("%02X", x & 0xFF));
        return s.toString();
    }

    static int u16(byte[] b, int off) {
        return ((b[off] & 0xFF) << 8) | (b[off + 1] & 0xFF);
    }

    private Gaia() {}
}
