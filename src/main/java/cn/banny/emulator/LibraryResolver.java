package cn.banny.emulator;

import java.io.File;

public interface LibraryResolver {

    File resolveLibrary(String soName);

    File resolveFile(File workDir, String path, boolean create);

}
