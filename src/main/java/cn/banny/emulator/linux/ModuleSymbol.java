package cn.banny.emulator.linux;

import com.sun.jna.Pointer;
import net.fornwall.jelf.ElfSymbol;

import java.io.IOException;
import java.util.Collection;

public class ModuleSymbol {

    private static final long WEAK_BASE = -1;

    final String soName;
    private final long load_base;
    final ElfSymbol symbol;
    final Pointer relocationAddr;
    final String toSoName;
    private final long offset;

    ModuleSymbol(String soName, long load_base, ElfSymbol symbol, Pointer relocationAddr, String toSoName, long offset) {
        this.soName = soName;
        this.load_base = load_base;
        this.symbol = symbol;
        this.relocationAddr = relocationAddr;
        this.toSoName = toSoName;
        this.offset = offset;
    }

    ModuleSymbol resolve(Collection<Module> modules, boolean resolveWeak) throws IOException {
        final String sym_name = symbol.getName();
        for (Module module : modules) {
            ElfSymbol elfSymbol = module.getELFSymbolByName(sym_name);
            if (elfSymbol != null && !elfSymbol.isUndef()) {
                switch (elfSymbol.getBinding()) {
                    case ElfSymbol.BINDING_GLOBAL:
                    case ElfSymbol.BINDING_WEAK:
                        return new ModuleSymbol(soName, module.base, elfSymbol, relocationAddr, module.name, offset);
                }
            }
        }

        if (resolveWeak && symbol.getBinding() == ElfSymbol.BINDING_WEAK) {
            return new ModuleSymbol(soName, WEAK_BASE, symbol, relocationAddr, "0", 0);
        }

        return null;
    }

    void relocation() {
        long value = load_base == WEAK_BASE ? 0 : load_base + symbol.value + offset;
        relocationAddr.setInt(0, (int) value);
    }

    public ElfSymbol getSymbol() {
        return symbol;
    }

    Pointer getRelocationAddr() {
        return relocationAddr;
    }
}
