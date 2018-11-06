package cn.banny.emulator.arm;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import unicorn.Unicorn;

public class UnixOs implements Os {

    @Override
    public int open(String path, int flags, int mode) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int close(int fd) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int writev(Unicorn unicorn, int fd, int iov, int iovcnt) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int munmap(int start, int length) {
        throw new UnsupportedOperationException();
    }

    @Override
    public long mmap(int start, int length, int prot, int flags, int fd, int offset) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int madvise(int addr, int len, int advise) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int gettimeofday(Unicorn unicorn, long tv, long tz) {
        Pointer tvp = null;
        Pointer tzp = null;
        try {
            if (tv != 0) {
                tvp = new Pointer(Native.malloc(8));
            }
            if (tz != 0) {
                tzp = new Pointer(Native.malloc(8));
            }
            int ret = CLibrary.INSTANCE.gettimeofday(tvp, tzp);
            if (ret == 0) {
                if (tvp != null) {
                    unicorn.mem_write(tv, tvp.getByteArray(0, 4));
                    unicorn.mem_write(tv + 4, tvp.getByteArray(4, 4));
                }

                if (tzp != null) {
                    unicorn.mem_write(tz, tzp.getByteArray(0, 4));
                    unicorn.mem_write(tz + 4, tzp.getByteArray(4, 4));
                }
            }
            return ret;
        } finally {
            if (tvp != null) {
                Native.free(Pointer.nativeValue(tvp));
            }
            if (tzp != null) {
                Native.free(Pointer.nativeValue(tzp));
            }
        }
    }
}
