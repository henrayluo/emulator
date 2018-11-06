package cn.banny.emulator.linux;

import cn.banny.auxiliary.Inspector;
import cn.banny.emulator.ByteArrayNumber;
import cn.banny.emulator.Emulator;
import cn.banny.emulator.Memory;
import cn.banny.emulator.StringNumber;
import cn.banny.emulator.arm.ARM;
import cn.banny.emulator.pointer.UnicornPointer;
import com.sun.jna.Pointer;
import net.fornwall.jelf.ElfSymbol;
import net.fornwall.jelf.SymbolLocator;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import unicorn.Unicorn;

import java.io.IOException;
import java.util.*;

public class Module {

    private static final Log log = LogFactory.getLog(Module.class);

    public final long base;
    public final long size;
    private final String path;
    private final SymbolLocator dynsym;
    public final String name;
    private final List<ModuleSymbol> unresolvedSymbol;
    private final List<InitFunction> initFunctionList;
    private final Map<String, Module> neededLibraries;
    private final List<MemRegion> regions;

    public Module(long base, long size, String path, String name, SymbolLocator dynsym,
                  List<ModuleSymbol> unresolvedSymbol, List<InitFunction> initFunctionList, Map<String, Module> neededLibraries, List<MemRegion> regions) {
        this.base = base;
        this.size = size;
        this.path = path;
        this.name = name;

        this.dynsym = dynsym;
        this.unresolvedSymbol = unresolvedSymbol;
        this.initFunctionList = initFunctionList;

        this.neededLibraries = neededLibraries;
        this.regions = regions;
    }

    private long entryPoint;

    void setEntryPoint(long entryPoint) {
        this.entryPoint = entryPoint;
    }

    void callInitFunction(Emulator emulator, boolean mustCallInit) throws IOException {
        if (!mustCallInit && !unresolvedSymbol.isEmpty()) {
            for (ModuleSymbol moduleSymbol : unresolvedSymbol) {
                log.debug("[" + name + "]" + moduleSymbol.getSymbol().getName() + " symbol is missing before init relocationAddr=" + moduleSymbol.getRelocationAddr());
            }
            return;
        }

        for (Iterator<InitFunction> iterator = initFunctionList.iterator(); iterator.hasNext(); ) {
            InitFunction initFunction = iterator.next();
            initFunction.call(emulator);
            iterator.remove();
        }
    }

    List<ModuleSymbol> getUnresolvedSymbol() {
        return unresolvedSymbol;
    }

    public List<MemRegion> getRegions() {
        return regions;
    }

    public Symbol findSymbolByName(String name) throws IOException {
        return findSymbolByName(name, true);
    }

    private Symbol findSymbolByName(String name, boolean withDependencies) throws IOException {
        ElfSymbol elfSymbol = dynsym.getELFSymbolByName(name);
        if (elfSymbol != null && !elfSymbol.isUndef()) {
            return new Symbol(this, elfSymbol);
        }

        if (withDependencies) {
            for (Module module : neededLibraries.values()) {
                Symbol symbol = module.findSymbolByName(name, true);
                if (symbol != null) {
                    return symbol;
                }
            }
        }
        return null;
    }

    public ElfSymbol getELFSymbolByName(String name) throws IOException {
        return dynsym.getELFSymbolByName(name);
    }

    public int callEntry(Emulator emulator, Object... args) {
        if (entryPoint <= 0) {
            throw new IllegalStateException("entry point invalid");
        }

        final Unicorn unicorn = emulator.getUnicorn();
        Memory memory = emulator.getMemory();
        final int kernelArgumentBlockSize = 0x1000; // 4k
        long stack = memory.allocateStack(kernelArgumentBlockSize);
        unicorn.mem_write(stack - 0x1000, new byte[0x1000]);
        long sp = stack;

        int argc = 0;
        List<Pointer> argv = new ArrayList<>();

        sp -= ARM.writeCString(unicorn, sp, emulator.getProcessName());
        argv.add(UnicornPointer.pointer(unicorn, sp));
        argc++;

        for (int i = 0; args != null && i < args.length; i++) {
            String arg = String.valueOf(args[i]);
            sp -= ARM.writeCString(unicorn, sp, arg);
            argv.add(UnicornPointer.pointer(unicorn, sp));
            argc++;
        }

        sp -= 0x100;
        UnicornPointer kernelArgumentBlock = UnicornPointer.pointer(unicorn, sp);
        assert kernelArgumentBlock != null;
        kernelArgumentBlock.setInt(0, argc);

        Pointer argvPointer = kernelArgumentBlock.share(4);
        for (int i = 0; i < argv.size(); i++) {
            argvPointer.setPointer(4 * i, argv.get(i));
        }
        argvPointer.setInt(4 * argv.size(), 0);

        Pointer envPointer = argvPointer.share(4 * argv.size() + 4);
        envPointer.setInt(0, 0);

        Pointer auxvPointer = envPointer.share(4);
        auxvPointer.setInt(0, 0);

        if (log.isDebugEnabled()) {
            byte[] data = unicorn.mem_read(sp, stack - sp);
            Inspector.inspect(data, "kernelArgumentBlock=" + kernelArgumentBlock + ", argvPointer=" + argvPointer + ", envPointer=" + envPointer + ", auxvPointer=" + auxvPointer);
        }

        return emulator.eFunc(base + entryPoint, kernelArgumentBlock).intValue();
    }

    public Number[] callFunction(Emulator emulator, String symbolName, Object... args) throws IOException {
        Symbol symbol = findSymbolByName(symbolName, false);
        if (symbol == null) {
            throw new IllegalStateException("find symbol failed: " + symbolName);
        }
        if (symbol.elfSymbol.isUndef()) {
            throw new IllegalStateException(symbolName + " is NOT defined");
        }

        return symbol.call(emulator, args);
    }

    public Number[] callFunction(Emulator emulator, long offset, Object... args) {
        List<Number> list = new ArrayList<>(args.length);
        for (Object arg : args) {
            if (arg instanceof String) {
                list.add(new StringNumber((String) arg));
            } else if(arg instanceof byte[]) {
                list.add(new ByteArrayNumber((byte[]) arg));
            } else if(arg instanceof UnicornPointer) {
                UnicornPointer pointer = (UnicornPointer) arg;
                list.add(pointer.peer);
            } else if (arg instanceof Number) {
                list.add((Number) arg);
            } else {
                throw new IllegalStateException("Unsupported arg: " + arg);
            }
        }
        return emulator.eFunc(base + offset, list.toArray(new Number[0]));
    }

    Collection<Module> getNeededLibraries() {
        return neededLibraries.values();
    }

    public Module getDependencyModule(String name) {
        return neededLibraries.get(name);
    }

    @Override
    public String toString() {
        return "Module{" +
                "base=" + base +
                ", size=" + size +
                ", path='" + path + '\'' +
                ", name='" + name + '\'' +
                '}';
    }
}
