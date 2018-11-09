package cn.banny.emulator.arm;

import cn.banny.emulator.Emulator;

/**
 * arm emulator
 * Created by zhkl0228 on 2017/5/2.
 */

public interface ARMEmulator extends Emulator {

    // From http://infocenter.arm.com/help/topic/com.arm.doc.ihi0044f/IHI0044F_aaelf.pdf

    /**
     * 用户模式
     */
    int USR_MODE = 0b10000;

    /**
     * 管理模式
     */
    int SVC_MODE = 0b10011;

    int R_ARM_ABS32 = 2;
    int R_ARM_REL32 = 3;
    int R_ARM_GLOB_DAT = 21;
    int R_ARM_JUMP_SLOT = 22;
    int R_ARM_RELATIVE = 23;

    int PAGE_ALIGN = 0x1000; // 4k

}
