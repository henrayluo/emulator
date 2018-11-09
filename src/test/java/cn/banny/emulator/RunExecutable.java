package cn.banny.emulator;

import cn.banny.emulator.arm.AndroidARMEmulator;
import cn.banny.emulator.linux.AndroidResolver;
import cn.banny.emulator.linux.Module;
import cn.banny.emulator.linux.ModuleListener;
import cn.banny.emulator.pointer.UnicornPointer;
import com.sun.jna.Pointer;
import net.fornwall.jelf.ElfSymbol;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;

class RunExecutable {

    static void run(File executable, ModuleListener listener, String[] preloads, Object...args) throws IOException {
        final Emulator emulator = new AndroidARMEmulator(executable.getName());
        emulator.setWorkDir(executable.getParentFile());
        try {
            long start = System.currentTimeMillis();
            Memory memory = emulator.getMemory();
            memory.setLibraryResolver(new AndroidResolver(new File("android"), 19));

            memory.setCallInitFunction();
            if (listener != null) {
                memory.setModuleListener(listener);
            }
            if (preloads != null) {
                for (String preload : preloads) {
                    if (preload != null) {
                        Module preloaded = memory.dlopen(preload);
                        System.out.println("preloaded=" + preloaded);
                    }
                }
            }

            Module module = emulator.loadLibrary(executable);
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
            emulator.getMemory().setErrno(Emulator.EACCES);
            int value = pointer.getInt(0);
            assert value == Emulator.EACCES;

            // emulator.traceCode();
            Pointer strerror = UnicornPointer.pointer(emulator.getUnicorn(), libc.callFunction(emulator, "strerror", Emulator.ECONNREFUSED)[0].intValue() & 0xffffffffL);
            assert strerror != null;
            System.out.println(strerror.getString(0));

            // emulator.traceCode();
            System.out.println("exit code: " + module.callEntry(emulator, args) + ", offset=" + (System.currentTimeMillis() - start) + "ms");
        } finally {
            IOUtils.closeQuietly(emulator);
        }
    }

    static void run(File executable, ModuleListener listener, Object...args) throws IOException {
        run(executable, listener, null, args);
    }

}
