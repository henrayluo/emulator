package cn.banny.emulator.dlfcn;

import cn.banny.emulator.Memory;
import cn.banny.emulator.linux.InitFunction;
import cn.banny.emulator.linux.Module;
import cn.banny.emulator.linux.Symbol;
import cn.banny.emulator.pointer.UnicornPointer;
import com.sun.jna.Pointer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import unicorn.ArmConst;
import unicorn.Unicorn;
import unicorn.UnicornConst;

import java.io.IOException;
import java.nio.ByteBuffer;

public class Implementation implements Dlfcn {

    private static final Log log = LogFactory.getLog(Implementation.class);

    private final UnicornPointer error;
    private final long dlerror;
    private final long dlclose;
    private final long dlopen;
    private final long dladdr;
    private final long dlsym;

    private Unicorn unicorn;

    public Implementation(Unicorn unicorn) {
        this.unicorn = unicorn;

        long base = 0xfffe0000L;
        unicorn.mem_map(base, 0x10000, UnicornConst.UC_PROT_READ | UnicornConst.UC_PROT_EXEC);
        byte[] b0 = new byte[] { 0x00, (byte) 0xf0, (byte) 0xa0, (byte) 0xe3 }; // mov pc, #0
        ByteBuffer buffer = ByteBuffer.allocate(0x10000);
        for (int i = 0; i < 0x10000; i += 4) {
            buffer.put(b0);
        }
        unicorn.mem_write(base, buffer.array());

        error = UnicornPointer.pointer(unicorn, base);
        assert error != null;
        error.setMemory(0, 0x100, (byte) 0);
        base += 0x100;

        byte[] dlerror = new byte[] {
                (byte) 0x07, (byte) 0xc0, (byte) 0xa0, (byte) 0xe1, // mov r12, r7
                (byte) 0xf1, (byte) 0x7a, (byte) 0xa0, (byte) 0xe3, // mov r7, #0xf1000
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xef, // svc 0
                (byte) 0x0c, (byte) 0x70, (byte) 0xa0, (byte) 0xe1, // mov r7, r12
                (byte) 0x1e, (byte) 0xff, (byte) 0x2f, (byte) 0xe1, // bx lr
        };
        this.dlerror = base;
        unicorn.mem_write(this.dlerror, dlerror);
        base += dlerror.length;

        byte[] dlclose = new byte[] {
                (byte) 0x07, (byte) 0xc0, (byte) 0xa0, (byte) 0xe1, // mov r12, r7
                (byte) 0xf2, (byte) 0x7a, (byte) 0xa0, (byte) 0xe3, // mov r7, #0xf2000
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xef, // svc 0
                (byte) 0x0c, (byte) 0x70, (byte) 0xa0, (byte) 0xe1, // mov r7, r12
                (byte) 0x1e, (byte) 0xff, (byte) 0x2f, (byte) 0xe1, // bx lr
        };
        this.dlclose = base;
        unicorn.mem_write(this.dlclose, dlclose);
        base += dlclose.length;

        byte[] dlopen = new byte[] {
                (byte) 0x04, (byte) 0xe0, (byte) 0x2d, (byte) 0xe5, // push {lr}
                (byte) 0x04, (byte) 0x70, (byte) 0x2d, (byte) 0xe5, // push {r7}
                (byte) 0xf3, (byte) 0x7a, (byte) 0xa0, (byte) 0xe3, // mov r7, #0xf3000
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xef, // svc 0
                (byte) 0x04, (byte) 0x70, (byte) 0x9d, (byte) 0xe4, // pop {r7} ; manipulate stack in dlopen
                (byte) 0x00, (byte) 0x00, (byte) 0x57, (byte) 0xe3, // cmp r7, #0
                (byte) 0x37, (byte) 0xff, (byte) 0x2f, (byte) 0x11, // blxne r7 ; call init array
                (byte) 0x00, (byte) 0x00, (byte) 0x57, (byte) 0xe3, // cmp r7, #0
                (byte) 0xfa, (byte) 0xff, (byte) 0xff, (byte) 0x1a, // bne #-24
                (byte) 0x04, (byte) 0x00, (byte) 0x9d, (byte) 0xe4, // pop {r0} ; return address
                (byte) 0x04, (byte) 0x70, (byte) 0x9d, (byte) 0xe4, // pop {r7}
                (byte) 0x04, (byte) 0xe0, (byte) 0x9d, (byte) 0xe4, // pop {lr}
                (byte) 0x1e, (byte) 0xff, (byte) 0x2f, (byte) 0xe1, // bx lr
        };
        this.dlopen = base;
        unicorn.mem_write(this.dlopen, dlopen);
        base += dlopen.length;

        byte[] dladdr = new byte[] {
                (byte) 0x07, (byte) 0xc0, (byte) 0xa0, (byte) 0xe1, // mov r12, r7
                (byte) 0x3d, (byte) 0x79, (byte) 0xa0, (byte) 0xe3, // mov r7, #0xf4000
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xef, // svc 0
                (byte) 0x0c, (byte) 0x70, (byte) 0xa0, (byte) 0xe1, // mov r7, r12
                (byte) 0x1e, (byte) 0xff, (byte) 0x2f, (byte) 0xe1, // bx lr
        };
        this.dladdr = base;
        unicorn.mem_write(this.dladdr, dladdr);
        base += dladdr.length;

        byte[] dlsym = new byte[] {
                (byte) 0x07, (byte) 0xc0, (byte) 0xa0, (byte) 0xe1, // mov r12, r7
                (byte) 0xf5, (byte) 0x7a, (byte) 0xa0, (byte) 0xe3, // mov r7, #0xf5000
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xef, // svc 0
                (byte) 0x0c, (byte) 0x70, (byte) 0xa0, (byte) 0xe1, // mov r7, r12
                (byte) 0x1e, (byte) 0xff, (byte) 0x2f, (byte) 0xe1, // bx lr
        };
        this.dlsym = base;
        unicorn.mem_write(this.dlsym, dlsym);

        log.debug("dlopen=0x" + Long.toHexString(this.dlopen) + ", dlsym=0x" + Long.toHexString(this.dlsym));
    }

    @Override
    public long hook(String soName, String symbol) {
        if ("libdl.so".equals(soName)) {
            switch (symbol) {
                case "dlerror":
                    return dlerror;
                case "dlclose":
                    return dlclose;
                case "dlopen":
                    return dlopen;
                case "dladdr":
                    return dladdr;
                case "dlsym":
                    return dlsym;
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
                                    log.debug("[" + m.name + "]CallInitFunction: 0x" + Long.toHexString(addr));
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
