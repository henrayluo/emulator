package cn.banny.emulator;

import cn.banny.emulator.arm.ARM;
import cn.banny.emulator.linux.AndroidResolver;
import cn.banny.emulator.linux.Module;
import com.sun.jna.Pointer;
import unicorn.Unicorn;

import java.io.File;

public class McToTest extends EmulatorTest {

    @Override
    protected LibraryResolver createLibraryResolver() {
        return new AndroidResolver(new File("android"), 19, "libc.so", "libz.so", "libm.so", "libdl.so", "liblog.so", "libjavacore.so", "libnativehelper.so");
    }

    public void testMcTo() throws Exception {
        long start = System.currentTimeMillis();
        emulator.getMemory().setCallInitFunction();
        Unicorn unicorn = emulator.getUnicorn();
        Module module = emulator.loadLibrary(new File("mcto/libmcto_media_player.so"));
        System.err.println("load offset=" + (System.currentTimeMillis() - start) + "ms");
        start = System.currentTimeMillis();
        // emulator.traceCode();
        Number[] numbers = module.callFunction(emulator, 0x249bc8 + 1, "/vps?tvid=11949478009&vid=7b23569cbed511dd58bcd6ce9ddd7b42&v=0&qypid=11949478009_unknown&src=02022001010000000000&tm=1519712402&k_tag=1&k_uid=359125052784388&bid=1&pt=0&d=1&s=0&rs=1&dfp=1413357b5efa4a4130b327995c377ebb38fbd916698ed95a28f56939e9d8825592&k_ver=9.0.0&k_ft1=859834543&k_err_retries=0&qd_v=1");
        long address = numbers[0].intValue() & 0xffffffffL;
        System.out.println("ret=" + ARM.readCString(unicorn, address));
        System.err.println("eFunc offset=" + (System.currentTimeMillis() - start) + "ms");
    }

    public void testVM() throws Exception {
        final Memory memory = emulator.getMemory();
        memory.setCallInitFunction();

        Module dvm = memory.load(new File("android/sdk19/lib/libdvm.so"));
        Pointer p_vm = memory.allocateStack(4);
        assertNotNull(p_vm);
        p_vm.setPointer(0, null);

        Pointer p_env = memory.allocateStack(4);
        assertNotNull(p_env);
        p_env.setPointer(0, null);

        Pointer optionString = memory.writeStackString("-Djava.class.path=.");

        Pointer bootclasspath = memory.writeStackString("-Xbootclasspath:/system/framework/core.jar:/system/framework/conscrypt.jar:/system/framework/okhttp.jar:/system/framework/core-junit.jar:/system/framework/bouncycastle.jar:/system/framework/ext.jar:/system/framework/framework.jar:/system/framework/framework2.jar:/system/framework/telephony-common.jar:/system/framework/voip-common.jar:/system/framework/mms-common.jar:/system/framework/android.policy.jar:/system/framework/services.jar:/system/framework/apache-xml.jar:/system/framework/webviewchromium.jar:/system/framework/telephony-msim.jar");

        Pointer options = memory.allocateStack(16);
        assertNotNull(options);
        Pointer option1 = options.share(0);
        option1.setPointer(0, optionString);
        option1.setPointer(4, null); // extraInfo
        Pointer option2 = options.share(8);
        option2.setPointer(0, bootclasspath);
        option2.setPointer(4, null);

        Pointer vm_args = memory.allocateStack(16);
        assertNotNull(vm_args);
        vm_args.setInt(0, 0x00010002); // version
        vm_args.setInt(4, 2); // nOptions
        vm_args.setPointer(8, options); // options
        vm_args.setInt(0xc, 1); // ignoreUnrecognized

        // emulator.traceCode();
        Number ret = dvm.callFunction(emulator, "JNI_CreateJavaVM", p_vm, p_env, vm_args)[0];
        assertEquals(0, ret.intValue());
        Pointer vm = p_vm.getPointer(0);
        assertNotNull(vm);
        Pointer env = p_env.getPointer(0);
        assertNotNull(env);
        System.out.println("vm=" + vm + ", env=" + env);
    }

}
