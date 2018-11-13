package cn.banny.emulator.dvm;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

class DvmField implements Hashable {

    private static final Log log = LogFactory.getLog(DvmField.class);

    private final DvmClass dvmClass;
    private final String fieldName;
    private final String fieldType;

    DvmField(DvmClass dvmClass, String fieldName, String fieldType) {
        this.dvmClass = dvmClass;
        this.fieldName = fieldName;
        this.fieldType = fieldType;
    }

    int getStaticObjectField() {
        String signature = dvmClass.className + "->" + fieldName + ":" + fieldType;
        log.debug("getStaticObjectField dvmClass=" + dvmClass + ", fieldName=" + fieldName + ", fieldType=" + fieldType + ", signature=" + signature);
        DvmObject object = dvmClass.vm.jni.getStaticObjectField(dvmClass, signature);
        return dvmClass.vm.addObject(object);
    }

    int getStaticIntField() {
        String signature = dvmClass.className + "->" + fieldName + ":" + fieldType;
        log.debug("getStaticIntField dvmClass=" + dvmClass + ", fieldName=" + fieldName + ", fieldType=" + fieldType + ", signature=" + signature);
        return dvmClass.vm.jni.getStaticIntField(dvmClass, signature);
    }

    int getObjectField(DvmObject dvmObject) {
        String signature = dvmClass.className + "->" + fieldName + ":" + fieldType;
        log.debug("getObjectField dvmObject=" + dvmObject + ", fieldName=" + fieldName + ", fieldType=" + fieldType + ", signature=" + signature);
        return dvmClass.vm.jni.getObjectField(dvmClass.vm, dvmObject, signature);
    }

}
