package cn.banny.emulator.dvm;

public abstract class DvmObject<T> implements Hashable {

    public final DvmClass objectType;
    private final T value;

    public DvmObject(DvmClass objectType, T value) {
        this.objectType = objectType;
        this.value = value;
    }

    public T getValue() {
        return value;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
                "value=" + value +
                '}';
    }

}
