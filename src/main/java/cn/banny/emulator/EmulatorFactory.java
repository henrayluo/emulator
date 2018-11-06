package cn.banny.emulator;

import cn.banny.emulator.arm.DefaultARMEmulator;

/**
 * emulator factory
 * Created by zhkl0228 on 2017/5/2.
 */

public class EmulatorFactory {

    /**
     * @return arm emulator
     */
    public static Emulator createARMEmulator() {
        return new DefaultARMEmulator();
    }

}
