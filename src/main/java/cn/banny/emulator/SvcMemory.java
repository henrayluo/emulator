package cn.banny.emulator;

import cn.banny.emulator.pointer.UnicornPointer;
import cn.banny.emulator.svc.Svc;
import unicorn.Unicorn;
import unicorn.UnicornConst;

import java.util.HashMap;
import java.util.Map;

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

    private int svcNumber = 1;

    private final Map<Integer, Svc> svcMap = new HashMap<>();

    public Svc getSvc(int svcNumber) {
        return svcMap.get(svcNumber);
    }

    public UnicornPointer registerSvc(Svc svc) {
        int number = svcNumber++;
        svcMap.put(number, svc);
        return svc.onRegister(this, number);
    }

}
