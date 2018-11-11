package cn.banny.emulator;

import cn.banny.emulator.pointer.UnicornPointer;
import cn.banny.emulator.svc.ArmSvc;
import cn.banny.emulator.svc.Svc;
import cn.banny.emulator.svc.ThumbSvc;
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

    private int thumbSvcNumber = 0;
    private int armSvcNumber = 0xff;

    private final Map<Integer, Svc> svcMap = new HashMap<>();

    public Svc getSvc(int svcNumber) {
        return svcMap.get(svcNumber);
    }

    public UnicornPointer registerSvc(Svc svc) {
        final int number;
        if (svc instanceof ThumbSvc) {
            number = ++thumbSvcNumber;
        } else if (svc instanceof ArmSvc) {
            number = ++armSvcNumber;
        } else {
            throw new IllegalStateException();
        }
        if (svcMap.put(number, svc) != null) {
            throw new IllegalStateException();
        }
        return svc.onRegister(this, number);
    }

}
