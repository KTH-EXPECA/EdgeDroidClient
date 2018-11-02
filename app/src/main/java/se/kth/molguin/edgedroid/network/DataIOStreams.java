package se.kth.molguin.edgedroid.network;

import android.support.annotation.NonNull;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class DataIOStreams implements DataInput, DataOutput, AutoCloseable {

    private final DataInputStream dataIn;
    private final DataOutputStream dataOut;

    public DataIOStreams(@NonNull DataInputStream dataIn,
                         @NonNull DataOutputStream dataOut) {
        this.dataIn = dataIn;
        this.dataOut = dataOut;
    }

    public DataIOStreams(@NonNull InputStream in, @NonNull OutputStream out) {
        this(new DataInputStream(in), new DataOutputStream(out));
    }

    @NonNull
    public DataInputStream getDataInputStream() {
        return this.dataIn;
    }

    @NonNull
    public DataOutputStream getDataOutputStream() {
        return this.dataOut;
    }

    @Override
    public void readFully(@NonNull byte[] b) throws IOException {
        this.dataIn.readFully(b);
    }

    @Override
    public void readFully(@NonNull byte[] b, int off, int len) throws IOException {
        this.dataIn.readFully(b, off, len);
    }

    @Override
    public int skipBytes(int n) throws IOException {
        return this.dataIn.skipBytes(n);
    }

    @Override
    public boolean readBoolean() throws IOException {
        return this.dataIn.readBoolean();
    }

    @Override
    public byte readByte() throws IOException {
        return this.dataIn.readByte();
    }

    @Override
    public int readUnsignedByte() throws IOException {
        return this.dataIn.readUnsignedByte();
    }

    @Override
    public short readShort() throws IOException {
        return this.dataIn.readShort();
    }

    @Override
    public int readUnsignedShort() throws IOException {
        return this.dataIn.readUnsignedShort();
    }

    @Override
    public char readChar() throws IOException {
        return this.dataIn.readChar();
    }

    @Override
    public int readInt() throws IOException {
        return this.dataIn.readInt();
    }

    @Override
    public long readLong() throws IOException {
        return this.dataIn.readLong();
    }

    @Override
    public float readFloat() throws IOException {
        return this.dataIn.readFloat();
    }

    @Override
    public double readDouble() throws IOException {
        return this.dataIn.readDouble();
    }

    @Override
    @Deprecated
    public String readLine() throws IOException {
        return this.dataIn.readLine();
    }

    @NonNull
    @Override
    public String readUTF() throws IOException {
        return this.dataIn.readUTF();
    }

    @Override
    public void write(int b) throws IOException {
        this.dataOut.write(b);
    }

    @Override
    public void write(@NonNull byte[] b) throws IOException {
        this.dataOut.write(b);
    }

    @Override
    public void write(@NonNull byte[] b, int off, int len) throws IOException {
        this.dataOut.write(b, off, len);
    }

    @Override
    public void writeBoolean(boolean v) throws IOException {
        this.dataOut.writeBoolean(v);
    }

    @Override
    public void writeByte(int v) throws IOException {
        this.dataOut.writeByte(v);
    }

    @Override
    public void writeShort(int v) throws IOException {
        this.dataOut.writeShort(v);
    }

    @Override
    public void writeChar(int v) throws IOException {
        this.dataOut.writeChar(v);
    }

    @Override
    public void writeInt(int v) throws IOException {
        this.dataOut.writeInt(v);
    }

    @Override
    public void writeLong(long v) throws IOException {
        this.dataOut.writeLong(v);
    }

    @Override
    public void writeFloat(float v) throws IOException {
        this.dataOut.writeFloat(v);
    }

    @Override
    public void writeDouble(double v) throws IOException {
        this.dataOut.writeDouble(v);
    }

    @Override
    public void writeBytes(@NonNull String s) throws IOException {
        this.dataOut.writeBytes(s);
    }

    @Override
    public void writeChars(@NonNull String s) throws IOException {
        this.dataOut.writeChars(s);
    }

    @Override
    public void writeUTF(@NonNull String s) throws IOException {
        this.dataOut.writeUTF(s);
    }

    @Override
    public void close() throws IOException {
        this.dataIn.close();
        this.dataOut.close();
    }

    public void flush() throws IOException {
        this.dataOut.flush();
    }
}
