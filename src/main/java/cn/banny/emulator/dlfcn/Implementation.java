package cn.banny.emulator.dlfcn;

import cn.banny.emulator.Memory;
import cn.banny.emulator.linux.Module;
import cn.banny.emulator.pointer.UnicornPointer;
import net.fornwall.jelf.ElfSymbol;
import unicorn.Unicorn;
import unicorn.UnicornConst;

import java.io.IOException;
import java.nio.ByteBuffer;

public class Implementation implements Dlfcn {

    private final UnicornPointer error;
    private final long dlerror;
    private final long dlclose;
    private final long dlopen;
    private final long dladdr;
    private final long dlsym;

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
        try {
            Module module = memory.loadLibrary(filename);
            if (module == null) {
                this.error.setString(0, "Find " + filename + " failed");
                return 0;
            }
            return (int) module.base;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public int dlsym(Memory memory, long handle, String symbol) {
        try {
            Module module = memory.findModuleByHandle(handle);
            ElfSymbol elfSymbol = module == null ? null : module.getELFSymbolByName(symbol);
            if (elfSymbol == null) {
                this.error.setString(0, "Find symbol " + symbol + " failed");
                return 0;
            }
            return (int) (module.base + elfSymbol.value);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public int dlclose(Memory memory, long handle) {
        if (memory.unloadLibrary(handle)) {
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

    public Implementation(Unicorn unicorn) {
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
                (byte) 0x07, (byte) 0xc0, (byte) 0xa0, (byte) 0xe1, // mov r12, r7
                (byte) 0xf3, (byte) 0x7a, (byte) 0xa0, (byte) 0xe3, // mov r7, #0xf3000
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xef, // svc 0
                (byte) 0x0c, (byte) 0x70, (byte) 0xa0, (byte) 0xe1, // mov r7, r12
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
    }

}
