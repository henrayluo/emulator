package cn.banny.emulator;

import cn.banny.emulator.linux.AndroidResolver;
import cn.banny.emulator.linux.Module;
import cn.banny.emulator.linux.ModuleListener;
import cn.banny.emulator.pointer.UnicornPointer;
import com.sun.jna.Pointer;
import net.fornwall.jelf.ElfSymbol;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;

public class SSHDebug {

    public static void main(String[] args) throws IOException {
        final Emulator emulator = EmulatorFactory.createARMEmulator();
        try {
            Memory memory = emulator.getMemory();
            memory.setLibraryResolver(new AndroidResolver(new File("../android"), 19));

            emulator.setProcessName("ssh");
            memory.setCallInitFunction();
            memory.setModuleListener(new ModuleListener() {
                @Override
                public void onLoaded(Module module) {
                    /*if ("libc.so".equals(module.name)) {
                        final Debugger debugger = emulator.attach();
                        debugger.addBreakPoint(module, 0x0000D100);
                    }*/
                }
            });
            Module module = emulator.loadLibrary(new File("../example_binaries/ssh"));
            Module libc = module.getDependencyModule("libc");
            ElfSymbol environ = libc.getELFSymbolByName("environ");
            if (environ != null) {
                Pointer pointer = UnicornPointer.pointer(emulator.getUnicorn(), libc.base + environ.value);
                assert pointer != null;
                System.err.println("environ=" + pointer + ", value=" + pointer.getPointer(0));
            }
            Number __errno = libc.callFunction(emulator, "__errno")[0];
            Pointer pointer = UnicornPointer.pointer(emulator.getUnicorn(), __errno.intValue() & 0xffffffffL);
            assert pointer != null;
            emulator.setErrno(Emulator.EACCES);
            int value = pointer.getInt(0);
            assert value == Emulator.EACCES;

            // emulator.traceCode();
            Pointer strerror = UnicornPointer.pointer(emulator.getUnicorn(), libc.callFunction(emulator, "strerror", 0x29)[0].intValue() & 0xffffffffL);
            assert strerror != null;
            System.out.println(strerror.getString(0));

            // emulator.traceCode();
            System.out.println("exit code: " + module.callEntry(emulator, "-p", "4446", "root@p.gzmtx.cn"));
        } finally {
            IOUtils.closeQuietly(emulator);
        }
    }

}
