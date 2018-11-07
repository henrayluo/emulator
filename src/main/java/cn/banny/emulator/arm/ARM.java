package cn.banny.emulator.arm;

import capstone.Capstone;
import cn.banny.emulator.Memory;
import cn.banny.emulator.linux.Module;
import unicorn.ArmConst;
import unicorn.Unicorn;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

/**
 * arm utils
 * Created by zhkl0228 on 2017/5/11.
 */

public class ARM {

    private static int getBit(long value, int offset) {
        long mask = 1L << offset;
        return (value & mask) != 0 ? 1 : 0;
    }

    private static final int CPSR_THUMB_BIT = 5;

    public static boolean isThumb(Unicorn unicorn) {
        Number cpsr = (Number) unicorn.reg_read(ArmConst.UC_ARM_REG_CPSR);
        return getBit(cpsr.intValue(), ARM.CPSR_THUMB_BIT) == 1;
    }

    public static void showThumbRegs(Unicorn unicorn) {
        showRegs(unicorn, ARM.THUMB_REGS);
    }

    static void showRegs(Unicorn unicorn, int[] regs) {
        if (regs == null || regs.length < 1) {
            regs = ARM.getAllRegisters(isThumb(unicorn));
        }
        StringBuilder builder = new StringBuilder();
        builder.append(">>>");
        for (int reg : regs) {
            Number number;
            int value;
            switch (reg) {
                case ArmConst.UC_ARM_REG_CPSR:
                    number = (Number) unicorn.reg_read(ArmConst.UC_ARM_REG_CPSR);
                    value = number.intValue();
                    builder.append(String.format(Locale.US, " cpsr: N=%d, Z=%d, C=%d, V=%d, T=%d, mode=",
                            getBit(value, 31),
                            getBit(value, 30),
                            getBit(value, 29),
                            getBit(value, 28),
                            getBit(value, CPSR_THUMB_BIT))).append(Integer.toBinaryString(value & 0x1f));
                    break;
                case ArmConst.UC_ARM_REG_R0:
                    number = (Number) unicorn.reg_read(reg);
                    value = number.intValue();
                    builder.append(String.format(Locale.US, " r0=0x%x", value));
                    break;
                case ArmConst.UC_ARM_REG_R1:
                    number = (Number) unicorn.reg_read(reg);
                    value = number.intValue();
                    builder.append(String.format(Locale.US, " r1=0x%x", value));
                    break;
                case ArmConst.UC_ARM_REG_R2:
                    number = (Number) unicorn.reg_read(reg);
                    value = number.intValue();
                    builder.append(String.format(Locale.US, " r2=0x%x", value));
                    break;
                case ArmConst.UC_ARM_REG_R3:
                    number = (Number) unicorn.reg_read(reg);
                    value = number.intValue();
                    builder.append(String.format(Locale.US, ", r3=0x%x", value));
                    break;
                case ArmConst.UC_ARM_REG_R4:
                    number = (Number) unicorn.reg_read(reg);
                    value = number.intValue();
                    builder.append(String.format(Locale.US, " r4=0x%x", value));
                    break;
                case ArmConst.UC_ARM_REG_R5:
                    number = (Number) unicorn.reg_read(reg);
                    value = number.intValue();
                    builder.append(String.format(Locale.US, " r5=0x%x", value));
                    break;
                case ArmConst.UC_ARM_REG_R6:
                    number = (Number) unicorn.reg_read(reg);
                    value = number.intValue();
                    builder.append(String.format(Locale.US, " r6=0x%x", value));
                    break;
                case ArmConst.UC_ARM_REG_R7:
                    number = (Number) unicorn.reg_read(reg);
                    value = number.intValue();
                    builder.append(String.format(Locale.US, " r7=0x%x", value));
                    break;
                case ArmConst.UC_ARM_REG_R8:
                    number = (Number) unicorn.reg_read(reg);
                    value = number.intValue();
                    builder.append(String.format(Locale.US, " r8=0x%x", value));
                    break;
                case ArmConst.UC_ARM_REG_R9:
                    number = (Number) unicorn.reg_read(reg);
                    value = number.intValue();
                    builder.append(String.format(Locale.US, " r9=0x%x", value));
                    break;
                case ArmConst.UC_ARM_REG_R10:
                    number = (Number) unicorn.reg_read(reg);
                    value = number.intValue();
                    builder.append(String.format(Locale.US, " r10=0x%x", value));
                    break;
                case ArmConst.UC_ARM_REG_FP:
                    number = (Number) unicorn.reg_read(reg);
                    value = number.intValue();
                    builder.append(String.format(Locale.US, " fp=0x%x", value));
                    break;
                case ArmConst.UC_ARM_REG_IP:
                    number = (Number) unicorn.reg_read(reg);
                    value = number.intValue();
                    builder.append(String.format(Locale.US, " ip=0x%x", value));
                    break;
                case ArmConst.UC_ARM_REG_SP:
                    number = (Number) unicorn.reg_read(reg);
                    value = number.intValue();
                    builder.append(String.format(Locale.US, " sp=0x%x", value));
                    break;
                case ArmConst.UC_ARM_REG_LR:
                    number = (Number) unicorn.reg_read(reg);
                    value = number.intValue();
                    builder.append(String.format(Locale.US, " lr=0x%x", value));
                    break;
                case ArmConst.UC_ARM_REG_PC:
                    number = (Number) unicorn.reg_read(reg);
                    value = number.intValue();
                    builder.append(String.format(Locale.US, " pc=0x%x", value));
                    break;
            }
        }
        System.out.println(builder.toString());
    }

    private static final int[] ARG_REGS = new int[] {
            ArmConst.UC_ARM_REG_R0,
            ArmConst.UC_ARM_REG_R1,
            ArmConst.UC_ARM_REG_R2,
            ArmConst.UC_ARM_REG_R3
    };

    private static final int[] THUMB_REGS = new int[] {
            ArmConst.UC_ARM_REG_R0,
            ArmConst.UC_ARM_REG_R1,
            ArmConst.UC_ARM_REG_R2,
            ArmConst.UC_ARM_REG_R3,
            ArmConst.UC_ARM_REG_R4,
            ArmConst.UC_ARM_REG_R5,
            ArmConst.UC_ARM_REG_R6,
            ArmConst.UC_ARM_REG_R7,

            ArmConst.UC_ARM_REG_FP,
            ArmConst.UC_ARM_REG_IP,

            ArmConst.UC_ARM_REG_SP,
            ArmConst.UC_ARM_REG_LR,
            ArmConst.UC_ARM_REG_PC,
            ArmConst.UC_ARM_REG_CPSR
    };
    private static final int[] ARM_REGS = new int[] {
            ArmConst.UC_ARM_REG_R0,
            ArmConst.UC_ARM_REG_R1,
            ArmConst.UC_ARM_REG_R2,
            ArmConst.UC_ARM_REG_R3,
            ArmConst.UC_ARM_REG_R4,
            ArmConst.UC_ARM_REG_R5,
            ArmConst.UC_ARM_REG_R6,
            ArmConst.UC_ARM_REG_R7,
            ArmConst.UC_ARM_REG_R8,
            ArmConst.UC_ARM_REG_R9,
            ArmConst.UC_ARM_REG_R10,

            ArmConst.UC_ARM_REG_FP,
            ArmConst.UC_ARM_REG_IP,

            ArmConst.UC_ARM_REG_SP,
            ArmConst.UC_ARM_REG_LR,
            ArmConst.UC_ARM_REG_PC,
            ArmConst.UC_ARM_REG_CPSR
    };

    static int[] getRegArgs() {
        return ARG_REGS;
    }

    private static int[] getAllRegisters(boolean thumb) {
        return thumb ? THUMB_REGS : ARM_REGS;
    }

    private static final int ALIGN_SIZE_BASE = 4;

    private static int alignSize(int size) {
        return ((size - 1) / ALIGN_SIZE_BASE + 1) * ALIGN_SIZE_BASE;
    }

    public static long alignSize(long size, long align) {
        return ((size - 1) / align + 1) * align;
    }

    public static int writeCString(Unicorn unicorn, final long addr, String str) {
        byte[] data = str.getBytes(StandardCharsets.UTF_8);
        return writeBytes(unicorn, addr, Arrays.copyOf(data, data.length + 1));
    }
    static int writeBytes(Unicorn unicorn, final long addr, byte[] data) {
        int alignSize = ARM.alignSize(data.length);
        unicorn.mem_write(addr - alignSize, Arrays.copyOf(data, data.length));
        return alignSize;
    }

    public static String readCString(Unicorn unicorn, long address) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(0x1000);
        int size = 0;
        try {
            while (true) {
                byte[] oneByte = unicorn.mem_read(address, 1);
                size += oneByte.length;

                if (size > 0x1000) {
                    throw new IllegalStateException("read utf8 string failed");
                }

                if (oneByte[0] == 0) {
                    break;
                }
                baos.write(oneByte);
                address += oneByte.length;
            }

            return baos.toString("UTf-8");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String assembleDetail(Memory memory, Capstone.CsInsn ins, long address, boolean thumb) {
        StringBuilder sb = new StringBuilder();
        Module module = memory.findModuleByAddress(address);
        if (module != null) {
            sb.append(String.format("[%" + memory.getMaxLengthSoName().length() + "s] ", module.name));
            sb.append(String.format("[0x%0" + Long.toHexString(memory.getMaxSizeOfSo()).length() + "x] ", address - module.base + (thumb ? 1 : 0)));
        }
        sb.append("[");
        if (ins.size == 2) {
            sb.append("      ");
        }
        for (byte b : ins.bytes) {
            sb.append(' ');
            String hex = Integer.toHexString(b & 0xff);
            if (hex.length() == 1) {
                sb.append(0);
            }
            sb.append(hex);
        }
        sb.append(" ] ");
        sb.append(String.format("0x%08x: %s %s", ins.address, ins.mnemonic, ins.opStr));
        return sb.toString();
    }

}
