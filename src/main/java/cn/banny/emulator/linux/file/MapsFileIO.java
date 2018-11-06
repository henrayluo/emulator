package cn.banny.emulator.linux.file;

import cn.banny.auxiliary.Inspector;
import cn.banny.emulator.Emulator;
import cn.banny.emulator.linux.MemRegion;
import cn.banny.emulator.linux.Module;
import com.sun.jna.Pointer;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import unicorn.Unicorn;
import unicorn.UnicornConst;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class MapsFileIO extends AbstractFileIO implements FileIO {

    private static final Log log = LogFactory.getLog(MapsFileIO.class);

    private final byte[] maps;
    private final InputStream inputStream;

    public MapsFileIO(int oflags, Collection<Module> modules) {
        super(oflags);

        List<MemRegion> list = new ArrayList<>(modules.size());
        for (Module module : modules) {
            list.addAll(module.getRegions());
        }
        Collections.sort(list);
        StringBuilder builder = new StringBuilder();
        for (MemRegion memRegion : list) {
            builder.append(String.format("%08x-%08x", memRegion.begin, memRegion.end)).append(' ');
            if ((memRegion.perms & UnicornConst.UC_PROT_READ) != 0) {
                builder.append('r');
            } else {
                builder.append('-');
            }
            if ((memRegion.perms & UnicornConst.UC_PROT_WRITE) != 0) {
                builder.append('w');
            } else {
                builder.append('-');
            }
            if ((memRegion.perms & UnicornConst.UC_PROT_EXEC) != 0) {
                builder.append('x');
            } else {
                builder.append('-');
            }
            builder.append("p ");
            builder.append(String.format("%08x", memRegion.offset));
            builder.append(" b3:19 0");
            for (int i = 0; i < 10; i++) {
                builder.append(' ');
            }
            builder.append(memRegion.name);
            builder.append('\n');
        }
        builder.append("ffff0000-ffff1000 r-xp 00000000 00:00 0          [vectors]");
        if (log.isDebugEnabled()) {
            log.debug("\n" + builder.toString());
        }

        maps = builder.toString().getBytes();
        inputStream = new ByteArrayInputStream(maps);
    }

    @Override
    public void close() {
        IOUtils.closeQuietly(inputStream);
    }

    @Override
    public int write(byte[] data) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int read(Unicorn unicorn, Pointer buffer, int count) {
        byte[] data = new byte[count];
        try {
            int read = inputStream.read(data, 0, count);
            if (read > 0) {
                byte[] mem = Arrays.copyOf(data, read);
                if (log.isDebugEnabled()) {
                    Inspector.inspect(mem, "read buffer=" + buffer + ", count=" + count + ", read=" + read);
                }
                buffer.write(0, mem, 0, mem.length);
            }
            return read;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public int fstat(Emulator emulator, Unicorn unicorn, Pointer stat) {
        stat.setLong(0x30, maps.length); // st_size
        return 0;
    }

    @Override
    public byte[] readFileToByteArray() {
        return maps;
    }

    @Override
    public FileIO dup2() {
        throw new AbstractMethodError();
    }

    @Override
    public String toString() {
        return "/proc/self/maps";
    }
}
