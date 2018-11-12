package cn.banny.emulator.dvm;

import cn.banny.emulator.pointer.UnicornPointer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import unicorn.UnicornException;

class DvmMethod {

    private static final Log log = LogFactory.getLog(DvmMethod.class);

    private final DvmClass dvmClass;
    private final String methodName;
    private final String args;

    DvmMethod(DvmClass dvmClass, String methodName, String args) {
        this.dvmClass = dvmClass;
        this.methodName = methodName;
        this.args = args;
    }

    int callStaticObjectMethodV() {
        String signature = dvmClass.className + "->" + methodName + args;
        int index = args.lastIndexOf('L');
        if (index == -1) {
            throw new UnicornException("Illegal args: " + args);
        }
        String objectType = args.substring(index + 1, args.length() - 1);
        log.debug("CallStaticObjectMethodV objectType=" + objectType + ", signature=" + signature);
        DvmObject object;
        switch (signature) {
            case "android/app/ActivityThread->currentActivityThread()Landroid/app/ActivityThread;":
            case "android/app/ActivityThread->currentApplication()Landroid/app/Application;":
                object = new DvmObject(dvmClass, null);
                break;
            default:
                throw new UnicornException();
        }

        long hash = object.hashCode() & 0xffffffffL;
        dvmClass.vm.objectMap.put(hash, object);
        return (int) hash;
    }

    int callObjectMethodV() {
        String signature = dvmClass.className + "->" + methodName + args;
        int index = args.lastIndexOf(")L");
        if (index == -1) {
            throw new UnicornException("Illegal args: " + args);
        }
        String objectType = args.substring(index + 2, args.length() - 1);
        log.debug("CallObjectMethodV objectType=" + objectType + ", signature=" + signature);
        DvmObject object;
        switch (signature) {
            case "android/app/ActivityThread->getClassLoader()Ljava/lang/ClassLoader;":
            case "android/app/ActivityThread->getContentResolver()Landroid/content/ContentResolver;":
                object = new DvmObject(dvmClass, null);
                break;
            default:
                throw new UnicornException();
        }

        long hash = object.hashCode() & 0xffffffffL;
        dvmClass.vm.objectMap.put(hash, object);
        return (int) hash;
    }

    int callStaticIntMethodV(UnicornPointer va_list) {
        String signature = dvmClass.className + "->" + methodName + args;
        log.debug("callStaticIntMethodV signature=" + signature);
        switch (signature) {
            case "android/provider/Settings$Secure->getInt(Landroid/content/ContentResolver;Ljava/lang/String;I)I":
                DvmObject object = dvmClass.vm.objectMap.get(va_list.getPointer(4).peer);
                String name = (String) object.value;
                if ("mock_location".equals(name)) {
                    return 0;
                }
                throw new UnicornException();
            default:
                throw new UnicornException();
        }
    }
}
