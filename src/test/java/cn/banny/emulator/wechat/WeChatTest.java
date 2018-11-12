package cn.banny.emulator.wechat;

import cn.banny.emulator.Emulator;
import cn.banny.emulator.LibraryResolver;
import cn.banny.emulator.Memory;
import cn.banny.emulator.SvcMemory;
import cn.banny.emulator.arm.ARMEmulator;
import cn.banny.emulator.arm.AndroidARMEmulator;
import cn.banny.emulator.debugger.Debugger;
import cn.banny.emulator.dvm.DalvikVM;
import cn.banny.emulator.dvm.DvmClass;
import cn.banny.emulator.dvm.VM;
import cn.banny.emulator.linux.AndroidResolver;
import cn.banny.emulator.linux.Module;
import cn.banny.emulator.linux.ModuleListener;

import java.io.File;
import java.io.IOException;

public class WeChatTest implements ModuleListener {

    private static LibraryResolver createLibraryResolver() {
        return new AndroidResolver(new File("android"), 19, "libc.so", "libdl.so", "liblog.so", "libm.so", "libz.so", "libstdc++.so", "libdvm.so", "libjavacore.so", "libnativehelper.so");
    }

    private static ARMEmulator createARMEmulator() {
        return new AndroidARMEmulator("com.tencent.mm");
    }

    @Override
    public void onLoaded(Emulator emulator, Module module) {
    }

    public static void main(String[] args) throws IOException {
        ARMEmulator emulator = createARMEmulator();
        final Memory memory = emulator.getMemory();
        memory.setLibraryResolver(createLibraryResolver());
        memory.setCallInitFunction();
        memory.setModuleListener(new WeChatTest());
        final File elfFile = new File("src/test/resources/example_binaries/libwechatnormsg.so");
        emulator.setWorkDir(elfFile.getParentFile());
        Module module = emulator.loadLibrary(elfFile, true);

        Debugger debugger = emulator.attach();
        // debugger.addBreakPoint(module, 0x00040EEC);
        // debugger.addBreakPoint(null, 0xffff0fdc);
        // debugger.addBreakPoint(module, 0x00f799);

        SvcMemory svcMemory = emulator.getSvcMemory();
        VM vm = new DalvikVM(svcMemory);

        // emulator.traceCode(module.base, module.base + module.size);
        module.callFunction(emulator, "JNI_OnLoad", vm.getJavaVM(), null);
        DvmClass Normsg$J2CBridge = vm.findClass("com/tencent/mm/plugin/normsg/Normsg$J2CBridge");
        Number ret = module.callFunction(emulator, 0x40da8 + 1, vm.getJNIEnv(), Normsg$J2CBridge.hashCode(), 0, 0, 0)[0];
        System.out.println("ret=0x" + (ret.intValue() & 0xffffffffL));
    }

}
