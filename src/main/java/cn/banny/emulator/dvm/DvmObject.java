package cn.banny.emulator.dvm;

class DvmObject {

    final DvmClass objectType;
    final Object value;

    DvmObject(DvmClass objectType, Object value) {
        this.objectType = objectType;
        this.value = value;
    }

}
