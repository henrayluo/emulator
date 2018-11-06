package cn.banny.emulator.arm;

import cn.banny.emulator.struct.Libcore;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

/**
 * libc
 * Created by zhkl0228 on 2017/5/11.
 */
public interface CLibrary extends Library {

    CLibrary INSTANCE = (CLibrary) Native.loadLibrary("c", CLibrary.class);

    int madvise(Pointer addr, int len, int advice);

    Pointer mmap(Pointer addr, int len, int prot, int flags, int fd, int offset);

    int munmap(Pointer addr, int len);

    int open(String path, int flags, int mode);

    int close(int fd);

    int gettimeofday(Pointer tv, Pointer tz);
    int gettimeofday(Libcore.TimeVal tv, Libcore.TimeZone tz);

    Pointer malloc(int size);

    void free(Pointer ptr);

}
