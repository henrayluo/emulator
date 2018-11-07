package cn.banny.emulator.linux.file;

import com.sun.jna.Pointer;
import unicorn.Unicorn;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

public abstract class AbstractFileIO implements FileIO {

    private static final int F_GETFD = 1;
    private static final int F_SETFD = 2;
    private static final int F_GETFL = 3;
    private static final int F_SETFL = 4;

    private static final int FD_CLOEXEC = 1;

    int op;
    int oflags;

    AbstractFileIO(int oflags) {
        this.oflags = oflags;
    }

    @Override
    public int fcntl(int cmd, int arg) {
        switch (cmd) {
            case F_GETFD:
                return op;
            case F_SETFD:
                if (FD_CLOEXEC == arg) {
                    op |= FD_CLOEXEC;
                    return 0;
                }
                break;
            case F_GETFL:
                return oflags;
            case F_SETFL:
                if ((O_APPEND & arg) != 0) {
                    oflags |= O_APPEND;
                }
                if ((O_RDWR & arg) != 0) {
                    oflags |= O_RDWR;
                }
                if ((O_NONBLOCK & arg) != 0) {
                    oflags |= O_NONBLOCK;
                }
                return 0;
        }
        throw new UnsupportedOperationException();
    }

    @Override
    public int ioctl(Unicorn unicorn, long request, long argp) {
        throw new AbstractMethodError();
    }

    @Override
    public int connect(Pointer addr, int addrlen) {
        throw new AbstractMethodError();
    }

    @Override
    public int setsockopt(int level, int optname, Pointer optval, int optlen) {
        throw new AbstractMethodError();
    }

    @Override
    public int getsockopt(int level, int optname, Pointer optval, Pointer optlen) {
        throw new AbstractMethodError();
    }

    @Override
    public int getsockname(Pointer addr, Pointer addrlen) {
        throw new AbstractMethodError();
    }

    @Override
    public int sendto(byte[] data, int flags, Pointer dest_addr, int addrlen) {
        throw new AbstractMethodError();
    }

    @Override
    public int lseek(int offset, int whence) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int ftruncate(int length) {
        throw new AbstractMethodError();
    }

    @Override
    public int getpeername(Pointer addr, Pointer addrlen) {
        throw new AbstractMethodError();
    }

    @Override
    public int shutdown(int how) {
        throw new AbstractMethodError();
    }

    @Override
    public final int mmap(Unicorn unicorn, long addr, int length, int prot, Map<Long, Integer> memoryMap) throws IOException {
        byte[] data = getMmapData();
        unicorn.mem_map(addr, length, prot);
        memoryMap.put(addr, length);
        if (data.length <= length) {
            unicorn.mem_write(addr, data);
        } else {
            unicorn.mem_write(addr, Arrays.copyOf(data, length));
        }
        return (int) addr;
    }

    byte[] getMmapData() throws IOException {
        throw new AbstractMethodError();
    }

}
