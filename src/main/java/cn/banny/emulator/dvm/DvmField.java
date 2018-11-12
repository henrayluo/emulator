package cn.banny.emulator.dvm;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

class DvmField {

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

}
