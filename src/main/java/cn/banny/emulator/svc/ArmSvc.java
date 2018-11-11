package cn.banny.emulator.svc;

import cn.banny.emulator.SvcMemory;
import cn.banny.emulator.pointer.UnicornPointer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public abstract class ArmSvc implements Svc {

    private static final byte[] BX_LR = new byte[] {
            (byte) 0x1e, (byte) 0xff, (byte) 0x2f, (byte) 0xe1 // bx lr
    };

    @Override
    public UnicornPointer onRegister(SvcMemory svcMemory, int svcNumber) {
        ByteBuffer buffer = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(svcNumber | 0xef000000);
        buffer.put(BX_LR);
        UnicornPointer pointer = svcMemory.allocate(8);
        pointer.write(0, buffer.array(), 0, 8);
        return pointer;
    }

}
