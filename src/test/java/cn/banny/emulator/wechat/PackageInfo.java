package cn.banny.emulator.wechat;

import cn.banny.emulator.dvm.DvmClass;
import cn.banny.emulator.dvm.DvmObject;

class PackageInfo extends DvmObject<String> {

    PackageInfo(DvmClass objectType, String packageName) {
        super(objectType, packageName);
    }

}
