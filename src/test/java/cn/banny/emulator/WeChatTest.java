package cn.banny.emulator;

import cn.banny.emulator.arm.AndroidARMEmulator;
import cn.banny.emulator.debugger.Debugger;
import cn.banny.emulator.linux.AndroidResolver;
import cn.banny.emulator.linux.Module;
import cn.banny.emulator.linux.ModuleListener;

import java.io.File;
import java.io.IOException;

public class WeChatTest implements ModuleListener {

    private static LibraryResolver createLibraryResolver() {
        return new AndroidResolver(new File("android"), 19, "libc.so", "libdl.so", "liblog.so", "libm.so", "libz.so", "libstdc++.so", "libdvm.so", "libjavacore.so", "libnativehelper.so");
    }

    private static Emulator createARMEmulator() {
        return new AndroidARMEmulator("com.tencent.mm");
    }

    @Override
    public void onLoaded(Emulator emulator, Module module) {
    }

    public static void main(String[] args) throws IOException {
        Emulator emulator = createARMEmulator();
        final Memory memory = emulator.getMemory();
        memory.setLibraryResolver(createLibraryResolver());
        memory.setCallInitFunction();
        memory.setModuleListener(new WeChatTest());
        final File elfFile = new File("src/test/resources/example_binaries/libwechatnormsg.so");
        emulator.setWorkDir(elfFile.getParentFile());
        Module module = emulator.loadLibrary(elfFile, true);

        Debugger debugger = emulator.attach();
        debugger.addBreakPoint(module, 0x00040EEC);
        // debugger.addBreakPoint(null, 0xffff0fdc);
        // emulator.traceCode(module.base, module.base + module.size);
        Number ret = module.callFunction(emulator, 0x40da8 + 1, null, null, 0, 0, 0)[0];
        System.out.println("ret=0x" + (ret.intValue() & 0xffffffffL));
    }

}
