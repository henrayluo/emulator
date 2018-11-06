package cn.banny.emulator.struct;

import com.sun.jna.Structure;

import java.util.Arrays;
import java.util.List;

public class Libcore {

    public static class TimeVal extends Structure {
        public int tv_sec;
        public int tv_usec;
        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("tv_sec", "tv_usec");
        }
    }

    public static class TimeZone extends Structure {
        public int tz_minuteswest;
        public int tz_dsttime;
        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("tz_minuteswest", "tz_dsttime");
        }
    }

}
