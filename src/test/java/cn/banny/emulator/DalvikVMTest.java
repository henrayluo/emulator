package cn.banny.emulator;

import cn.banny.emulator.debugger.Debugger;
import cn.banny.emulator.linux.Module;
import cn.banny.emulator.linux.ModuleListener;

import java.io.File;
import java.io.IOException;

public class DalvikVMTest implements ModuleListener {

    public static void main(String[] args) throws IOException {
        RunExecutable.run(new File("../example_binaries/dalvikvm"), new DalvikVMTest(), "-cp", "dex.jar", "DexTest");
    }

    @Override
    public void onLoaded(Emulator emulator, Module module) {
        /*if ("libdvm.so".equals(module.name)) {
            final Debugger debugger = emulator.attach();
            debugger.addBreakPoint(module, 0x0005070C);
        }*/
    }
}
