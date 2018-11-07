package cn.banny.emulator.arm;

import capstone.Capstone;
import cn.banny.emulator.AbstractEmulator;
import cn.banny.emulator.pointer.UnicornPointer;
import cn.banny.utils.Hex;
import com.sun.jna.Pointer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import unicorn.ArmConst;
import unicorn.Unicorn;
import unicorn.UnicornConst;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static unicorn.ArmConst.*;

/**
 * default arm emulator
 * Created by zhkl0228 on 2017/5/2.
 */

public class DefaultARMEmulator extends AbstractEmulator implements ARMEmulator {

    private static final Log log = LogFactory.getLog(DefaultARMEmulator.class);

    private final Capstone capstoneArm, capstoneThumb;

    public DefaultARMEmulator() {
        super(UnicornConst.UC_ARCH_ARM, UnicornConst.UC_MODE_ARM);
        this.switchUserMode();

        this.capstoneArm = new Capstone(Capstone.CS_ARCH_ARM, Capstone.CS_MODE_ARM);
        this.capstoneThumb = new Capstone(Capstone.CS_ARCH_ARM, Capstone.CS_MODE_THUMB);

        setupTraps();
    }

    /**
     * https://github.com/lunixbochs/usercorn/blob/master/go/arch/arm/linux.go
     */
    private void setupTraps() {
        unicorn.mem_map(0xffff0000L, 0x10000, UnicornConst.UC_PROT_READ | UnicornConst.UC_PROT_EXEC);
        byte[] b0 = new byte[] { 0x00, (byte) 0xf0, (byte) 0xa0, (byte) 0xe3 };
        ByteBuffer buffer = ByteBuffer.allocate(0x10000);
        // write "mov pc, #0" to all kernel trap addresses so they will throw exception
        for (int i = 0; i < 0x10000; i += 4) {
            buffer.put(b0);
        }
        unicorn.mem_write(0xffff0000L, buffer.array());

        try {
            byte[] __kuser_memory_barrier = new byte[] { 0x1e, (byte) 0xff, 0x2f, (byte) 0xe1 }; // bx lr
            byte[] __kuser_cmpxchg = Hex.decodeHex("5ff07ff59f3f92e1003053e0913f820101003303faffff0a000073e2efffffea".toCharArray());
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
        } catch (Hex.DecoderException e) {
            throw new IllegalStateException(e);
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
        long sp = initializeTLS(this.memory.getStackPointer());
        final long tls = sp;

        final List<Number> numbers = new ArrayList<>(10);
        int i = 0;
        int[] regArgs = ARM.getRegArgs();
        final Arguments args = new Arguments(unicorn, sp, arguments);
        sp = args.sp;

        while (args.args != null && i < args.args.length && i < regArgs.length) {
            unicorn.reg_write(regArgs[i], args.args[i]);
            i++;
        }
        while (args.args != null && i < args.args.length) {
            Number number = (Number) args.args[i];
            sp -= 4;
            Pointer pointer = UnicornPointer.pointer(unicorn, sp);
            assert pointer != null;
            pointer.setInt(0, number.intValue());
            i++;
        }

        unicorn.reg_write(UC_ARM_REG_C13_C0_3, tls);

        final long lr = 0xffff0fa0L;
        initialize(sp, lr);
        emulate(begin, lr, timeout);
        numbers.add(0, (Number) unicorn.reg_read(ArmConst.UC_ARM_REG_R0));
        numbers.addAll(args.pointers);
        return numbers.toArray(new Number[0]);
    }

    @Override
    public Number eFunc(long begin, UnicornPointer sp) {
        long tls = initializeTLS(this.memory.getStackPointer());
        unicorn.reg_write(UC_ARM_REG_C13_C0_3, tls);

        initialize(sp.peer, 0);
        emulate(begin, 0, timeout);
        return (Number) unicorn.reg_read(ArmConst.UC_ARM_REG_R0);
    }

    private void initialize(long sp, long lr) {
        unicorn.reg_write(ArmConst.UC_ARM_REG_SP, sp);
        unicorn.reg_write(ArmConst.UC_ARM_REG_LR, lr);
        log.debug("initialize sp=0x" + Long.toHexString(sp) + ", lr=0x" + Long.toHexString(lr));

        enableVFP();
    }

    private void switchUserMode() {
        int value = ((Number) unicorn.reg_read(UC_ARM_REG_CPSR)).intValue();
        value &= 0x7ffffff0;
        unicorn.reg_write(UC_ARM_REG_CPSR, value);
    }

    private void enableVFP() {
        int value = ((Number) unicorn.reg_read(UC_ARM_REG_C1_C0_2)).intValue();
        value |= (0xf << 20);
        unicorn.reg_write(UC_ARM_REG_C1_C0_2, value);
        unicorn.reg_write(UC_ARM_REG_FPEXC, 0x40000000);
    }

    @Override
    public Unicorn eBlock(long begin, long until) {
        long sp = initializeTLS(this.memory.getStackPointer());
        unicorn.reg_write(UC_ARM_REG_C13_C0_3, sp); // errno

        initialize(sp, 0);
        emulate(begin, until, traceInstruction ? 0 : timeout);
        showRegs();
        return unicorn;
    }

    private long initializeTLS(long sp) {
        sp -= 0x400; // reserve space for pthread_internal_t
        final Pointer thread = UnicornPointer.pointer(unicorn, sp);

        sp -= 4;
        final Pointer __stack_chk_guard = UnicornPointer.pointer(unicorn, sp);

        int size = ARM.writeCString(unicorn, sp, getProcessName());
        sp -= size;
        final Pointer programName = UnicornPointer.pointer(unicorn, sp);

        sp -= 4;
        final Pointer programNamePointer = UnicornPointer.pointer(unicorn, sp);
        assert programNamePointer != null;
        programNamePointer.setPointer(0, programName);

        sp -= 0x100;
        final Pointer vector = UnicornPointer.pointer(unicorn, sp);
        assert vector != null;
        vector.setInt(0, 25); // AT_RANDOM is a pointer to 16 bytes of randomness on the stack.
        vector.setPointer(4, __stack_chk_guard);

        sp -= 4;
        final Pointer environ = UnicornPointer.pointer(unicorn, sp);
        assert environ != null;
        environ.setInt(0, 0);

        sp -= 0x100;
        final Pointer argv = UnicornPointer.pointer(unicorn, sp);
        assert argv != null;
        argv.setPointer(4, programNamePointer);
        argv.setPointer(8, environ);
        argv.setPointer(0xc, vector);

        sp -= (0x80 * 4); // tls size
        final Pointer tls = UnicornPointer.pointer(unicorn, sp);
        assert tls != null;
        tls.setPointer(4, thread);
        this.errno = tls.share(8);
        tls.setPointer(0xc, argv);

        log.debug("initializeTLS tls=" + tls + ", argv=" + argv + ", vector=" + vector + ", thread=" + thread + ", environ=" + environ);
        return sp;
    }

    private Pointer errno;

    @Override
    public void setErrno(int errno) {
        this.errno.setInt(0, errno);
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
