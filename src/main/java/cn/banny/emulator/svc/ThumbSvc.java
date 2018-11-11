package cn.banny.emulator.svc;

import cn.banny.emulator.SvcMemory;
import cn.banny.emulator.pointer.UnicornPointer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public abstract class ThumbSvc implements Svc {

    private static final byte[] BX_LR = new byte[] {
            (byte) 0x70, (byte) 0x47 // bx lr
    };

    @Override
    public UnicornPointer onRegister(SvcMemory svcMemory, int svcNumber) {
        if (svcNumber > 0xff) {
            throw new IllegalStateException();
        }

        ByteBuffer buffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putShort((short) (svcNumber | 0xdf00));
        buffer.put(BX_LR);
        UnicornPointer pointer = svcMemory.allocate(4);
        pointer.write(0, buffer.array(), 0, 4);
        return pointer;
    }

}
