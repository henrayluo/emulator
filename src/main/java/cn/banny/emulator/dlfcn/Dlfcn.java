package cn.banny.emulator.dlfcn;

import cn.banny.emulator.Memory;

public interface Dlfcn {

    int __ARM_NR_BASE = 0xf0000;

    int __NR_dlerror = __ARM_NR_BASE + 0x1000;
    int __NR_dlclose = __ARM_NR_BASE + 0x2000;
    int __NR_dlopen = __ARM_NR_BASE + 0x3000;
    int __NR_dladdr = __ARM_NR_BASE + 0x4000;
    int __NR_dlsym = __ARM_NR_BASE + 0x5000;

    /**
     * 返回0表示没有hook，否则返回hook以后的调用地址
     */
    long hook(String soName, String symbol);

    int dlopen(Memory memory, String filename, int flags);

    int dlerror();

    int dlsym(Memory memory, long handle, String symbol);

    int dlclose(Memory memory, long handle);
}
