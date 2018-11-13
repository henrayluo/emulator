package cn.banny.emulator.debugger;

import capstone.Capstone;
import cn.banny.auxiliary.Inspector;
import cn.banny.emulator.Emulator;
import cn.banny.emulator.Memory;
import cn.banny.emulator.arm.ARM;
import cn.banny.emulator.arm.AndroidARMEmulator;
import cn.banny.emulator.linux.Module;
import cn.banny.emulator.pointer.UnicornPointer;
import com.sun.jna.Pointer;
import net.fornwall.jelf.ElfSymbol;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import unicorn.ArmConst;
import unicorn.Unicorn;
import unicorn.UnicornException;

import java.io.IOException;
import java.util.*;

public class SimpleDebugger implements Debugger {

    private static final Log log = LogFactory.getLog(SimpleDebugger.class);

    private final Map<Long, Module> breakMap = new HashMap<>();

    @Override
    public void addBreakPoint(Module module, String symbol) {
        try {
            ElfSymbol elfSymbol = module.getELFSymbolByName(symbol);
            if (elfSymbol == null) {
                throw new IllegalStateException("find symbol failed: " + symbol);
            }
            addBreakPoint(module, elfSymbol.value);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void addBreakPoint(Module module, long offset) {
        long address = (module == null ? offset : module.base + offset) & 0xfffffffeL;
        if (log.isDebugEnabled()) {
            log.debug("addBreakPoint address=0x" + Long.toHexString(address));
        }
        breakMap.put(address, module);
    }

    private class CodeHistory {
        final long address;
        final byte[] asm;
        final boolean thumb;
        CodeHistory(long address, byte[] asm, boolean thumb) {
            this.address = address;
            this.asm = asm;
            this.thumb = thumb;
        }
        Capstone.CsInsn disassemble(Emulator emulator) {
            return emulator.disassemble(address, asm, thumb)[0];
        }
    }

    private final List<CodeHistory> historyList = new ArrayList<>();

    @Override
    public void hook(Unicorn u, long address, int size, Object user) {
        Emulator emulator = (Emulator) user;

        while (historyList.size() > 10) {
            historyList.remove(0);
        }
        CodeHistory history = new CodeHistory(address, u.mem_read(address, size), ARM.isThumb(u));
        historyList.add(history);

        singleStep--;

        if (breakMap.containsKey(address)) {
            Module breakModule = breakMap.get(address);
            if (breakModule == null) {
                breakMap.remove(address); // remove temp breakpoint
            }
            loop(emulator, u, address, size);
        } else if (singleStep == 0) {
            loop(emulator, u, address, size);
        } else if (breakMnemonic != null) {
            Capstone.CsInsn ins = history.disassemble(emulator);
            if (breakMnemonic.equals(ins.mnemonic)) {
                breakMnemonic = null;
                loop(emulator, u, address, size);
            }
        }
    }

    @Override
    public void debug(Emulator emulator) {
        Unicorn unicorn = emulator.getUnicorn();
        long address = ((Number) unicorn.reg_read(ArmConst.UC_ARM_REG_PC)).intValue() & 0xffffffffL;
        loop(emulator, unicorn, address, 0);
    }

    private int singleStep;

    private void loop(Emulator emulator, Unicorn u, long address, int size) {
        System.out.println("debugger break at: 0x" + Long.toHexString(address));
        boolean thumb = ARM.isThumb(u);
        long nextAddress = 0;
        try {
            emulator.showRegs();
            nextAddress = disassemble(emulator, address, size, thumb);
        } catch (UnicornException e) {
            e.printStackTrace();
        }

        Scanner scanner = new Scanner(System.in);
        String line;
        while ((line = scanner.nextLine()) != null) {
            try {
                if ("help".equals(line)) {
                    System.out.println("c: continue");
                    System.out.println("n: step over");
                    System.out.println("bt: back trace");
                    System.out.println();
                    System.out.println("s|si: step into");
                    System.out.println("s[decimal]: execute specified amount instruction");
                    System.out.println("sblx: execute util BLX mnemonic");
                    System.out.println();
                    System.out.println("m(op) [size]: show memory, default size is 0x70, size may hex or decimal");
                    System.out.println("mr0-mr7, mfp, mip, msp [size]: show memory of specified register");
                    System.out.println("m(address) [size]: show memory of specified address, address must start with 0x");
                    System.out.println();
                    System.out.println("b(address): add temporarily breakpoint, address must start with 0x, can be module offset");
                    System.out.println("blr: add temporarily breakpoint of register LR");
                    System.out.println();
                    System.out.println("d|dis: show disassemble");
                    System.out.println("stop: stop emulation");
                    continue;
                }
                if ("d".equals(line) || "dis".equals(line)) {
                    disassemble(emulator, address, size, thumb);
                    continue;
                }
                if (line.startsWith("m")) {
                    String command = line;
                    String[] tokens = line.split("\\s+");
                    int length = 0x70;
                    try {
                        if (tokens.length >= 2) {
                            command = tokens[0];
                            int radix = 10;
                            String str = tokens[1];
                            if (str.startsWith("0x")) {
                                str = str.substring(2);
                                radix = 16;
                            }
                            length = Integer.parseInt(str, radix);
                        }
                    } catch(NumberFormatException ignored) {}

                    int reg = -1;
                    String name = null;
                    if (command.startsWith("mr") && command.length() == 3) {
                        char c = command.charAt(2);
                        if (c >= '0' && c <= '7') {
                            int r = c - '0';
                            reg = ArmConst.UC_ARM_REG_R0 + r;
                            name = "r" + r;
                        }
                    } else if ("mfp".equals(command)) {
                        reg = ArmConst.UC_ARM_REG_FP;
                        name = "fp";
                    } else if ("mip".equals(command)) {
                        reg = ArmConst.UC_ARM_REG_IP;
                        name = "ip";
                    } else if ("msp".equals(command)) {
                        reg = ArmConst.UC_ARM_REG_SP;
                        name = "sp";
                    } else if (command.startsWith("m0x")) {
                        long addr = Long.parseLong(command.substring(3), 16);
                        Pointer pointer = UnicornPointer.pointer(u, addr);
                        if (pointer != null) {
                            Inspector.inspect(pointer.getByteArray(0, length), pointer.toString());
                        } else {
                            System.out.println(addr + " is null");
                        }
                        continue;
                    }
                    if (reg != -1) {
                        Pointer pointer = UnicornPointer.register(u, reg);
                        if (pointer != null) {
                            Inspector.inspect(pointer.getByteArray(0, length), name + "=" + pointer);
                        } else {
                            System.out.println(name + " is null");
                        }
                        continue;
                    }
                }
                if ("bt".equals(line)) {
                    Memory memory = emulator.getMemory();
                    String maxLengthSoName = memory.getMaxLengthSoName();
                    boolean hasTrace = false;
                    UnicornPointer sp = UnicornPointer.register(u, ArmConst.UC_ARM_REG_SP);
                    UnicornPointer lr = UnicornPointer.register(u, ArmConst.UC_ARM_REG_LR);
                    UnicornPointer r7 = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R7);
                    do {
                        Module module = memory.findModuleByAddress(lr.peer);
                        if (lr.peer == AndroidARMEmulator.LR) {
                            break;
                        }

                        hasTrace = true;
                        StringBuilder sb = new StringBuilder();
                        if (module != null) {
                            sb.append(String.format("[%" + maxLengthSoName.length() + "s]", module.name));
                            sb.append(String.format("[0x%0" + Long.toHexString(memory.getMaxSizeOfSo()).length() + "x]", lr.peer - module.base + (thumb ? 1 : 0)));
                        } else {
                            sb.append(String.format("[%" + maxLengthSoName.length() + "s]", "0x" + Long.toHexString(lr.peer)));
                            sb.append(String.format("[0x%0" + Long.toHexString(memory.getMaxSizeOfSo()).length() + "x]", lr.peer - 0xfffe0000L + (thumb ? 1 : 0)));
                        }
                        System.out.println(sb);

                        if (r7.peer <= sp.peer) {
                            System.err.println("r7=" + r7 + ", sp=" + sp);
                            break;
                        }

                        r7 = r7.getPointer(0);
                        lr = r7.getPointer(4);
                    } while(true);
                    if (!hasTrace) {
                        System.err.println("Decode back trace failed.");
                    }
                    continue;
                }
                if (line.startsWith("b0x")) {
                    try {
                        long addr = Long.parseLong(line.substring(3), 16) & 0xFFFFFFFFFFFFFFFEL;
                        Module module;
                        if (addr < Memory.MMAP_BASE && (module = emulator.getMemory().findModuleByAddress(address)) != null) {
                            addr += module.base;
                        }
                        breakMap.put(addr, null); // temp breakpoint
                        System.out.println("Add temporarily breakpoint: 0x" + Long.toHexString(addr));
                        continue;
                    } catch(NumberFormatException ignored) {
                    }
                }
                if ("blr".equals(line)) { // break LR
                    long addr = ((Number) u.reg_read(ArmConst.UC_ARM_REG_LR)).intValue() & 0xffffffffL;
                    breakMap.put(addr, null);
                    System.out.println("Add temporarily breakpoint: 0x" + Long.toHexString(addr));
                    continue;
                }
                if ("c".equals(line)) { // continue
                    break;
                }
                if ("n".equals(line)) {
                    if (nextAddress == 0) {
                        System.out.println("Next address failed.");
                        continue;
                    } else {
                        // System.out.println("Add temporarily breakpoint: 0x" + Long.toHexString(nextAddress));
                        breakMap.put(nextAddress, null);
                        break;
                    }
                }
                if ("stop".equals(line)) {
                    u.emu_stop();
                    break;
                }
                if ("s".equals(line) || "si".equals(line)) {
                    singleStep = 1;
                    break;
                }
                if (line.startsWith("s")) {
                    try {
                        singleStep = Integer.parseInt(line.substring(1));
                        break;
                    } catch (NumberFormatException e) {
                        breakMnemonic = line.substring(1);
                        break;
                    }
                }
            } catch (UnicornException e) {
                e.printStackTrace();
            }
        }
    }

    private String breakMnemonic;

    /**
     * @return next address
     */
    private long disassemble(Emulator emulator, long address, int size, boolean thumb) {
        long next = 0;
        boolean on = false;
        StringBuilder sb = new StringBuilder();
        for (CodeHistory history : historyList) {
            if (history.address == address) {
                sb.append("=> *");
                on = true;
            } else {
                sb.append("    ");
                if (on) {
                    next = history.address;
                    on = false;
                }
            }
            Capstone.CsInsn ins = history.disassemble(emulator);
            sb.append(ARM.assembleDetail(emulator.getMemory(), ins, history.address, history.thumb, on ? '*' : ' ')).append('\n');
        }
        long nextAddr = address + size;
        Capstone.CsInsn[] insns = emulator.disassemble(nextAddr, 4 * 10, 10);
        for (Capstone.CsInsn ins : insns) {
            if (nextAddr == address) {
                sb.append("=> *");
                on = true;
            } else {
                sb.append("    ");
                if (on) {
                    next = nextAddr;
                    on = false;
                }
            }
            sb.append(ARM.assembleDetail(emulator.getMemory(), ins, nextAddr, thumb, on ? '*' : ' ')).append('\n');
            nextAddr += ins.size;
        }
        System.out.println(sb);
        return next;
    }

}
