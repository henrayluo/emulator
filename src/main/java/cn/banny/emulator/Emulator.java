package cn.banny.emulator;

import cn.banny.emulator.debugger.Debugger;
import cn.banny.emulator.linux.Module;
import cn.banny.emulator.pointer.UnicornPointer;
import unicorn.Unicorn;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;

/**
 * cpu emulator
 * Created by zhkl0228 on 2017/5/2.
 */

public interface Emulator extends Closeable, Disassembler {

    int EPERM = 1;
    int EBADF = 9;
    int EAGAIN = 11;
    int ENOMEM = 12;
    int EACCES = 13;
    int EINVAL = 22;
    int ECONNREFUSED = 111;

    int getPageAlign();

    /**
     * trace memory read
     */
    Emulator traceRead();
    Emulator traceRead(long begin, long end);

    /**
     * trace memory write
     */
    Emulator traceWrite();
    Emulator traceWrite(long begin, long end);

    /**
     * trace instruction
     */
    void traceCode();
    void traceCode(long begin, long end);

    Number[] eFunc(long begin, Number... args);

    void eInit(long begin);

    Number eEntry(long begin, long sp);

    /**
     * emulate block
     * @param begin start address
     * @param until stop address
     */
    Unicorn eBlock(long begin, long until);

    /**
     * show all registers
     */
    void showRegs();

    /**
     * show registers
     */
    void showRegs(int... regs);

    Module loadLibrary(File libraryFile) throws IOException;
    Module loadLibrary(File libraryFile, boolean forceCallInit) throws IOException;

    Alignment align(long addr, long size);

    Memory getMemory();

    Unicorn getUnicorn();

    int getPid();

    String getProcessName();

    Debugger attach();

}
