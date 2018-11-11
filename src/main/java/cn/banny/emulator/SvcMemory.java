package cn.banny.emulator;

import cn.banny.emulator.pointer.UnicornPointer;
import unicorn.Unicorn;
import unicorn.UnicornConst;

public class SvcMemory {

    private UnicornPointer base;

    SvcMemory(Unicorn unicorn, long base, int size) {
        this.base = UnicornPointer.pointer(unicorn, base);
        assert this.base != null;
        this.base.setSize(size);

        unicorn.mem_map(base, size, UnicornConst.UC_PROT_READ | UnicornConst.UC_PROT_EXEC);
    }

    public UnicornPointer allocate(int size) {
        UnicornPointer pointer = base.share(0, size);
        base = (UnicornPointer) base.share(size);
        return pointer;
    }

}
