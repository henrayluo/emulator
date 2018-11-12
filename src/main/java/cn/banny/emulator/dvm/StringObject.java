package cn.banny.emulator.dvm;

import cn.banny.emulator.pointer.UnicornPointer;

public class StringObject extends DvmObject<String> {

    public StringObject(DvmClass objectType, String value) {
        super(objectType, value);
    }

    UnicornPointer utf;

}
