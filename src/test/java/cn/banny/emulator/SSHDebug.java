package cn.banny.emulator;

import java.io.File;
import java.io.IOException;

public class SSHDebug {

    public static void main(String[] args) throws IOException {
        RunExecutable.run(new File("../example_binaries/ssh"), null, "-p", "4446", "root@p.gzmtx.cn");
    }

}
