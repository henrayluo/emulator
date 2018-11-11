package cn.banny.emulator.dlfcn;

import cn.banny.emulator.Emulator;
import cn.banny.emulator.Memory;
import cn.banny.emulator.SvcMemory;
import cn.banny.emulator.linux.InitFunction;
import cn.banny.emulator.linux.Module;
import cn.banny.emulator.linux.Symbol;
import cn.banny.emulator.pointer.UnicornPointer;
import cn.banny.emulator.svc.ArmSvc;
import com.sun.jna.Pointer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import unicorn.ArmConst;
import unicorn.Unicorn;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

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

        this.dlerror = svcMemory.registerSvc(new ArmSvc() {
            @Override
            public int handle(Unicorn u, Emulator emulator) {
                return (int) error.peer;
            }
        });

        this.dlclose = svcMemory.registerSvc(new ArmSvc() {
            @Override
            public int handle(Unicorn u, Emulator emulator) {
                long handle = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R0)).intValue() & 0xffffffffL;
                if (log.isDebugEnabled()) {
                    log.debug("dlclose handle=0x" + Long.toHexString(handle));
                }
                return dlclose(emulator.getMemory(), handle);
            }
        });

        this.dlopen = svcMemory.registerSvc(new ArmSvc() {
            @Override
            public UnicornPointer onRegister(SvcMemory svcMemory, int svcNumber) {
                ByteBuffer buffer = ByteBuffer.allocate(28).order(ByteOrder.LITTLE_ENDIAN);
                buffer.put(new byte[] { (byte) 0xf0, (byte) 0x40, (byte) 0x2d, (byte) 0xe9 }); // push {r4, r5, r6, r7, lr}
                buffer.putInt(svcNumber | 0xef000000); // svc
                buffer.put(new byte[] { (byte) 0x04, (byte) 0x70, (byte) 0x9d, (byte) 0xe4 }); // pop {r0} ; manipulated stack in dlopen
                buffer.put(new byte[] { (byte) 0x00, (byte) 0x00, (byte) 0x57, (byte) 0xe3 }); // cmp r0, #0
                buffer.put(new byte[] { (byte) 0x10, (byte) 0xe0, (byte) 0x4f, (byte) 0x12 }); // subne lr, pc, #16
                buffer.put(new byte[] { (byte) 0x17, (byte) 0xff, (byte) 0x2f, (byte) 0x11 }); // bxne r0 ; call init array
                buffer.put(new byte[] { (byte) 0xf1, (byte) 0x80, (byte) 0xbd, (byte) 0xe8 }); // pop {r0, r4, r5, r6, r7, pc} ; with return address
                UnicornPointer pointer = svcMemory.allocate(28);
                pointer.write(0, buffer.array(), 0, 28);
                return pointer;
            }
            @Override
            public int handle(Unicorn u, Emulator emulator) {
                Pointer filename = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R0);
                int flags = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R1)).intValue();
                if (log.isDebugEnabled()) {
                    log.debug("dlopen filename=" + filename.getString(0) + ", flags=" + flags);
                }
                return dlopen(emulator.getMemory(), filename.getString(0));
            }
        });

        this.dladdr = svcMemory.registerSvc(new ArmSvc() {
            @Override
            public int handle(Unicorn u, Emulator emulator) {
                long addr = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R0)).intValue() & 0xffffffffL;
                Pointer info = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R1);
                if (log.isDebugEnabled()) {
                    log.debug("dladdr addr=0x" + Long.toHexString(addr) + ", info=" + info);
                }
                throw new UnsupportedOperationException();
            }
        });

        this.dlsym = svcMemory.registerSvc(new ArmSvc() {
            @Override
            public int handle(Unicorn u, Emulator emulator) {
                long handle = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R0)).intValue() & 0xffffffffL;
                Pointer symbol = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R1);
                if (log.isDebugEnabled()) {
                    log.debug("dlsym handle=0x" + Long.toHexString(handle) + ", symbol=" + symbol.getString(0));
                }
                return dlsym(emulator.getMemory(), handle, symbol.getString(0));
            }
        });

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

    private int dlopen(Memory memory, String filename) {
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

    private int dlsym(Memory memory, long handle, String symbol) {
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

    private int dlclose(Memory memory, long handle) {
        if (memory.dlclose(handle)) {
            return 0;
        } else {
            this.error.setString(0, "dlclose 0x" + Long.toHexString(handle) + " failed");
            return -1;
        }
    }

}
