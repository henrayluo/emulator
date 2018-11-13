package cn.banny.emulator.dvm;

public class ArrayObject extends DvmObject<DvmObject[]> {

    public ArrayObject(DvmClass objectType, DvmObject...value) {
        super(objectType, value);
    }

}
