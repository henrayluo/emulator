package cn.banny.emulator.dvm;

public class ArrayObject extends DvmObject<Object[]> {

    public ArrayObject(DvmClass objectType, Object[] value) {
        super(objectType, value);
    }

}
