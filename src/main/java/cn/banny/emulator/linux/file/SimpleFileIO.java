package cn.banny.emulator.linux.file;

import cn.banny.auxiliary.Inspector;
import cn.banny.emulator.Emulator;
import cn.banny.emulator.arm.ARM;
import cn.banny.emulator.linux.IO;
import com.sun.jna.Pointer;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import unicorn.Unicorn;

import java.io.*;
import java.util.Arrays;

public class SimpleFileIO extends AbstractFileIO implements FileIO {

    private static final Log log = LogFactory.getLog(SimpleFileIO.class);

    final File file;
    final String path;
    private final RandomAccessFile randomAccessFile;

    public SimpleFileIO(int oflags, File file, String path) {
        super(oflags);
        this.file = file;
        this.path = path;

        try {
            randomAccessFile = new RandomAccessFile(file, "rws");
        } catch (FileNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void close() {
        IOUtils.closeQuietly(randomAccessFile);

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
            if (debugStream != null) {
                debugStream.write(data);
                debugStream.flush();
            }

            if (outputStream == null) {
                outputStream = createFileOutputStream(file);
            }

            outputStream.write(data);
            outputStream.flush();
            return data.length;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    OutputStream createFileOutputStream(File file) throws FileNotFoundException {
        return new FileOutputStream(file);
    }

    OutputStream debugStream;

    void setDebugStream(OutputStream stream) {
        this.debugStream = new BufferedOutputStream(stream);
    }

    @Override
    public int read(Unicorn unicorn, Pointer buffer, int count) {
        try {
            byte[] data = new byte[count];
            int read = randomAccessFile.read(data);
            if (read <= 0) {
                return read;
            }

            final byte[] buf;
            if (read == count) {
                buf = data;
            } else {
                buf = Arrays.copyOf(data, read);
            }
            if (log.isDebugEnabled() && buf.length < 10240) {
                Inspector.inspect(buf, "read " + file);
            }
            buffer.write(0, buf, 0, buf.length);
            return buf.length;
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
    byte[] getMmapData(int offset, int length) throws IOException {
        randomAccessFile.seek(offset);
        ByteArrayOutputStream baos = new ByteArrayOutputStream(length);
        byte[] buf = new byte[10240];
        do {
            int count = length - baos.size();
            if (count == 0) {
                break;
            }

            if (count > buf.length) {
                count = buf.length;
            }

            int read = randomAccessFile.read(buf, 0, count);
            if (read == -1) {
                break;
            }

            baos.write(buf, 0, read);
        } while (true);
        return baos.toByteArray();
    }

    @Override
    public String toString() {
        return path;
    }

    @Override
    public int ioctl(Unicorn unicorn, long request, long argp) {
        if (IO.STDOUT.equals(path) || IO.STDERR.equals(path)) {
            return 0;
        }

        return super.ioctl(unicorn, request, argp);
    }

    @Override
    public FileIO dup2() {
        SimpleFileIO dup = new SimpleFileIO(oflags, file, path);
        dup.debugStream = debugStream;
        dup.op = op;
        dup.oflags = oflags;
        return dup;
    }

    @Override
    public int lseek(int offset, int whence) {
        try {
            switch (whence) {
                case SEEK_SET:
                    randomAccessFile.seek(offset);
                    return (int) randomAccessFile.getFilePointer();
                case SEEK_CUR:
                    randomAccessFile.seek(randomAccessFile.getFilePointer() + offset);
                    return (int) randomAccessFile.getFilePointer();
                case SEEK_END:
                    randomAccessFile.seek(randomAccessFile.length() - offset);
                    return (int) randomAccessFile.getFilePointer();
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        return super.lseek(offset, whence);
    }

    @Override
    public int llseek(long offset_high, long offset_low, Pointer result, int whence) {
        try {
            long offset = (offset_high<<32) | offset_low;
            switch (whence) {
                case SEEK_SET:
                    randomAccessFile.seek(offset);
                    result.setLong(0, randomAccessFile.getFilePointer());
                    return 0;
                case SEEK_END:
                    randomAccessFile.seek(randomAccessFile.length() - offset);
                    result.setLong(0, randomAccessFile.getFilePointer());
                    return 0;
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        return super.llseek(offset_high, offset_low, result, whence);
    }
}
