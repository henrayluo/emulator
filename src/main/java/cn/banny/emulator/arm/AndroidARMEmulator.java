package cn.banny.emulator.arm;

import capstone.Capstone;
import cn.banny.emulator.AbstractEmulator;
import com.sun.jna.Pointer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import unicorn.ArmConst;
import unicorn.Unicorn;
import unicorn.UnicornConst;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * android arm emulator
 * Created by zhkl0228 on 2017/5/2.
 */

public class AndroidARMEmulator extends AbstractEmulator implements ARMEmulator {

    private static final Log log = LogFactory.getLog(AndroidARMEmulator.class);

    private final Capstone capstoneArm, capstoneThumb;
    public static final long LR = 0xffff0000L;

    public AndroidARMEmulator() {
        this(null);
    }

    public AndroidARMEmulator(String processName) {
        super(UnicornConst.UC_ARCH_ARM, UnicornConst.UC_MODE_ARM, processName);

        this.capstoneArm = new Capstone(Capstone.CS_ARCH_ARM, Capstone.CS_MODE_ARM);
        this.capstoneThumb = new Capstone(Capstone.CS_ARCH_ARM, Capstone.CS_MODE_THUMB);

        setupTraps();
    }

    /**
     * https://github.com/lunixbochs/usercorn/blob/master/go/arch/arm/linux.go
     */
    private void setupTraps() {
        unicorn.mem_map(0xffff0000L, 0x10000, UnicornConst.UC_PROT_READ | UnicornConst.UC_PROT_EXEC);
        byte[] b0 = new byte[] {
                (byte) 0x00, (byte) 0xf0, (byte) 0xa0, (byte) 0xe3, // mov pc, #0
        };
        ByteBuffer buffer = ByteBuffer.allocate(0x10000);
        // write "mov pc, #0" to all kernel trap addresses so they will throw exception
        for (int i = 0; i < 0x10000; i += 4) {
            buffer.put(b0);
        }
        unicorn.mem_write(0xffff0000L, buffer.array());

        byte[] __kuser_memory_barrier = new byte[] {
                (byte) 0x1e, (byte) 0xff, (byte) 0x2f, (byte) 0xe1, // bx lr
        };
        byte[] __kuser_cmpxchg = new byte[] {
                (byte) 0x5f, (byte) 0xf0, (byte) 0x7f, (byte) 0xf5, // dmb sy
                (byte) 0x9f, (byte) 0x3f, (byte) 0x92, (byte) 0xe1, // ldrex r3, [r2]
                (byte) 0x00, (byte) 0x30, (byte) 0x53, (byte) 0xe0, // subs r3, r3, r0
                (byte) 0x91, (byte) 0x3f, (byte) 0x82, (byte) 0x01, // strexeq r3, r1, [r2]
                (byte) 0x01, (byte) 0x00, (byte) 0x33, (byte) 0x03, // teqeq r3, #1,
                (byte) 0xfa, (byte) 0xff, (byte) 0xff, (byte) 0x0a, // beq #0xffff0fc4
                (byte) 0x00, (byte) 0x00, (byte) 0x73, (byte) 0xe2, // rsbs r0, r3, #0
                (byte) 0xef, (byte) 0xff, (byte) 0xff, (byte) 0xea, // b #0xffff0fa0
        };
        unicorn.mem_write(0xffff0fa0L, __kuser_memory_barrier);
        unicorn.mem_write(0xffff0fc0L, __kuser_cmpxchg);

        if (log.isDebugEnabled()) {
            log.debug("__kuser_memory_barrier");
            for (int i = 0; i < __kuser_memory_barrier.length; i += 4) {
                printAssemble(0xffff0fa0L + i, 4);
            }
            log.debug("__kuser_cmpxchg");
            for (int i = 0; i < __kuser_cmpxchg.length; i += 4) {
                printAssemble(0xffff0fc0L + i, 4);
            }
        }
    }

    @Override
    public boolean printAssemble(long address, int size) {
        printAssemble(disassemble(address, size, 0), address, ARM.isThumb(unicorn));
        return true;
    }

    @Override
    public Capstone.CsInsn[] disassemble(long address, int size, long count) {
        boolean thumb = ARM.isThumb(unicorn);
        byte[] code = unicorn.mem_read(address, size);
        return thumb ? capstoneThumb.disasm(code, address, count) : capstoneArm.disasm(code, address, count);
    }

    @Override
    public Capstone.CsInsn[] disassemble(long address, byte[] code, boolean thumb) {
        return thumb ? capstoneThumb.disasm(code, address) : capstoneArm.disasm(code, address);
    }

    private void printAssemble(Capstone.CsInsn[] insns, long address, boolean thumb) {
        StringBuilder sb = new StringBuilder();
        for (Capstone.CsInsn ins : insns) {
            sb.append("### Trace Instruction ");
            sb.append(ARM.assembleDetail(memory, ins, address, thumb));
            sb.append('\n');
            address += ins.size;
        }
        System.out.print(sb.toString());
    }

    @Override
    protected void closeInternal() {
        capstoneThumb.close();
        capstoneArm.close();
    }

    @Override
    public int getPageAlign() {
        return PAGE_ALIGN;
    }

    @Override
    public Number[] eFunc(long begin, Number... arguments) {
        int i = 0;
        int[] regArgs = ARM.getRegArgs();
        final Arguments args = new Arguments(this.memory, arguments);

        while (args.args != null && i < args.args.length && i < regArgs.length) {
            unicorn.reg_write(regArgs[i], args.args[i]);
            i++;
        }
        while (args.args != null && i < args.args.length) {
            Number number = (Number) args.args[i];
            Pointer pointer = memory.allocateStack(4);
            assert pointer != null;
            pointer.setInt(0, number.intValue());
            i++;
        }

        unicorn.reg_write(ArmConst.UC_ARM_REG_LR, LR);
        final List<Number> numbers = new ArrayList<>(10);
        numbers.add(emulate(begin, LR, timeout, true));
        numbers.addAll(args.pointers);
        return numbers.toArray(new Number[0]);
    }

    @Override
    public void eInit(long begin) {
        unicorn.reg_write(ArmConst.UC_ARM_REG_LR, LR);
        emulate(begin, LR, timeout, false);
    }

    @Override
    public Number eEntry(long begin, long sp) {
        memory.setStackPoint(sp);
        unicorn.reg_write(ArmConst.UC_ARM_REG_LR, LR);
        return emulate(begin, LR, timeout, true);
    }

    @Override
    public Unicorn eBlock(long begin, long until) {
        unicorn.reg_write(ArmConst.UC_ARM_REG_LR, LR);
        emulate(begin, until, traceInstruction ? 0 : timeout, true);
        return unicorn;
    }

    @Override
    public void showRegs() {
        this.showRegs((int[]) null);
    }

    @Override
    public void showRegs(int... regs) {
        ARM.showRegs(unicorn, regs);
    }

}
