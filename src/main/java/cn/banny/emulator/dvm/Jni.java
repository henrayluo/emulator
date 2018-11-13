package cn.banny.emulator.dvm;

public interface Jni {

    DvmObject getStaticObjectField(DvmClass dvmClass, String signature);

    int getStaticIntField(DvmClass dvmClass, String signature);

    int callStaticBooleanMethodV(String signature);

    int callStaticIntMethodV(String signature, VaList vaList);

    DvmObject callObjectMethodV(DvmClass dvmClass, String signature, String methodName, String args, VaList vaList);

    DvmObject callStaticObjectMethodV(DvmClass dvmClass, String signature, String methodName, String args);

    int getObjectField(VM vm, DvmObject dvmObject, String signature);
}
