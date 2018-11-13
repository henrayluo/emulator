package cn.banny.emulator.wechat;

import cn.banny.emulator.dvm.DvmClass;
import cn.banny.emulator.dvm.DvmObject;

class Signature extends DvmObject<String> {

    Signature(DvmClass objectType, String packageName) {
        super(objectType, packageName);
    }

}
