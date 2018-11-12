package cn.banny.emulator.dvm;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import unicorn.UnicornException;

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
        int index = fieldType.lastIndexOf('L');
        if (index == -1) {
            throw new UnicornException("Illegal fieldType: " + fieldType);
        }
        String objectType = fieldType.substring(index + 1, fieldType.length() - 1);
        log.debug("getStaticObjectField dvmClass=" + dvmClass + ", fieldName=" + fieldName + ", fieldType=" + fieldType + ", signature=" + signature + ", objectType=" + objectType);
        DvmObject object;
        switch (signature) {
            case "android/provider/Settings$Secure->ALLOW_MOCK_LOCATION:Ljava/lang/String;":
                object = new DvmObject(dvmClass, "mock_location");
                break;
            default:
                throw new UnicornException();
        }
        long hash = object.hashCode() & 0xffffffffL;
        dvmClass.vm.objectMap.put(hash, object);
        return (int) hash;
    }

}
