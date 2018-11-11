package cn.banny.emulator.dlfcn;

import cn.banny.emulator.Memory;
import cn.banny.emulator.SvcMemory;
import cn.banny.emulator.linux.InitFunction;
import cn.banny.emulator.linux.Module;
import cn.banny.emulator.linux.Symbol;
import cn.banny.emulator.pointer.UnicornPointer;
import com.sun.jna.Pointer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import unicorn.ArmConst;
import unicorn.Unicorn;

import java.io.IOException;

public class Implementation implements Dlfcn {

    private static final Log log = LogFactory.getLog(Implementation.class);

    private final UnicornPointer error;
    private final UnicornPointer dlerror;
    private final UnicornPointer dlclose;
    private final UnicornPointer dlopen;
    private final UnicornPointer dladdr;
    private final UnicornPointer dlsym;

    private Unicorn unicorn;

    public Implementation(Unicorn unicorn, SvcMemory svcMemory) {
        this.unicorn = unicorn;

        error = svcMemory.allocate(0x40);
        assert error != null;
        error.setMemory(0, 0x40, (byte) 0);

        byte[] dlerror = new byte[] {
                (byte) 0x07, (byte) 0xc0, (byte) 0xa0, (byte) 0xe1, // mov r12, r7
                (byte) 0xf1, (byte) 0x7a, (byte) 0xa0, (byte) 0xe3, // mov r7, #0xf1000
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xef, // svc 0
                (byte) 0x0c, (byte) 0x70, (byte) 0xa0, (byte) 0xe1, // mov r7, r12
                (byte) 0x1e, (byte) 0xff, (byte) 0x2f, (byte) 0xe1, // bx lr
        };
        this.dlerror = svcMemory.allocate(dlerror.length);
        this.dlerror.write(0, dlerror, 0, dlerror.length);

        byte[] dlclose = new byte[] {
                (byte) 0x07, (byte) 0xc0, (byte) 0xa0, (byte) 0xe1, // mov r12, r7
                (byte) 0xf2, (byte) 0x7a, (byte) 0xa0, (byte) 0xe3, // mov r7, #0xf2000
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xef, // svc 0
                (byte) 0x0c, (byte) 0x70, (byte) 0xa0, (byte) 0xe1, // mov r7, r12
                (byte) 0x1e, (byte) 0xff, (byte) 0x2f, (byte) 0xe1, // bx lr
        };
        this.dlclose = svcMemory.allocate(dlclose.length);
        this.dlclose.write(0, dlclose, 0, dlclose.length);

        byte[] dlopen = new byte[] {
                (byte) 0xf0, (byte) 0x40, (byte) 0x2d, (byte) 0xe9, // push {r4, r5, r6, r7, lr}
                (byte) 0xf3, (byte) 0x7a, (byte) 0xa0, (byte) 0xe3, // mov r7, #0xf3000
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xef, // svc 0
                (byte) 0x04, (byte) 0x70, (byte) 0x9d, (byte) 0xe4, // pop {r0} ; manipulated stack in dlopen
                (byte) 0x00, (byte) 0x00, (byte) 0x57, (byte) 0xe3, // cmp r0, #0
                (byte) 0x10, (byte) 0xe0, (byte) 0x4f, (byte) 0x12, // subne lr, pc, #16
                (byte) 0x17, (byte) 0xff, (byte) 0x2f, (byte) 0x11, // bxne r0 ; call init array
                (byte) 0xf1, (byte) 0x80, (byte) 0xbd, (byte) 0xe8, // pop {r0, r4, r5, r6, r7, pc} ; with return address
        };
        this.dlopen = svcMemory.allocate(dlopen.length);
        this.dlopen.write(0, dlopen, 0, dlopen.length);

        byte[] dladdr = new byte[] {
                (byte) 0x07, (byte) 0xc0, (byte) 0xa0, (byte) 0xe1, // mov r12, r7
                (byte) 0x3d, (byte) 0x79, (byte) 0xa0, (byte) 0xe3, // mov r7, #0xf4000
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xef, // svc 0
                (byte) 0x0c, (byte) 0x70, (byte) 0xa0, (byte) 0xe1, // mov r7, r12
                (byte) 0x1e, (byte) 0xff, (byte) 0x2f, (byte) 0xe1, // bx lr
        };
        this.dladdr = svcMemory.allocate(dladdr.length);
        this.dladdr.write(0, dladdr, 0, dladdr.length);

        byte[] dlsym = new byte[] {
                (byte) 0x07, (byte) 0xc0, (byte) 0xa0, (byte) 0xe1, // mov r12, r7
                (byte) 0xf5, (byte) 0x7a, (byte) 0xa0, (byte) 0xe3, // mov r7, #0xf5000
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xef, // svc 0
                (byte) 0x0c, (byte) 0x70, (byte) 0xa0, (byte) 0xe1, // mov r7, r12
                (byte) 0x1e, (byte) 0xff, (byte) 0x2f, (byte) 0xe1, // bx lr
        };
        this.dlsym = svcMemory.allocate(dlsym.length);
        this.dlsym.write(0, dlsym, 0, dlsym.length);

        log.debug("dlopen=" + this.dlopen + ", dlsym=" + this.dlsym);
    }

    @Override
    public long hook(String soName, String symbol) {
        if ("libdl.so".equals(soName)) {
            switch (symbol) {
                case "dlerror":
                    return dlerror.peer;
                case "dlclose":
                    return dlclose.peer;
                case "dlopen":
                    return dlopen.peer;
                case "dladdr":
                    return dladdr.peer;
                case "dlsym":
                    return dlsym.peer;
            }
        }
        return 0;
    }

    @Override
    public int dlopen(Memory memory, String filename, int flags) {
        Pointer pointer = UnicornPointer.register(unicorn, ArmConst.UC_ARM_REG_SP);
        try {
            Module module = memory.dlopen(filename, false);
            if (module == null) {
                pointer = pointer.share(-4); // return value
                pointer.setInt(0, 0);

                pointer = pointer.share(-4); // NULL-terminated
                pointer.setInt(0, 0);

                this.error.setString(0, "Resolve library " + filename + " failed");
                return 0;
            } else {
                pointer = pointer.share(-4); // return value
                pointer.setInt(0, (int) module.base);

                pointer = pointer.share(-4); // NULL-terminated
                pointer.setInt(0, 0);

                for (Module m : memory.getLoadedModules()) {
                    if (!m.getUnresolvedSymbol().isEmpty()) {
                        continue;
                    }
                    for (InitFunction initFunction : m.initFunctionList) {
                        if (initFunction.addresses != null) {
                            for (long addr : initFunction.addresses) {
                                if (addr != 0 && addr != -1) {
                                    log.debug("[" + m.name + "]PushInitFunction: 0x" + Long.toHexString(addr));
                                    pointer = pointer.share(-4); // init array
                                    pointer.setInt(0, (int) (m.base + addr));
                                }
                            }
                        }
                    }
                    m.initFunctionList.clear();
                }

                return (int) module.base;
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } finally {
            unicorn.reg_write(ArmConst.UC_ARM_REG_SP, ((UnicornPointer) pointer).peer);
        }
    }

    @Override
    public int dlsym(Memory memory, long handle, String symbol) {
        try {
            Symbol elfSymbol = memory.dlsym(handle, symbol);
            if (elfSymbol == null) {
                this.error.setString(0, "Find symbol " + symbol + " failed");
                return 0;
            }
            return (int) elfSymbol.getAddress();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public int dlclose(Memory memory, long handle) {
        if (memory.dlclose(handle)) {
            return 0;
        } else {
            this.error.setString(0, "dlclose 0x" + Long.toHexString(handle) + " failed");
            return -1;
        }
    }

    @Override
    public int dlerror() {
        return (int) error.peer;
    }

}
