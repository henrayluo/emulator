package cn.banny.emulator.wechat;

import cn.banny.emulator.Emulator;
import cn.banny.emulator.LibraryResolver;
import cn.banny.emulator.Memory;
import cn.banny.emulator.SvcMemory;
import cn.banny.emulator.arm.ARMEmulator;
import cn.banny.emulator.arm.AndroidARMEmulator;
import cn.banny.emulator.debugger.Debugger;
import cn.banny.emulator.dvm.*;
import cn.banny.emulator.linux.AndroidResolver;
import cn.banny.emulator.linux.Module;
import cn.banny.emulator.linux.ModuleListener;
import unicorn.UnicornException;

import java.io.File;
import java.io.IOException;

public class WeChatTest implements ModuleListener, Jni {

    private static LibraryResolver createLibraryResolver() {
        return new AndroidResolver(new File("android"), 19, "libc.so", "libdl.so", "liblog.so", "libm.so", "libz.so", "libstdc++.so", "libdvm.so", "libjavacore.so", "libnativehelper.so");
    }

    private static ARMEmulator createARMEmulator() {
        return new AndroidARMEmulator("com.tencent.mm");
    }

    @Override
    public void onLoaded(Emulator emulator, Module module) {
    }

    public static void main(String[] args) throws IOException {
        WeChatTest test = new WeChatTest();

        ARMEmulator emulator = createARMEmulator();
        final Memory memory = emulator.getMemory();
        memory.setLibraryResolver(createLibraryResolver());
        memory.setCallInitFunction();
        memory.setModuleListener(test);
        final File elfFile = new File("src/test/resources/example_binaries/libwechatnormsg.so");
        emulator.setWorkDir(elfFile.getParentFile());
        Module module = emulator.loadLibrary(elfFile, true);

        Debugger debugger = emulator.attach();
        // debugger.addBreakPoint(module, 0x00040EEC);
        // debugger.addBreakPoint(null, 0xffff0fdc);
        // debugger.addBreakPoint(module, 0x00f799);

        SvcMemory svcMemory = emulator.getSvcMemory();
        VM vm = new DalvikVM(svcMemory, test);

        // emulator.traceCode(module.base, module.base + module.size);
        module.callFunction(emulator, "JNI_OnLoad", vm.getJavaVM(), null);
        System.out.println("Call JNI_OnLoad finished");
        DvmClass Normsg$J2CBridge = vm.findClass("com/tencent/mm/plugin/normsg/Normsg$J2CBridge");
        Number ret = module.callFunction(emulator, 0x40da8 + 1, vm.getJNIEnv(), Normsg$J2CBridge.hashCode(), 0, 0, 0)[0];
        System.out.println("ret=0x" + (ret.intValue() & 0xffffffffL));
    }

    @Override
    public DvmObject getStaticObjectField(DvmClass dvmClass, String signature) {
        switch (signature) {
            case "android/provider/Settings$Secure->ALLOW_MOCK_LOCATION:Ljava/lang/String;":
                return new DvmObject(dvmClass, "mock_location");
        }
        throw new UnicornException();
    }

    @Override
    public int callStaticBooleanMethodV(String signature) {
        switch (signature) {
            case "android/os/Debug->isDebuggerConnected()Z":
                return VM.JNI_FALSE;
        }
        return VM.JNI_ERR;
    }

    @Override
    public int callStaticIntMethodV(String signature, VaList vaList) {
        switch (signature) {
            case "android/provider/Settings$Secure->getInt(Landroid/content/ContentResolver;Ljava/lang/String;I)I":
                DvmObject object = vaList.getObject(4);
                String name = (String) object.getValue();
                switch (name) {
                    case "mock_location":
                        return 0;
                    default:
                        int def = vaList.getInt(8);
                        System.err.println("signature=" + signature + ", return default value: " + def);
                        return def;
                }
            default:
                throw new UnicornException();
        }
    }

    @Override
    public DvmObject callObjectMethodV(DvmClass dvmClass, String signature, String methodName, String args) {
        switch (signature) {
            case "android/app/ActivityThread->getClassLoader()Ljava/lang/ClassLoader;":
            case "android/app/ActivityThread->getContentResolver()Landroid/content/ContentResolver;":
            case "android/app/ActivityThread->getPackageManager()Landroid/content/pm/PackageManager;":
                return new DvmObject(dvmClass, methodName + args);
            default:
                throw new UnicornException();
        }
    }

    @Override
    public DvmObject callStaticObjectMethodV(DvmClass dvmClass, String signature, String methodName, String args) {
        switch (signature) {
            case "android/app/ActivityThread->currentActivityThread()Landroid/app/ActivityThread;":
            case "android/app/ActivityThread->currentApplication()Landroid/app/Application;":
                return new DvmObject(dvmClass, methodName + args);
            default:
                throw new UnicornException();
        }
    }
}
