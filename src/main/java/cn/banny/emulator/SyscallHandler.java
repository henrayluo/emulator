package cn.banny.emulator;

import cn.banny.emulator.dlfcn.Dlfcn;
import cn.banny.emulator.linux.file.FileIO;
import unicorn.InterruptHook;

import java.util.Map;
import java.util.TreeMap;

/**
 * syscall handler
 * Created by zhkl0228 on 2017/5/9.
 */

public abstract class SyscallHandler implements InterruptHook {

    public final Map<Integer, FileIO> fdMap = new TreeMap<>();

    public final Dlfcn dlfcn;

    public SyscallHandler(Dlfcn dlfcn) {
        this.dlfcn = dlfcn;
    }

    public final int getMinFd() {
        int last_fd = -1;
        for (int fd : fdMap.keySet()) {
            if (last_fd + 1 == fd) {
                last_fd = fd;
            } else {
                break;
            }
        }
        return last_fd + 1;
    }

}
