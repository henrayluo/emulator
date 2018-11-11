package cn.banny.emulator.dlfcn;

public interface Dlfcn {

    /**
     * 返回0表示没有hook，否则返回hook以后的调用地址
     */
    long hook(String soName, String symbol);
}
