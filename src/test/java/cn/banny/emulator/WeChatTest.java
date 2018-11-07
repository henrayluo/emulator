package cn.banny.emulator;

import cn.banny.emulator.arm.AndroidARMEmulator;
import cn.banny.emulator.linux.AndroidResolver;
import cn.banny.emulator.linux.Module;
import cn.banny.emulator.pointer.UnicornPointer;
import com.sun.jna.Pointer;
import unicorn.Unicorn;

import java.io.File;

public class WeChatTest extends EmulatorTest {

    @Override
    protected LibraryResolver createLibraryResolver() {
        return new AndroidResolver(new File("android"), 19, "libc.so", "libdl.so", "liblog.so", "libm.so", "libz.so", "libstdc++.so", "libdvm.so", "libjavacore.so", "libnativehelper.so");
    }

    @Override
    protected Emulator createARMEmulator() {
        return new AndroidARMEmulator("com.tencent.mm");
    }

    public void testNorMsg() throws Exception {
        final Memory memory = emulator.getMemory();
        final Unicorn unicorn = emulator.getUnicorn();
        memory.setCallInitFunction();

        Module dvm = memory.loadLibrary("libdvm.so");
        Pointer p_vm = UnicornPointer.pointer(unicorn, memory.allocateStack(4));
        assertNotNull(p_vm);
        p_vm.setPointer(0, null);

        Pointer p_env = UnicornPointer.pointer(unicorn, memory.allocateStack(4));
        assertNotNull(p_env);
        p_env.setPointer(0, null);

        Pointer optionString = UnicornPointer.pointer(unicorn, memory.allocateStack(64));
        assertNotNull(optionString);
        optionString.setString(0, "-Djava.class.path=.");

        Pointer bootclasspath = UnicornPointer.pointer(unicorn, memory.allocateStack(512));
        assertNotNull(bootclasspath);
        bootclasspath.setString(0, "-Xbootclasspath:/system/framework/core.jar:/system/framework/conscrypt.jar:/system/framework/okhttp.jar:/system/framework/core-junit.jar:/system/framework/bouncycastle.jar:/system/framework/ext.jar:/system/framework/framework.jar:/system/framework/framework2.jar:/system/framework/telephony-common.jar:/system/framework/voip-common.jar:/system/framework/mms-common.jar:/system/framework/android.policy.jar:/system/framework/services.jar:/system/framework/apache-xml.jar:/system/framework/webviewchromium.jar:/system/framework/telephony-msim.jar");

        Pointer options = UnicornPointer.pointer(unicorn, memory.allocateStack(16));
        assertNotNull(options);
        Pointer option1 = options.share(0);
        option1.setPointer(0, optionString);
        option1.setPointer(4, null); // extraInfo
        Pointer option2 = options.share(8);
        option2.setPointer(0, bootclasspath);
        option2.setPointer(4, null);

        Pointer vm_args = UnicornPointer.pointer(unicorn, memory.allocateStack(16));
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

        final File elfFile = new File("example_binaries/libwechatnormsg.so");
        Module module = emulator.loadLibrary(elfFile, true);
    }

}
