package cn.banny.emulator.dvm;

public class PlaceholderObject extends DvmObject<String> {

    public PlaceholderObject(DvmClass objectType, String methodName, String args) {
        super(objectType, methodName + args);
    }

}
