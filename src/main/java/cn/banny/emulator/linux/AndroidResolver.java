package cn.banny.emulator.linux;

import cn.banny.emulator.LibraryResolver;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class AndroidResolver implements LibraryResolver {

    private final File androidDir;
    private final List<String> needed;

    public AndroidResolver(File androidDir, int sdk, String...needed) {
        this.androidDir = new File(androidDir, "sdk" + sdk);
        this.needed = needed == null ? null : Arrays.asList(needed);
    }

    @Override
    public File resolveLibrary(String soName) {
        if (needed == null) {
            return null;
        }
        File file = new File(androidDir, "lib/" + soName);
        if (needed.isEmpty() || needed.contains(soName)) {
            return file;
        }
        return null;
    }

    @Override
    public File resolveFile(String path) {
        if (IO.STDOUT.equals(path) || IO.STDERR.equals(path)) {
            try {
                File io = new File(androidDir, "../io");
                if (!io.exists() && !io.mkdir()) {
                    throw new IOException("mkdir failed: " + io);
                }
                File stdio = new File(io, path);
                if (!stdio.exists() && !stdio.createNewFile()) {
                    throw new IOException("create new file failed: " + stdio);
                }
                return stdio;
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
        if (path.startsWith("/dev/log/")) {
            try {
                File log = new File(androidDir, ".." + path);
                File logDir = log.getParentFile();
                if (!logDir.exists() && !logDir.mkdirs()) {
                    throw new IOException("mkdirs failed: " + logDir);
                }
                if (!log.exists() && !log.createNewFile()) {
                    throw new IOException("create new file failed: " + log);
                }
                return log;
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        File file = new File(androidDir, path);
        if (file.canRead()) {
            return file;
        }
        return null;
    }
}
