package cn.banny.emulator.svc;

import cn.banny.emulator.SvcMemory;

public interface Svc {

    void onRegister(SvcMemory svcMemory, int svcNumber);

}
