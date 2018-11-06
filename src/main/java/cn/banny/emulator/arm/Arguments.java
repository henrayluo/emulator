package cn.banny.emulator.arm;

import cn.banny.emulator.ByteArrayNumber;
import cn.banny.emulator.StringNumber;
import cn.banny.utils.Hex;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import unicorn.Unicorn;

import java.util.ArrayList;
import java.util.List;

class Arguments {

    private static final Log log = LogFactory.getLog(Arguments.class);

    final long sp;
    final Object[] args;

    Arguments(Unicorn unicorn, long sp, Number[] args) {
        int i = 0;
        while (args != null && i < args.length) {
            if (args[i] instanceof StringNumber) {
                StringNumber str = (StringNumber) args[i];
                int size = ARM.writeCString(unicorn, sp, str.value);
                if (log.isDebugEnabled()) {
                    log.debug("map arg" + (i+1) + ": 0x" + Long.toHexString(sp - size) + " -> " + args[i] + ", size=" + size);
                }
                sp -= size;
                args[i] = sp;
                pointers.add(sp);
            } else if (args[i] instanceof ByteArrayNumber) {
                ByteArrayNumber array = (ByteArrayNumber) args[i];
                int size = ARM.writeBytes(unicorn, sp, array.value);
                if (log.isDebugEnabled()) {
                    log.debug("map arg" + (i+1) + ": 0x" + Long.toHexString(sp - size) + " -> " + Hex.encodeHexString(array.value) + ", size=" + size);
                }
                sp -= size;
                args[i] = sp;
                pointers.add(sp);
            }
            i++;
        }

        this.sp = sp;
        this.args = args;
    }

    final List<Number> pointers = new ArrayList<>(10);

}
