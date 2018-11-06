package cn.banny.emulator.linux.file;

import cn.banny.emulator.Emulator;
import cn.banny.emulator.arm.ARM;
import cn.banny.emulator.linux.IO;
import com.sun.jna.Pointer;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import unicorn.Unicorn;

import java.io.*;
import java.util.Arrays;

public class SimpleFileIO extends AbstractFileIO implements FileIO {

    final File file;
    final String path;

    public SimpleFileIO(int oflags, File file, String path) {
        super(oflags);
        this.file = file;
        this.path = path;
    }

    @Override
    public void close() {
        IOUtils.closeQuietly(outputStream);
        IOUtils.closeQuietly(inputStream);

        if (debugStream != null) {
            try {
                debugStream.flush();
            } catch (IOException ignored) {
            }
        }
    }

    private OutputStream outputStream;

    @Override
    public int write(byte[] data) {
        try {
            if (outputStream == null) {
                outputStream = new FileOutputStream(file);
            }

            if (debugStream != null) {
                debugStream.write(data);
            }

            outputStream.write(data);
            outputStream.flush();
            return data.length;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    OutputStream debugStream;

    void setDebugStream(OutputStream stream) {
        this.debugStream = new BufferedOutputStream(stream);
    }

    private InputStream inputStream;

    @Override
    public int read(Unicorn unicorn, Pointer buffer, int count) {
        try {
            if (inputStream == null) {
                inputStream = new FileInputStream(file);
            }

            byte[] data = new byte[count];
            int read = inputStream.read(data);
            if (read <= 0) {
                return read;
            }

            if (read == count) {
                buffer.write(0, data, 0, data.length);
            } else {
                buffer.write(0, Arrays.copyOf(data, read), 0, read);
            }
            return read;
        } catch (IOException e) {
            throw new IllegalStateException();
        }
    }

    @Override
    public int fstat(Emulator emulator, Unicorn unicorn, Pointer stat) {
        int st_mode;
        if (IO.STDOUT.equals(file.getName())) {
            st_mode = IO.S_IFCHR | 0x777;
        } else {
            st_mode = 0;
        }
        /*
         * 0x18: st_uid
         * 0x1c: st_gid
         * 0x30: st_size
         * 0x38: st_blksize
         */
        stat.setInt(0x10, st_mode); // st_mode
        stat.setLong(0x30, file.length()); // st_size
        stat.setInt(0x38, (int) ARM.alignSize(file.length(), emulator.getPageAlign())); // 0x38
        return 0;
    }

    @Override
    public byte[] readFileToByteArray() throws IOException {
        return FileUtils.readFileToByteArray(this.file);
    }

    @Override
    public String toString() {
        return path;
    }

    @Override
    public int ioctl(long request, Pointer argp) {
        if (IO.STDOUT.equals(path) || IO.STDERR.equals(path)) {
            return 0;
        }

        return super.ioctl(request, argp);
    }

    @Override
    public FileIO dup2() {
        SimpleFileIO dup = new SimpleFileIO(0, file, path);
        dup.debugStream = debugStream;
        dup.op = op;
        dup.oflags = oflags;
        return dup;
    }
}
