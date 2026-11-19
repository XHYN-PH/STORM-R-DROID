/* Copyright (c) 2026 XHYN_PH. SPDX-License-Identifier: MIT */
package com.naruto.r2;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Reads only the signal and the crashing thread's relative PCs from an Android tombstone. */
final class NativeBacktrace {
    private static final int MAX_TRACE = 8 * 1024 * 1024;
    private static final int MAX_FRAMES = 32;

    // Field numbers follow AOSP debuggerd/proto/tombstone.proto. We skip all
    // raw memory, registers, app paths and logs, and never share the protobuf.
    static String describe(InputStream input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        for (int count; (count = input.read(chunk)) != -1;) {
            if (count == 0 || bytes.size() + count > MAX_TRACE) throw invalid();
            bytes.write(chunk, 0, count);
        }
        ByteBuffer data = ByteBuffer.wrap(bytes.toByteArray());
        Reader header = new Reader(data.duplicate());
        long crashingTid = 0;
        StringBuilder result = new StringBuilder();
        while (header.next()) {
            if (header.field == 6) crashingTid = header.number();
            else if (header.field == 10) readSignal(header.message(), result);
            else header.skip();
        }
        if (crashingTid == 0) throw invalid();

        Reader body = new Reader(data);
        boolean foundNative = false;
        boolean foundTranslated = false;
        while (body.next()) {
            boolean nativeThread = body.field == 16 && !foundNative;
            boolean translatedThread = body.field == 25 && !foundTranslated;
            if (!nativeThread && !translatedThread) {
                body.skip();
                continue;
            }
            Reader entry = body.message();
            long tid = 0;
            Reader thread = null;
            while (entry.next()) {
                if (entry.field == 1) tid = entry.number();
                else if (entry.field == 2) thread = entry.message();
                else entry.skip();
            }
            if (tid != crashingTid || thread == null) continue;
            String title = translatedThread ? "Translated ARM backtrace" : "Backtrace";
            int frames = 0;
            while (frames < MAX_FRAMES && thread.next()) {
                if (thread.field != 4) {
                    thread.skip();
                    continue;
                }
                if (frames == 0) result.append(title).append(":\n");
                readFrame(thread.message(), result, frames++);
            }
            if (nativeThread) foundNative = frames > 0;
            if (translatedThread) foundTranslated = frames > 0;
        }
        if (!foundNative && !foundTranslated) result.append("Android's trace has no frames for the crashing thread.\n");
        return result.toString();
    }

    private static void readSignal(Reader signal, StringBuilder result) throws IOException {
        long number = 0;
        String name = "";
        while (signal.next()) {
            if (signal.field == 1) number = signal.number();
            else if (signal.field == 2) name = signal.text(32);
            else signal.skip();
        }
        if (number > 0 && number <= 64) {
            result.append("Signal: ");
            if (name.matches("SIG[A-Z0-9]+")) result.append(name).append(" ");
            result.append("(").append(number).append(")\n");
        }
    }

    private static void readFrame(Reader frame, StringBuilder result, int index) throws IOException {
        long pc = 0;
        String module = "", build = "";
        while (frame.next()) {
            if (frame.field == 1) pc = frame.number();
            else if (frame.field == 6) module = frame.text(1024);
            else if (frame.field == 8) build = frame.text(128);
            else frame.skip();
        }
        module = module.substring(module.lastIndexOf('/') + 1);
        if (!module.matches("[A-Za-z0-9_.+\\-\\[\\]]{1,120}")) module = "(unknown module)";
        result.append(String.format(Locale.ROOT, "#%02d pc %016x  %s", index, pc, module));
        if (build.matches("[a-fA-F0-9]{8,128}")) result.append(" (BuildId ").append(build).append(")");
        result.append('\n');
    }

    private static IOException invalid() {
        return new IOException("Invalid or oversized Android native trace");
    }

    private static final class Reader {
        final ByteBuffer data;
        int field, wire;

        Reader(ByteBuffer data) { this.data = data; }

        boolean next() throws IOException {
            if (!data.hasRemaining()) return false;
            long tag = varint();
            if (tag < 8 || tag > 0xffffffffL) throw invalid();
            field = (int)(tag >>> 3);
            wire = (int)(tag & 7);
            return true;
        }

        long number() throws IOException {
            if (wire != 0) throw invalid();
            return varint();
        }

        Reader message() throws IOException {
            int length = length();
            ByteBuffer slice = data.slice();
            slice.limit(length);
            advance(length);
            return new Reader(slice);
        }

        String text(int maximum) throws IOException {
            Reader contents = message();
            if (contents.data.remaining() > maximum) return "";
            return StandardCharsets.UTF_8.decode(contents.data).toString();
        }

        void skip() throws IOException {
            switch (wire) {
                case 0: varint(); break;
                case 1: advance(8); break;
                case 2: advance(length()); break;
                case 5: advance(4); break;
                default: throw invalid();
            }
        }

        private int length() throws IOException {
            if (wire != 2) throw invalid();
            long size = varint();
            if (size < 0 || size > data.remaining()) throw invalid();
            return (int)size;
        }

        private void advance(int bytes) throws IOException {
            if (bytes > data.remaining()) throw invalid();
            data.position(data.position() + bytes);
        }

        private long varint() throws IOException {
            long value = 0;
            for (int shift = 0; shift < 64; shift += 7) {
                if (!data.hasRemaining()) throw invalid();
                int next = data.get() & 255;
                if (shift == 63 && next > 1) throw invalid();
                value |= (long)(next & 127) << shift;
                if ((next & 128) == 0) return value;
            }
            throw invalid();
        }
    }
}
