package cn.banny.emulator.dvm;

import cn.banny.emulator.pointer.UnicornPointer;

public class VaList {

    private final DalvikVM vm;
    private final UnicornPointer va_list;

    VaList(DalvikVM vm, UnicornPointer va_list) {
        this.vm = vm;
        this.va_list = va_list;
    }

    public DvmObject getObject(int offset) {
        return vm.objectMap.get(va_list.getPointer(offset).peer);
    }

    public int getInt(int offset) {
        return va_list.getInt(offset);
    }

}
