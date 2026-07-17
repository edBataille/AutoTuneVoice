package br.com.autotunevoice;

import java.io.*;
import java.nio.*;
import java.nio.file.Files;

public final class WavIO {
    private WavIO() {}

    public static void writeMono16(File file, short[] pcm, int sampleRate) throws IOException {
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
            int dataSize = pcm.length * 2;
            writeAscii(out, "RIFF"); writeLE32(out, 36 + dataSize); writeAscii(out, "WAVE");
            writeAscii(out, "fmt "); writeLE32(out, 16); writeLE16(out, 1); writeLE16(out, 1);
            writeLE32(out, sampleRate); writeLE32(out, sampleRate * 2); writeLE16(out, 2); writeLE16(out, 16);
            writeAscii(out, "data"); writeLE32(out, dataSize);
            for (short value : pcm) writeLE16(out, value);
        }
    }

    public static short[] readMono16(File file) throws IOException {
        byte[] bytes = Files.readAllBytes(file.toPath());
        ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int dataOffset = 44;
        for (int i = 12; i + 8 < bytes.length;) {
            String id = new String(bytes, i, 4, java.nio.charset.StandardCharsets.US_ASCII);
            int size = b.getInt(i + 4);
            if ("data".equals(id)) { dataOffset = i + 8; break; }
            i += 8 + size + (size & 1);
        }
        int count = (bytes.length - dataOffset) / 2;
        short[] pcm = new short[count];
        b.position(dataOffset);
        for (int i = 0; i < count; i++) pcm[i] = b.getShort();
        return pcm;
    }

    private static void writeAscii(DataOutputStream out, String s) throws IOException { out.writeBytes(s); }
    private static void writeLE16(DataOutputStream out, int v) throws IOException { out.writeByte(v & 255); out.writeByte((v >>> 8) & 255); }
    private static void writeLE32(DataOutputStream out, int v) throws IOException { writeLE16(out, v); writeLE16(out, v >>> 16); }
}
