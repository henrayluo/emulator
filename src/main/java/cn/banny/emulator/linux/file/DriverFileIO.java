package cn.banny.emulator.linux.file;

import cn.banny.emulator.Emulator;
import com.sun.jna.Pointer;
import unicorn.Unicorn;

public class DriverFileIO extends AbstractFileIO implements FileIO {

    public static DriverFileIO create(int oflags, String pathname) {
        if ("/dev/urandom".equals(pathname) || "/dev/random".equals(pathname)) {
            return new RandomFileIO(pathname);
        }
        if ("/dev/alarm".equals(pathname) || "/dev/null".equals(pathname)) {
            return new DriverFileIO(oflags, pathname);
        }
        if ("/dev/ashmem".equals(pathname)) {
            return new Ashmem(oflags, pathname);
        }
        return null;
    }

    private final String path;

    DriverFileIO(int oflags, String path) {
        super(oflags);
        this.path = path;
    }

    @Override
    public void close() {
    }

    @Override
    public int write(byte[] data) {
        throw new AbstractMethodError();
    }

    @Override
    public int read(Unicorn unicorn, Pointer buffer, int count) {
        throw new AbstractMethodError();
    }

    @Override
    public int fstat(Emulator emulator, Unicorn unicorn, Pointer stat) {
        throw new AbstractMethodError();
    }

    @Override
    public int ioctl(Unicorn unicorn, long request, long argp) {
        if ("/dev/alarm".equals(path)) {
            return 0;
        }

        return super.ioctl(unicorn, request, argp);
    }

    @Override
    public FileIO dup2() {
        throw new AbstractMethodError();
    }

    @Override
    public String toString() {
        return path;
    }
}
