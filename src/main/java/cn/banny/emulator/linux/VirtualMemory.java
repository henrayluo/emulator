package cn.banny.emulator.linux;

import cn.banny.auxiliary.Inspector;
import cn.banny.emulator.*;
import cn.banny.emulator.arm.ARMEmulator;
import cn.banny.emulator.linux.file.*;
import cn.banny.emulator.pointer.UnicornPointer;
import com.sun.jna.Pointer;
import net.fornwall.jelf.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import unicorn.Unicorn;
import unicorn.UnicornConst;
import unicorn.WriteHook;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

public class VirtualMemory implements Memory {

    private static final Log log = LogFactory.getLog(VirtualMemory.class);

    private final Unicorn unicorn;
    private final Emulator emulator;
    private final SyscallHandler syscallHandler;
    private LibraryResolver libraryResolver;

    private long sp;

    private long mmapBaseAddress;

    public VirtualMemory(Unicorn unicorn, Emulator emulator, SyscallHandler syscallHandler) {
        this.unicorn = unicorn;
        this.emulator = emulator;
        this.syscallHandler = syscallHandler;

        // init stack
        final long stackSize = STACK_SIZE_OF_PAGE * emulator.getPageAlign();
        unicorn.mem_map(STACK_BASE - stackSize, stackSize, UnicornConst.UC_PROT_READ | UnicornConst.UC_PROT_WRITE);
        sp = STACK_BASE;

        mmapBaseAddress = MMAP_BASE;
    }

    @Override
    public void setLibraryResolver(LibraryResolver libraryResolver) {
        this.libraryResolver = libraryResolver;

        open(STDIN, FileIO.O_RDONLY);
        open(STDOUT, FileIO.O_WRONLY);
        open(STDERR, FileIO.O_WRONLY);
    }

    private final Map<String, Module> modules = new LinkedHashMap<>();

    @Override
    public Module load(File elfFile) throws IOException {
        return load(elfFile,false);
    }

    @Override
    public Module load(File elfFile, boolean forceCallInit) throws IOException {
        return loadInternal(elfFile, null, forceCallInit);
    }

    @Override
    public byte[] unpack(File elfFile) throws IOException {
        final byte[] fileData = FileUtils.readFileToByteArray(elfFile);
        loadInternal(elfFile, new WriteHook() {
            @Override
            public void hook(Unicorn u, long address, int size, long value, Object user) {
                byte[] data = Arrays.copyOf(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(value).array(), size);
                if (log.isDebugEnabled()) {
                    Inspector.inspect(data, "### Unpack WRITE at 0x" + Long.toHexString(address));
                }
                System.arraycopy(data, 0, fileData, (int) address, data.length);
            }
        }, true);
        return fileData;
    }

    private Module loadInternal(File elfFile, WriteHook unpackHook, boolean forceCallInit) throws IOException {
        Module module = loadInternal(elfFile.getParentFile(), elfFile, unpackHook);
        for (Module m : modules.values()) {
            for (Iterator<ModuleSymbol> iterator = m.getUnresolvedSymbol().iterator(); iterator.hasNext(); ) {
                ModuleSymbol moduleSymbol = iterator.next();
                ModuleSymbol resolved = moduleSymbol.resolve(modules.values(), true);
                if (resolved != null) {
                    log.debug("[" + moduleSymbol.soName + "]" + moduleSymbol.symbol.getName() + " symbol resolved to " + resolved.toSoName);
                    resolved.relocation();
                    iterator.remove();
                } else {
                    log.info("[" + moduleSymbol.soName + "]symbol " + moduleSymbol.symbol.getName() + " is missing relocationAddr=" + moduleSymbol.relocationAddr);
                }
            }
        }
        if (callInitFunction || forceCallInit) {
            for (Module m : modules.values()) {
                boolean forceCall = forceCallInit && m == module;
                if (callInitFunction) {
                    m.callInitFunction(emulator, forceCall);
                } else if(forceCall) {
                    m.callInitFunction(emulator, true);
                }
            }
        }
        return module;
    }

    private ModuleListener moduleListener;

    @Override
    public void setModuleListener(ModuleListener listener) {
        moduleListener = listener;
    }

    private Module loadInternal(File workDir, File file, final WriteHook unpackHook) throws IOException {
        ElfFile elfFile = ElfFile.fromFile(file);
        if (elfFile.objectSize != ElfFile.CLASS_32) {
            throw new ElfException("Must be 32-bit");
        }

        if (elfFile.encoding != ElfFile.DATA_LSB) {
            throw new ElfException("Must be LSB");
        }

        if (elfFile.arch != ElfFile.ARCH_ARM) {
            throw new ElfException("Must be ARM arch.");
        }

        long start = System.currentTimeMillis();
        long bound_low = 0;
        long bound_high = 0;
        for (int i = 0; i < elfFile.num_ph; i++) {
            ElfSegment ph = elfFile.getProgramHeader(i);
            if (ph.type == ElfSegment.PT_LOAD && ph.mem_size > 0) {
                if (bound_low > ph.virtual_address) {
                    bound_low = ph.virtual_address;
                }

                long high = ph.virtual_address + ph.mem_size;

                if (bound_high < high) {
                    bound_high = high;
                }
            }
        }

        ElfDynamicStructure dynamicStructure = null;

        final long baseAlign = emulator.getPageAlign();
        final long load_base = ((mmapBaseAddress - 1) / baseAlign + 1) * baseAlign;
        long size = emulator.align(0, bound_high - bound_low).size;
        mmapBaseAddress = load_base + size;

        final List<MemRegion> regions = new ArrayList<>(5);
        for (int i = 0; i < elfFile.num_ph; i++) {
            ElfSegment ph = elfFile.getProgramHeader(i);
            switch (ph.type) {
                case ElfSegment.PT_LOAD:
                    int prot = get_segment_protection(ph.flags);
                    if (prot == UnicornConst.UC_PROT_NONE) {
                        prot = UnicornConst.UC_PROT_ALL;
                    }

                    final long begin = load_base + ph.virtual_address;
                    final long end = begin + ph.mem_size;
                    this.mem_map(begin, ph.mem_size, prot, file.getName());
                    unicorn.mem_write(begin, ph.getPtLoadData());

                    regions.add(new MemRegion(begin, end, prot, file.getAbsolutePath(), ph.virtual_address));

                    if (unpackHook != null && (prot & UnicornConst.UC_PROT_EXEC) != 0) { // unpack executable code
                        unicorn.hook_add(new WriteHook() {
                            @Override
                            public void hook(Unicorn u, long address, int size, long value, Object user) {
                                if (address >= begin && address < end) {
                                    unpackHook.hook(u, address - load_base, size, value, user);
                                }
                            }
                        }, begin, end, null);
                    }

                    break;
                case ElfSegment.PT_DYNAMIC:
                    dynamicStructure = ph.getDynamicStructure();
                    break;
                case ElfSegment.PT_INTERP:
                    log.debug("[" + file.getName() + "]interp=" + ph.getIntepreter());
                    break;
                default:
                    log.debug("[" + file.getName() + "]segment type=0x" + Integer.toHexString(ph.type) + ", offset=0x" + Long.toHexString(ph.offset));
                    break;
            }
        }

        if (dynamicStructure == null) {
            throw new IllegalStateException("dynamicStructure is empty.");
        }
        final String soName = dynamicStructure.getSOName(file.getName());

        Map<String, Module> neededLibraries = new HashMap<>();
        for (String neededLibrary : dynamicStructure.getNeededLibraries()) {
            log.debug(soName + " need dependency " + neededLibrary);

            Module loaded = modules.get(neededLibrary);
            if (loaded != null) {
                neededLibraries.put(FilenameUtils.getBaseName(loaded.name), loaded);
                continue;
            }
            File neededLibraryFile = new File(workDir, neededLibrary);
            if (libraryResolver != null && !neededLibraryFile.canRead()) {
                neededLibraryFile = libraryResolver.resolveLibrary(neededLibrary);
            }
            if (neededLibraryFile != null && neededLibraryFile.canRead()) {
                Module needed = loadInternal(workDir, neededLibraryFile, null);
                neededLibraries.put(FilenameUtils.getBaseName(needed.name), needed);
            } else {
                log.info(soName + " load dependency " + neededLibrary + " failed");
            }
        }

        for (Module module : modules.values()) {
            for (Iterator<ModuleSymbol> iterator = module.getUnresolvedSymbol().iterator(); iterator.hasNext(); ) {
                ModuleSymbol moduleSymbol = iterator.next();
                ModuleSymbol resolved = moduleSymbol.resolve(module.getNeededLibraries(), false);
                if (resolved != null) {
                    log.debug("[" + moduleSymbol.soName + "]" + moduleSymbol.symbol.getName() + " symbol resolved to " + resolved.toSoName);
                    resolved.relocation();
                    iterator.remove();
                }
            }
        }

        List<ModuleSymbol> list = new ArrayList<>();
        for (MemoizedObject<ElfRelocation> object : dynamicStructure.getRelocations()) {
            ElfRelocation relocation = object.getValue();
            final int type = relocation.type();
            if (type == 0) {
                continue;
            }
            ElfSymbol symbol = relocation.symbol();
            long sym_value = symbol.value;
            Pointer relocationAddr = UnicornPointer.pointer(unicorn, load_base + relocation.offset());
            assert relocationAddr != null;

            ModuleSymbol moduleSymbol;
            switch (type) {
                case ARMEmulator.R_ARM_ABS32:
                    // relocationAddr.setInt(0, (int) (load_base + sym_value));
                    long offset = relocationAddr.getInt(0);
                    moduleSymbol = resolveSymbol(load_base, symbol, relocationAddr, soName, neededLibraries.values(), offset);
                    if (moduleSymbol == null) {
                        list.add(new ModuleSymbol(soName, load_base, symbol, relocationAddr, null, offset));
                    } else {
                        moduleSymbol.relocation();
                    }
                    break;
                case ARMEmulator.R_ARM_RELATIVE:
                    if (sym_value == 0) {
                        relocationAddr.setInt(0, (int) load_base + relocationAddr.getInt(0));
                    } else {
                        throw new UnsupportedOperationException();
                    }
                    break;
                case ARMEmulator.R_ARM_GLOB_DAT:
                case ARMEmulator.R_ARM_JUMP_SLOT:
                    moduleSymbol = resolveSymbol(load_base, symbol, relocationAddr, soName, neededLibraries.values(), 0);
                    if (moduleSymbol == null) {
                        list.add(new ModuleSymbol(soName, load_base, symbol, relocationAddr, null, 0));
                    } else {
                        moduleSymbol.relocation();
                    }
                    break;
                default:
                    log.warn("Unhandled relocation type " + relocation.type() + ", symbol=" + symbol + ", relocationAddr=0x" + relocationAddr);
                    break;
            }
        }

        List<InitFunction> initFunctionList = new ArrayList<>();
        if (elfFile.file_type == ElfFile.FT_DYN) { // not executable
            int init = dynamicStructure.getInit();
            ElfInitArray preInitArray = dynamicStructure.getPreInitArray();
            ElfInitArray initArray = dynamicStructure.getInitArray();

            initFunctionList.add(new InitFunction(load_base, soName, init));

            if (preInitArray != null) {
                initFunctionList.add(new InitFunction(load_base, soName, preInitArray));
            }

            if (initArray != null) {
                initFunctionList.add(new InitFunction(load_base, soName, initArray));
            }
        }

        SymbolLocator dynsym = dynamicStructure.getSymbolStructure();
        if (dynsym == null) {
            throw new IllegalStateException("dynsym is null");
        }
        Module module = new Module(load_base, bound_high - bound_low, file.getAbsolutePath(), soName, dynsym, list, initFunctionList, neededLibraries, regions);
        if ("libc.so".equals(soName)) { // libc
            ElfSymbol __bionic_brk = module.getELFSymbolByName("__bionic_brk");
            if (__bionic_brk != null) {
                unicorn.mem_write(module.base + __bionic_brk.value, ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt((int) HEAP_BASE).array());
                brk = HEAP_BASE;
            }
        }

        modules.put(soName, module);
        if (maxSoName == null || soName.length() > maxSoName.length()) {
            maxSoName = soName;
        }
        if (bound_high - bound_low > maxSizeOfSo) {
            maxSizeOfSo = bound_high - bound_low;
        }
        module.setEntryPoint(elfFile.entry_point);
        log.debug("Load library " + soName + " offset=" + (System.currentTimeMillis() - start) + "ms" + ", entry_point=0x" + Long.toHexString(elfFile.entry_point));
        if (moduleListener != null) {
            moduleListener.onLoaded(module);
        }
        return module;
    }

    private String maxSoName;
    private long maxSizeOfSo;

    private boolean callInitFunction;

    @Override
    public void setCallInitFunction() {
        this.callInitFunction = true;
    }

    @Override
    public Module findModuleByAddress(long address) {
        for (Module module : modules.values()) {
            if (address >= module.base && address < module.base + module.size) {
                return module;
            }
        }
        return null;
    }

    private ModuleSymbol resolveSymbol(long load_base, ElfSymbol symbol, Pointer relocationAddr, String soName, Collection<Module> neededLibraries, long offset) throws IOException {
        if (!symbol.isUndef()) {
            return new ModuleSymbol(soName, load_base, symbol, relocationAddr, soName, offset);
        }

        return new ModuleSymbol(soName, load_base, symbol, relocationAddr, null, offset).resolve(neededLibraries, false);
    }

    private void mem_map(long address, long size, int prot, String libraryName) {
        Alignment alignment = emulator.align(address, size);

        log.debug("[" + libraryName + "]0x" + Long.toHexString(alignment.address) + " - 0x" + Long.toHexString(alignment.address + alignment.size) + ", size=0x" + Long.toHexString(alignment.size));

        unicorn.mem_map(alignment.address, alignment.size, prot);
    }

    private int get_segment_protection(int flags) {
        int prot = Unicorn.UC_PROT_NONE;
        if ((flags & /* PF_R= */4) != 0) prot |= Unicorn.UC_PROT_READ;
        if ((flags & /* PF_W= */2) != 0) prot |= Unicorn.UC_PROT_WRITE;
        if ((flags & /* PF_X= */1) != 0) prot |= Unicorn.UC_PROT_EXEC;
        return prot;
    }

    @Override
    public long getStackPointer() {
        return sp;
    }

    @Override
    public long allocateStack(int size) {
        long sp = this.sp;
        this.sp -= size;
        return sp;
    }

//    private static final int MAP_SHARED =	0x01;		/* Share changes */
//    private static final int MAP_PRIVATE =	0x02;		/* Changes are private */
//    private static final int MAP_TYPE =	0x0f;		/* Mask for type of mapping */
//    private static final int MAP_FIXED =	0x10;		/* Interpret addr exactly */
//    private static final int MAP_ANONYMOUS =	0x20;		/* don't use a file */

    private final Map<Long, Integer> memoryMap = new TreeMap<>();

    private long allocateMapAddress(int length) {
        Map.Entry<Long, Integer> lastEntry = null;
        for (Map.Entry<Long, Integer> entry : memoryMap.entrySet()) {
            if (lastEntry == null) {
                lastEntry = entry;
            } else {
                long mmapAddress = lastEntry.getKey() + lastEntry.getValue();
                if (mmapAddress + length <= entry.getKey()) {
                    return mmapAddress;
                } else {
                    lastEntry = entry;
                }
            }
        }
        if (lastEntry != null) {
            long mmapAddress = lastEntry.getKey() + lastEntry.getValue();
            if (mmapAddress < mmapBaseAddress) {
                log.debug("allocateMapAddress mmapBaseAddress=0x" + Long.toHexString(mmapBaseAddress) + ", mmapAddress=0x" + Long.toHexString(mmapAddress));
                mmapBaseAddress = mmapAddress;
            }
        }

        long addr = mmapBaseAddress;
        mmapBaseAddress += length;
        return addr;
    }

    @Override
    public int mmap(long start, int length, int prot, int flags, int fd, int offset) {
        if (length % emulator.getPageAlign() != 0) {
            return -1;
        }

        if (start == 0 && fd == -1 && offset == 0) {
            long addr = allocateMapAddress(length);
            log.debug("mmap addr=0x" + Long.toHexString(addr) + ", mmapBaseAddress=0x" + Long.toHexString(mmapBaseAddress));
            unicorn.mem_map(addr, length, prot);
            memoryMap.put(addr, length);
            return (int) addr;
        }
        try {
            FileIO file;
            if (start == 0 && fd > 0 && offset == 0 && (file = syscallHandler.fdMap.get(fd)) != null) {
                long addr = allocateMapAddress(length);
                log.debug("mmap addr=0x" + Long.toHexString(addr) + ", mmapBaseAddress=0x" + Long.toHexString(mmapBaseAddress));
                unicorn.mem_map(addr, length, prot);
                memoryMap.put(addr, length);
                byte[] data = file.readFileToByteArray();
                if (data.length <= length) {
                    unicorn.mem_write(addr, data);
                } else {
                    unicorn.mem_write(addr, Arrays.copyOf(data, length));
                }
                return (int) addr;
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        throw new UnsupportedOperationException();
    }

    @Override
    public int munmap(long start, int length) {
        unicorn.mem_unmap(start, length);
        if(memoryMap.remove(start) != length) {
            throw new IllegalStateException("munmap failed");
        }
        return 0;
    }

    @Override
    public int mprotect(long address, int length, int prot) {
        unicorn.mem_protect(address, length, prot);
        return 0;
    }

    private long brk;

    @Override
    public int brk(long address) {
        if (address % emulator.getPageAlign() != 0) {
            throw new UnsupportedOperationException();
        }

        if (address > brk) {
            unicorn.mem_map(brk, address - brk, UnicornConst.UC_PROT_READ | UnicornConst.UC_PROT_WRITE);
            this.brk = address;
        } else if(address < brk) {
            unicorn.mem_unmap(address, brk - address);
            this.brk = address;
        }

        return (int) this.brk;
    }

    @Override
    public int open(String pathname, int oflags) {
        int minFd = syscallHandler.getMinFd();

        if ("/dev/tty".equals(pathname)) {
            FileIO io = new NullFileIO(pathname);
            syscallHandler.fdMap.put(minFd, io);
            return minFd;
        }

        if ("/proc/self/maps".equals(pathname) || ("/proc/" + emulator.getPid() + "/maps").equals(pathname)) {
            FileIO io = new MapsFileIO(oflags, modules.values());
            syscallHandler.fdMap.put(minFd, io);
            return minFd;
        }
        FileIO driverIO = DriverFileIO.create(oflags, pathname);
        if (driverIO != null) {
            syscallHandler.fdMap.put(minFd, driverIO);
            return minFd;
        }
        if (STDIN.equals(pathname)) {
            FileIO io = new Stdin(oflags);
            syscallHandler.fdMap.put(minFd, io);
            return minFd;
        }

        File file = libraryResolver == null ? null : libraryResolver.resolveFile(pathname);
        if (file == null) {
            emulator.setErrno(Emulator.EACCES);
            return -1;
        }

        if (STDOUT.equals(pathname)) {
            syscallHandler.fdMap.put(FD_STDOUT, new Stdout(oflags, file, pathname, false));
            return FD_STDOUT;
        }
        if (STDERR.equals(pathname)) {
            syscallHandler.fdMap.put(FD_STDERR, new Stdout(oflags, file, pathname, true));
            return FD_STDERR;
        }
        final FileIO io;
        if (pathname.startsWith("/dev/log/")) {
            io = new LogCatFileIO(oflags, file, pathname);
        } else {
            io = new SimpleFileIO(oflags, file, pathname);
        }

        syscallHandler.fdMap.put(minFd, io);
        return minFd;
    }

    @Override
    public String getMaxLengthSoName() {
        return maxSoName;
    }

    @Override
    public long getMaxSizeOfSo() {
        return maxSizeOfSo;
    }
}
