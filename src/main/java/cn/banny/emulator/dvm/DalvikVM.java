package cn.banny.emulator.dvm;

import cn.banny.emulator.Emulator;
import cn.banny.emulator.SvcMemory;
import cn.banny.emulator.pointer.UnicornPointer;
import cn.banny.emulator.svc.ArmSvc;
import com.sun.jna.Pointer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import unicorn.ArmConst;
import unicorn.Unicorn;
import unicorn.UnicornException;

import java.util.HashMap;
import java.util.Map;

public class DalvikVM implements VM {

    private static final Log log = LogFactory.getLog(DalvikVM.class);

    private final Map<Long, DvmClass> classMap = new HashMap<>();

    private final UnicornPointer _JavaVM;
    private final UnicornPointer _JNIEnv;
    final Jni jni;

    public DalvikVM(SvcMemory svcMemory, Jni jni) {
        this.jni = jni;

        _JavaVM = svcMemory.allocate(4);

        Pointer _FindClass = svcMemory.registerSvc(new ArmSvc() {
            @Override
            public int handle(Unicorn u, Emulator emulator) {
                Pointer env = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R0);
                Pointer className = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R1);
                String name = className.getString(0);
                long hash = name.hashCode() & 0xffffffffL;
                log.debug("FindClass env=" + env + ", className=" + name + ", hash=0x" + Long.toHexString(hash));
                classMap.put(hash, new DvmClass(DalvikVM.this, name));
                return (int) hash;
            }
        });

        Pointer _NewGlobalRef = svcMemory.registerSvc(new ArmSvc() {
            @Override
            public int handle(Unicorn u, Emulator emulator) {
                UnicornPointer object = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R1);
                log.debug("NewGlobalRef object=" + object);
                return (int) object.peer;
            }
        });

        Pointer _DeleteGlobalRef = svcMemory.registerSvc(new ArmSvc() {
            @Override
            public int handle(Unicorn u, Emulator emulator) {
                UnicornPointer object = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R0);
                objectMap.remove(object.peer);
                return 0;
            }
        });

        Pointer _IsSameObject = svcMemory.registerSvc(new ArmSvc() {
            @Override
            public int handle(Unicorn u, Emulator emulator) {
                Pointer ref1 = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R1);
                Pointer ref2 = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R2);
                log.debug("IsSameObject ref1=" + ref1 + ", ref2=" + ref2);
                return ref1 == ref2 || ref1.equals(ref2) ? JNI_TRUE : JNI_FALSE;
            }
        });

        Pointer _GetObjectClass = svcMemory.registerSvc(new ArmSvc() {
            @Override
            public int handle(Unicorn u, Emulator emulator) {
                UnicornPointer obj = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R1);
                DvmObject object = objectMap.get(obj.peer);
                if (object == null) {
                    throw new UnicornException();
                } else {
                    DvmClass dvmClass = object.objectType;
                    long hash = dvmClass.hashCode() & 0xffffffffL;
                    classMap.put(hash, dvmClass);
                    return (int) hash;
                }
            }
        });

        Pointer _GetMethodID = svcMemory.registerSvc(new ArmSvc() {
            @Override
            public int handle(Unicorn u, Emulator emulator) {
                UnicornPointer clazz = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R1);
                Pointer methodName = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R2);
                Pointer argsPointer = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R3);
                String name = methodName.getString(0);
                String args = argsPointer.getString(0);
                log.debug("GetMethodID class=" + clazz + ", methodName=" + name + ", args=" + args);
                DvmClass dvmClass = classMap.get(clazz.peer);
                if (dvmClass == null) {
                    throw new UnicornException();
                } else {
                    return dvmClass.getMethodID(name, args);
                }
            }
        });

        Pointer _CallObjectMethodV = svcMemory.registerSvc(new ArmSvc() {
            @Override
            public int handle(Unicorn u, Emulator emulator) {
                UnicornPointer object = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R1);
                UnicornPointer jmethodID = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R2);
                log.debug("CallObjectMethodV object=" + object + ", jmethodID=" + jmethodID);
                DvmObject dvmObject = objectMap.get(object.peer);
                DvmClass dvmClass = dvmObject == null ? null : dvmObject.objectType;
                DvmMethod dvmMethod = dvmClass == null ? null : dvmClass.methodMap.get(jmethodID.peer);
                if (dvmMethod == null) {
                    throw new UnicornException();
                } else {
                    return dvmMethod.callObjectMethodV();
                }
            }
        });

        Pointer _GetFieldID = svcMemory.registerSvc(new ArmSvc() {
            @Override
            public int handle(Unicorn u, Emulator emulator) {
                UnicornPointer clazz = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R1);
                Pointer fieldName = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R2);
                Pointer argsPointer = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R3);
                String name = fieldName.getString(0);
                String args = argsPointer.getString(0);
                log.debug("GetFieldID class=" + clazz + ", fieldName=" + name + ", args=" + args);
                DvmClass dvmClass = classMap.get(clazz.peer);
                if (dvmClass == null) {
                    throw new UnicornException();
                } else {
                    return dvmClass.getFieldID(name, args);
                }
            }
        });

        Pointer _GetStaticMethodID = svcMemory.registerSvc(new ArmSvc() {
            @Override
            public int handle(Unicorn u, Emulator emulator) {
                UnicornPointer clazz = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R1);
                Pointer methodName = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R2);
                Pointer argsPointer = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R3);
                String name = methodName.getString(0);
                String args = argsPointer.getString(0);
                log.debug("GetStaticMethodID class=" + clazz + ", methodName=" + name + ", args=" + args);
                DvmClass dvmClass = classMap.get(clazz.peer);
                if (dvmClass == null) {
                    throw new UnicornException();
                } else {
                    return dvmClass.getStaticMethodID(name, args);
                }
            }
        });

        Pointer _CallStaticObjectMethodV = svcMemory.registerSvc(new ArmSvc() {
            @Override
            public int handle(Unicorn u, Emulator emulator) {
                UnicornPointer clazz = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R1);
                UnicornPointer jmethodID = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R2);
                log.debug("CallStaticObjectMethodV clazz=" + clazz + ", jmethodID=" + jmethodID);
                DvmClass dvmClass = classMap.get(clazz.peer);
                DvmMethod dvmMethod = dvmClass == null ? null : dvmClass.staticMethodMap.get(jmethodID.peer);
                if (dvmMethod == null) {
                    throw new UnicornException();
                } else {
                    return dvmMethod.callStaticObjectMethodV();
                }
            }
        });

        Pointer _CallStaticBooleanMethodV = svcMemory.registerSvc(new ArmSvc() {
            @Override
            public int handle(Unicorn u, Emulator emulator) {
                UnicornPointer clazz = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R1);
                UnicornPointer jmethodID = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R2);
                UnicornPointer va_list = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R3);
                log.debug("CallStaticBooleanMethodV clazz=" + clazz + ", jmethodID=" + jmethodID + ", va_list=" + va_list);
                DvmClass dvmClass = classMap.get(clazz.peer);
                DvmMethod dvmMethod = dvmClass == null ? null : dvmClass.staticMethodMap.get(jmethodID.peer);
                if (dvmMethod == null) {
                    throw new UnicornException();
                } else {
                    return dvmMethod.callStaticBooleanMethodV();
                }
            }
        });

        Pointer _CallStaticIntMethodV = svcMemory.registerSvc(new ArmSvc() {
            @Override
            public int handle(Unicorn u, Emulator emulator) {
                UnicornPointer clazz = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R1);
                UnicornPointer jmethodID = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R2);
                UnicornPointer va_list = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R3);
                log.debug("CallStaticIntMethodV clazz=" + clazz + ", jmethodID=" + jmethodID + ", va_list=" + va_list);
                DvmClass dvmClass = classMap.get(clazz.peer);
                DvmMethod dvmMethod = dvmClass == null ? null : dvmClass.staticMethodMap.get(jmethodID.peer);
                if (dvmMethod == null) {
                    throw new UnicornException();
                } else {
                    return dvmMethod.callStaticIntMethodV(va_list);
                }
            }
        });

        Pointer _GetStaticFieldID = svcMemory.registerSvc(new ArmSvc() {
            @Override
            public int handle(Unicorn u, Emulator emulator) {
                UnicornPointer clazz = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R1);
                Pointer fieldName = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R2);
                Pointer argsPointer = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R3);
                String name = fieldName.getString(0);
                String args = argsPointer.getString(0);
                log.debug("GetStaticFieldID class=" + clazz + ", fieldName=" + name + ", args=" + args);
                DvmClass dvmClass = classMap.get(clazz.peer);
                if (dvmClass == null) {
                    throw new UnicornException();
                } else {
                    return dvmClass.getStaticFieldID(name, args);
                }
            }
        });

        Pointer _GetStaticObjectField = svcMemory.registerSvc(new ArmSvc() {
            @Override
            public int handle(Unicorn u, Emulator emulator) {
                UnicornPointer clazz = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R1);
                UnicornPointer jfieldID = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R2);
                log.debug("GetStaticObjectField clazz=" + clazz + ", jfieldID=" + jfieldID);
                DvmClass dvmClass = classMap.get(clazz.peer);
                DvmField dvmField = dvmClass == null ? null : dvmClass.staticFieldMap.get(jfieldID.peer);
                if (dvmField == null) {
                    throw new UnicornException();
                } else {
                    return dvmField.getStaticObjectField();
                }
            }
        });

        Pointer _RegisterNatives = svcMemory.registerSvc(new ArmSvc() {
            @Override
            public int handle(Unicorn u, Emulator emulator) {
                UnicornPointer clazz = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R1);
                Pointer methods = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R2);
                int nMethods = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R3)).intValue();
                DvmClass dvmClass = classMap.get(clazz.peer);
                log.debug("RegisterNatives dvmClass=" + dvmClass + ", methods=" + methods + ", nMethods=" + nMethods);
                for (int i = 0; i < nMethods; i++) {
                    Pointer method = methods.share(i * 0xc);
                    Pointer name = method.getPointer(0);
                    Pointer signature = method.getPointer(4);
                    Pointer fnPtr = method.getPointer(8);
                    log.debug("RegisterNatives dvmClass=" + dvmClass + ", name=" + name.getString(0) + ", signature=" + signature.getString(0) + ", fnPtr=" + fnPtr);
                }
                return JNI_OK;
            }
        });

        Pointer _ExceptionCheck = svcMemory.registerSvc(new ArmSvc() {
            @Override
            public int handle(Unicorn u, Emulator emulator) {
                log.debug("ExceptionCheck");
                return JNI_FALSE;
            }
        });

        Pointer _GetObjectRefType = svcMemory.registerSvc(new ArmSvc() {
            @Override
            public int handle(Unicorn u, Emulator emulator) {
                UnicornPointer object = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R1);
                DvmObject dvmObject = objectMap.get(object.peer);
                DvmClass dvmClass = classMap.get(object.peer);
                log.debug("GetObjectRefType object=" + object + ", dvmObject=" + dvmObject + ", dvmClass=" + dvmClass);
                return dvmObject == null && dvmClass == null ? JNIInvalidRefType : JNIGlobalRefType;
            }
        });

        final UnicornPointer _JNIEnvImpl = svcMemory.allocate(0x3a4);
        for (int i = 0; i < 0x3a4; i += 4) {
            _JNIEnvImpl.setInt(i, i);
        }
        _JNIEnvImpl.setPointer(0x18, _FindClass);
        _JNIEnvImpl.setPointer(0x54, _NewGlobalRef);
        _JNIEnvImpl.setPointer(0x58, _DeleteGlobalRef);
        _JNIEnvImpl.setPointer(0x60, _IsSameObject);
        _JNIEnvImpl.setPointer(0x7c, _GetObjectClass);
        _JNIEnvImpl.setPointer(0x84, _GetMethodID);
        _JNIEnvImpl.setPointer(0x8c, _CallObjectMethodV);
        _JNIEnvImpl.setPointer(0x178, _GetFieldID);
        _JNIEnvImpl.setPointer(0x1c4, _GetStaticMethodID);
        _JNIEnvImpl.setPointer(0x1cc, _CallStaticObjectMethodV);
        _JNIEnvImpl.setPointer(0x1d8, _CallStaticBooleanMethodV);
        _JNIEnvImpl.setPointer(0x208, _CallStaticIntMethodV);
        _JNIEnvImpl.setPointer(0x240, _GetStaticFieldID);
        _JNIEnvImpl.setPointer(0x244, _GetStaticObjectField);
        _JNIEnvImpl.setPointer(0x35c, _RegisterNatives);
        _JNIEnvImpl.setPointer(0x390, _ExceptionCheck);
        _JNIEnvImpl.setPointer(0x3a0, _GetObjectRefType);

        _JNIEnv = svcMemory.allocate(4);
        _JNIEnv.setPointer(0, _JNIEnvImpl);

        UnicornPointer _AttachCurrentThread = svcMemory.registerSvc(new ArmSvc() {
            @Override
            public int handle(Unicorn u, Emulator emulator) {
                Pointer vm = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R0);
                Pointer env = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R1);
                Pointer args = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R2); // JavaVMAttachArgs*
                log.debug("AttachCurrentThread vm=" + vm + ", env=" + env.getPointer(0) + ", args=" + args);
                env.setPointer(0, _JNIEnv);
                return JNI_OK;
            }
        });

        UnicornPointer _JNIInvokeInterface = svcMemory.allocate(32);
        _JNIInvokeInterface.setPointer(0x10, _AttachCurrentThread);

        _JavaVM.setPointer(0, _JNIInvokeInterface);

        log.debug("_JavaVM=" + _JavaVM + ", _JNIInvokeInterface=" + _JNIInvokeInterface + ", _JNIEnv=" + _JNIEnv);
    }

    @Override
    public Pointer getJavaVM() {
        return _JavaVM;
    }

    @Override
    public Pointer getJNIEnv() {
        return _JNIEnv;
    }

    final Map<Long, DvmObject> objectMap = new HashMap<>();

    int addObject(DvmObject object) {
        if (object == null) {
            return 0;
        } else {
            long hash = object.hashCode() & 0xffffffffL;
            objectMap.put(hash, object);
            return (int) hash;
        }
    }

    @Override
    public DvmClass findClass(String className) {
        return classMap.get(className.hashCode() & 0xffffffffL);
    }
}
