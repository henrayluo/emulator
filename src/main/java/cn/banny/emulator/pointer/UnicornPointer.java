package cn.banny.emulator.pointer;

import cn.banny.emulator.InvalidMemoryAccessException;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import unicorn.Unicorn;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public class UnicornPointer extends Pointer {

    private final Unicorn unicorn;
    public final long peer;

    private UnicornPointer(Unicorn unicorn, long peer) {
        super(0);

        this.unicorn = unicorn;
        this.peer = peer;
    }

    private long size;

    public UnicornPointer setSize(long size) {
        if (size < 0) {
            throw new IllegalArgumentException("size=" + size);
        }
        this.size = size;
        return this;
    }

    public static UnicornPointer pointer(Unicorn unicorn, long addr) {
        return addr == 0 ? null : new UnicornPointer(unicorn, addr);
    }

    private static UnicornPointer pointer(Unicorn unicorn, Number number) {
        return pointer(unicorn, number.intValue() & 0xffffffffL);
    }

    public static UnicornPointer register(Unicorn unicorn, int reg) {
        return pointer(unicorn, (Number) unicorn.reg_read(reg));
    }

    @Override
    public long indexOf(long offset, byte value) {
        throw new AbstractMethodError();
    }

    @Override
    public void read(long offset, byte[] buf, int index, int length) {
        byte[] data = getByteArray(offset, length);
        System.arraycopy(data, 0, buf, index, length);
    }

    @Override
    public void read(long offset, short[] buf, int index, int length) {
        throw new AbstractMethodError();
    }

    @Override
    public void read(long offset, char[] buf, int index, int length) {
        throw new AbstractMethodError();
    }

    @Override
    public void read(long offset, int[] buf, int index, int length) {
        throw new AbstractMethodError();
    }

    @Override
    public void read(long offset, long[] buf, int index, int length) {
        throw new AbstractMethodError();
    }

    @Override
    public void read(long offset, float[] buf, int index, int length) {
        throw new AbstractMethodError();
    }

    @Override
    public void read(long offset, double[] buf, int index, int length) {
        throw new AbstractMethodError();
    }

    @Override
    public void read(long offset, Pointer[] buf, int index, int length) {
        throw new AbstractMethodError();
    }

    @Override
    public void write(long offset, byte[] buf, int index, int length) {
        if (size > 0) {
            if (offset < 0) {
                throw new IllegalArgumentException();
            }

            if (size - offset < length) {
                throw new InvalidMemoryAccessException();
            }
        }

        if (index == 0 && buf.length == length) {
            unicorn.mem_write(peer + offset, buf);
        } else {
            byte[] data = new byte[length];
            System.arraycopy(buf, index, data, 0, length);
            unicorn.mem_write(peer + offset, data);
        }
    }

    @Override
    public void write(long offset, short[] buf, int index, int length) {
        throw new AbstractMethodError();
    }

    @Override
    public void write(long offset, char[] buf, int index, int length) {
        throw new AbstractMethodError();
    }

    @Override
    public void write(long offset, int[] buf, int index, int length) {
        throw new AbstractMethodError();
    }

    @Override
    public void write(long offset, long[] buf, int index, int length) {
        throw new AbstractMethodError();
    }

    @Override
    public void write(long offset, float[] buf, int index, int length) {
        throw new AbstractMethodError();
    }

    @Override
    public void write(long offset, double[] buf, int index, int length) {
        throw new AbstractMethodError();
    }

    @Override
    public void write(long offset, Pointer[] buf, int index, int length) {
        throw new AbstractMethodError();
    }

    @Override
    public byte getByte(long offset) {
        return getByteArray(offset, 1)[0];
    }

    @Override
    public char getChar(long offset) {
        return getByteBuffer(offset, 2).getChar();
    }

    @Override
    public short getShort(long offset) {
        return getByteBuffer(offset, 2).getShort();
    }

    @Override
    public int getInt(long offset) {
        return getByteBuffer(offset, 4).getInt();
    }

    @Override
    public long getLong(long offset) {
        return getByteBuffer(offset, 8).getLong();
    }

    @Override
    public NativeLong getNativeLong(long offset) {
        throw new AbstractMethodError();
    }

    @Override
    public float getFloat(long offset) {
        return getByteBuffer(offset, 4).getFloat();
    }

    @Override
    public double getDouble(long offset) {
        return getByteBuffer(offset, 8).getDouble();
    }

    @Override
    public UnicornPointer getPointer(long offset) {
        return pointer(unicorn, (Number) getInt(offset));
    }

    @Override
    public byte[] getByteArray(long offset, int arraySize) {
        return unicorn.mem_read(peer + offset, arraySize);
    }

    @Override
    public ByteBuffer getByteBuffer(long offset, long length) {
        return ByteBuffer.wrap(getByteArray(offset, (int) length)).order(ByteOrder.LITTLE_ENDIAN);
    }

    @Override
    public String getWideString(long offset) {
        throw new AbstractMethodError();
    }

    @Override
    public String getString(long offset) {
        return getString(offset, "UTF-8");
    }

    @Override
    public String getString(long offset, String encoding) {
        long addr = peer + offset;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            byte[] data;
            while ((data = unicorn.mem_read(addr++, 1))[0] != 0) {
                baos.write(data);

                if (baos.size() > 0x1000) { // 4k
                    throw new IllegalStateException("buffer overflow");
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        try {
            return baos.toString(encoding);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private ByteBuffer allocateBuffer(int size) {
        return ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
    }

    @Override
    public void setMemory(long offset, long length, byte value) {
        byte[] data = new byte[(int) length];
        Arrays.fill(data, value);
        write(offset, data, 0, data.length);
    }

    @Override
    public void setByte(long offset, byte value) {
        write(offset, new byte[] { value }, 0, 1);
    }

    @Override
    public void setShort(long offset, short value) {
        write(offset, allocateBuffer(2).putShort(value).array(), 0, 2);
    }

    @Override
    public void setChar(long offset, char value) {
        write(offset, allocateBuffer(2).putChar(value).array(), 0, 2);
    }

    @Override
    public void setInt(long offset, int value) {
        write(offset, allocateBuffer(4).putInt(value).array(), 0, 4);
    }

    @Override
    public void setLong(long offset, long value) {
        write(offset, allocateBuffer(8).putLong(value).array(), 0, 8);
    }

    @Override
    public void setNativeLong(long offset, NativeLong value) {
        throw new AbstractMethodError();
    }

    @Override
    public void setFloat(long offset, float value) {
        write(offset, allocateBuffer(4).putFloat(value).array(), 0, 4);
    }

    @Override
    public void setDouble(long offset, double value) {
        write(offset, allocateBuffer(8).putDouble(value).array(), 0, 8);
    }

    @Override
    public void setPointer(long offset, Pointer value) {
        if (value == null) {
            setInt(offset, 0);
        } else {
            setInt(offset, (int) ((UnicornPointer) value).peer);
        }
    }

    @Override
    public void setWideString(long offset, String value) {
        throw new AbstractMethodError();
    }

    @Override
    public void setString(long offset, WString value) {
        throw new AbstractMethodError();
    }

    @Override
    public void setString(long offset, String value) {
        setString(offset, value, "UTF-8");
    }

    @Override
    public void setString(long offset, String value, String encoding) {
        try {
            byte[] data = value.getBytes(encoding);
            write(offset, Arrays.copyOf(data, data.length + 1), 0, data.length + 1);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public UnicornPointer share(long offset, long sz) {
        if (offset == 0L && sz == size) {
            return this;
        }

        UnicornPointer pointer = new UnicornPointer(unicorn, peer + offset);
        if (size > 0) {
            if (offset < 0) {
                throw new IllegalArgumentException();
            }

            if (offset >= size) {
                throw new InvalidMemoryAccessException();
            }

            long newSize = size - offset;
            pointer.setSize(sz > 0 && sz < newSize ? sz : newSize);
        } else {
            pointer.setSize(sz);
        }
        return pointer;
    }

    @Override
    public String toString() {
        return "unicorn@0x" + Long.toHexString(peer);
    }

}
