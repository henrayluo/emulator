package cn.banny.emulator.svc;

import cn.banny.emulator.Emulator;
import unicorn.Unicorn;

public interface SvcHandler {

    int handle(Unicorn u, Emulator emulator);

}
