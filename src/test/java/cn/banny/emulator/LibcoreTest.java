package cn.banny.emulator;

import cn.banny.emulator.arm.CLibrary;
import cn.banny.emulator.struct.Libcore;
import junit.framework.TestCase;

public class LibcoreTest extends TestCase {

    public void test_gettimeofday() {
        Libcore.TimeVal tv = new Libcore.TimeVal();
        Libcore.TimeZone tz = new Libcore.TimeZone();
        int ret = CLibrary.INSTANCE.gettimeofday(tv, tz);
        System.out.println("tv_sec=" + tv.tv_sec + ", tv_usec=" + tv.tv_usec + ", tz_minuteswest=" + tz.tz_minuteswest + ", tz_dsttime=" + tz.tz_dsttime + ", ret=" + ret);
        tz.clear();
        tv.clear();
    }

}
