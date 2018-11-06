package cn.banny.emulator;

import cn.banny.utils.Hex;
import unicorn.MemHook;
import unicorn.Unicorn;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * trace memory read
 * Created by zhkl0228 on 2017/5/2.
 */

class TraceMemoryHook implements MemHook {

    @Override
    public void hook(Unicorn u, long address, int size, Object user) {
        byte[] data = u.mem_read(address, size);
        String value;
        if (data.length == 4) {
            value = "0x" + Long.toHexString(ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getInt() & 0xffffffffL);
        } else {
            value = Hex.encodeHexString(data);
        }
        System.out.println("### Memory READ at 0x" + Long.toHexString(address) + ", data size = " + size + ", data value = " + value);
    }

    @Override
    public void hook(Unicorn u, long address, int size, long value, Object user) {
        System.out.println("### Memory WRITE at 0x" + Long.toHexString(address) + ", data size = " + size + ", data value = 0x" + Long.toHexString(value));
    }

}
