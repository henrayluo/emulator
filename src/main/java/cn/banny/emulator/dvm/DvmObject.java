package cn.banny.emulator.dvm;

public class DvmObject implements Hashable {

    final DvmClass objectType;
    private final Object value;

    public DvmObject(DvmClass objectType, Object value) {
        this.objectType = objectType;
        this.value = value;
    }

    public Object getValue() {
        return value;
    }
}
