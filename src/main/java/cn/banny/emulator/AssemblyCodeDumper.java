package cn.banny.emulator;

import unicorn.CodeHook;
import unicorn.Unicorn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * my code hook
 * Created by zhkl0228 on 2017/5/2.
 */

class AssemblyCodeDumper implements CodeHook {

    private final Emulator emulator;

    AssemblyCodeDumper(Emulator emulator) {
        super();

        this.emulator = emulator;
    }

    private boolean traceInstruction;
    private long traceBegin, traceEnd;

    private final List<Alt> alts = new ArrayList<>();

    void initialize(long begin, long end, Alt... alts) {
        this.traceInstruction = true;
        this.traceBegin = begin;
        this.traceEnd = end;

        this.alts.clear();
        Collections.addAll(this.alts, alts);
    }

    private boolean canTrace(long address) {
        return traceInstruction && (traceBegin > traceEnd || address >= traceBegin && address <= traceEnd);
    }

    @Override
    public void hook(Unicorn u, long address, int size, Object user) {
        for (Alt alt : alts) {
            if (alt.replaceFunction(u, address)) {
                alt.forceReturn(u);
                return;
            }
        }

        if (canTrace(address)) {
            if (!emulator.printAssemble(address, size)) {
                System.err.println("### Trace Instruction at 0x" + Long.toHexString(address) + ", size = " + size);
            }
        }
    }

}
