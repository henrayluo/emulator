package cn.banny.emulator.dvm;

import com.sun.jna.Pointer;

public interface VM {

    int JNI_FALSE = 0;
    int JNI_TRUE = 1;
    int JNI_OK = 0;
    int JNI_ERR = -1;
    int JNI_NULL = 0;

    int JNIInvalidRefType = 0; // 无效引用
    int JNIGlobalRefType = 2;  //全局引用

    Pointer getJavaVM();

    Pointer getJNIEnv();

    DvmClass resolveClass(String className);
    DvmClass findClass(String className);

}
