package org.aeonlucid;

import cn.banny.emulator.EmulatorTest;
import cn.banny.emulator.LibraryResolver;
import cn.banny.emulator.linux.Module;
import cn.banny.emulator.linux.AndroidResolver;
import unicorn.ArmConst;
import unicorn.Unicorn;

import java.io.File;

public class AndroidNativeEmuTest extends EmulatorTest {

    @Override
    protected LibraryResolver createLibraryResolver() {
        return new AndroidResolver(new File("../android"), 19, "libc.so", "libdl.so");
    }

    public void testExample() throws Exception {
        emulator.getMemory().setCallInitFunction();
        Module module = emulator.loadLibrary(new File("../example_binaries/libnative-lib.so"));

        emulator.traceCode();
        Number[] numbers = module.callFunction(emulator, 0x7e0 + 1);
        System.out.println("eFunc length is: " + numbers[0].intValue());

        emulator.traceCode();
        numbers = module.callFunction(emulator, 0x7e0 + 1);
        System.out.println("eFunc length is: " + numbers[0].intValue());

        emulator.traceCode();
        Unicorn unicorn = emulator.eBlock(module.base + 0x7e6 + 1, module.base + 0x7ea);
        System.out.println("String length is: " + ((Number) (unicorn.reg_read(ArmConst.UC_ARM_REG_R0))).intValue());
    }

    public void testCallTest() throws Exception {
        emulator.setProcessName(getClass().getSimpleName());
        emulator.getMemory().setCallInitFunction();
        Module module = emulator.loadLibrary(new File("../example_binaries/libnative-lib.so"));

        Number[] numbers = module.callFunction(emulator, "_Z4testv");
        System.out.println("String length is: " + numbers[0].intValue());
    }

}
