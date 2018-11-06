package cn.banny.emulator;

import cn.banny.emulator.linux.AndroidResolver;
import cn.banny.emulator.linux.Module;

import java.io.File;

public class WeChatTest extends EmulatorTest {

    @Override
    protected LibraryResolver createLibraryResolver() {
        return new AndroidResolver(new File("../android"), 19, "libc.so", "libdl.so", "liblog.so", "libm.so", "libz.so", "libstdc++.so");
    }

    public void testNorMsg() throws Exception {
        emulator.setProcessName("com.tencent.mm");
        emulator.getMemory().setCallInitFunction();
        final File elfFile = new File("../example_binaries/libwechatnormsg.so");
        Module module = emulator.loadLibrary(elfFile, true);
    }

}
