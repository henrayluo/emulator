package cn.banny.emulator;

import cn.banny.emulator.linux.AndroidResolver;
import cn.banny.emulator.linux.Module;

import java.io.File;

public class BusyBoxTest extends EmulatorTest {

    @Override
    protected LibraryResolver createLibraryResolver() {
        return new AndroidResolver(new File("../android"), 19);
    }

    public void testExecutable() throws Exception {
        emulator.setProcessName("busybox");
        emulator.getMemory().setCallInitFunction();
        Module module = emulator.loadLibrary(new File("../example_binaries/busybox"));
        // emulator.traceCode();
        System.out.println("exit code: " + module.callEntry(emulator, "wget", "http://pv.sohu.com/cityjson?ie=utf-8", "-O", "-"));
    }

}
