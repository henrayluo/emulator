package cn.banny.emulator.dvm;

import cn.banny.emulator.pointer.UnicornPointer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import unicorn.UnicornException;

class DvmMethod implements Hashable {

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
        log.debug("CallStaticObjectMethodV signature=" + signature);
        DvmObject object = dvmClass.vm.jni.callStaticObjectMethodV(dvmClass, signature, methodName, args);
        return dvmClass.vm.addObject(object);
    }

    int callObjectMethodV(VaList vaList) {
        String signature = dvmClass.className + "->" + methodName + args;
        log.debug("CallObjectMethodV signature=" + signature);
        DvmObject object = dvmClass.vm.jni.callObjectMethodV(dvmClass, signature, methodName, args, vaList);
        return dvmClass.vm.addObject(object);
    }

    int callStaticIntMethodV(VaList vaList) {
        String signature = dvmClass.className + "->" + methodName + args;
        log.debug("callStaticIntMethodV signature=" + signature);
        return dvmClass.vm.jni.callStaticIntMethodV(signature, vaList);
    }

    int callStaticBooleanMethodV() {
        String signature = dvmClass.className + "->" + methodName + args;
        log.debug("callStaticBooleanMethodV signature=" + signature);
        int ret = dvmClass.vm.jni.callStaticBooleanMethodV(signature);
        if (ret == VM.JNI_ERR) {
            throw new UnicornException();
        } else {
            return ret;
        }
    }
}
