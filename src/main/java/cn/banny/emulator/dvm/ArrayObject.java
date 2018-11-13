package cn.banny.emulator.dvm;

public class ArrayObject extends DvmObject<DvmObject[]> {

    public ArrayObject(DvmObject...value) {
        super(null, value);
    }

}
