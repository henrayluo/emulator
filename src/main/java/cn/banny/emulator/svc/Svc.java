package cn.banny.emulator.svc;

import cn.banny.emulator.Emulator;
import cn.banny.emulator.SvcMemory;
import cn.banny.emulator.pointer.UnicornPointer;
import unicorn.Unicorn;

public interface Svc {

    UnicornPointer onRegister(SvcMemory svcMemory, int svcNumber);

    int handle(Unicorn u, Emulator emulator);

}
