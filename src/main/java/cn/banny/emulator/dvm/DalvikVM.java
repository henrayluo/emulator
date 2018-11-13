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

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class DalvikVM implements VM {

    private static final Log log = LogFactory.getLog(DalvikVM.class);

    private final Map<Long, DvmClass> classMap = new HashMap<>();

    private final UnicornPointer _JavaVM;
    private final UnicornPointer _JNIEnv;
    final Jni jni;

    public DalvikVM(final SvcMemory svcMemory, Jni jni) {
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

        Pointer _ExceptionOccurred = svcMemory.registerSvc(new ArmSvc() {
            @Override
            public int handle(Unicorn u, Emulator emulator) {
                return JNI_NULL;
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
                UnicornPointer va_list = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R3);
                log.debug("CallObjectMethodV object=" + object + ", jmethodID=" + jmethodID + ", va_list=" + va_list);
                DvmObject dvmObject = objectMap.get(object.peer);
                DvmClass dvmClass = dvmObject == null ? null : dvmObject.objectType;
                DvmMethod dvmMethod = dvmClass == null ? null : dvmClass.methodMap.get(jmethodID.peer);
                if (dvmMethod == null) {
                    throw new UnicornException();
                } else {
                    return dvmMethod.callObjectMethodV(new VaList(DalvikVM.this, va_list));
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

        Pointer _GetObjectField = svcMemory.registerSvc(new ArmSvc() {
            @Override
            public int handle(Unicorn u, Emulator emulator) {
                UnicornPointer object = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R1);
                UnicornPointer jfieldID = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R2);
                log.debug("GetObjectField object=" + object + ", jfieldID=" + jfieldID);
                DvmObject dvmObject = objectMap.get(object.peer);
                DvmClass dvmClass = dvmObject == null ? null : dvmObject.objectType;
                DvmField dvmField = dvmClass == null ? null : dvmClass.fieldMap.get(jfieldID.peer);
                if (dvmField == null) {
                    throw new UnicornException();
                } else {
                    return dvmField.getObjectField(dvmObject);
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
                    return dvmMethod.callStaticIntMethodV(new VaList(DalvikVM.this, va_list));
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

        Pointer _GetStaticIntField = svcMemory.registerSvc(new ArmSvc() {
            @Override
            public int handle(Unicorn u, Emulator emulator) {
                UnicornPointer clazz = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R1);
                UnicornPointer jfieldID = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R2);
                log.debug("GetStaticIntField clazz=" + clazz + ", jfieldID=" + jfieldID);
                DvmClass dvmClass = classMap.get(clazz.peer);
                DvmField dvmField = dvmClass == null ? null : dvmClass.staticFieldMap.get(jfieldID.peer);
                if (dvmField == null) {
                    throw new UnicornException();
                } else {
                    return dvmField.getStaticIntField();
                }
            }
        });

        Pointer _GetStringUTFLength = svcMemory.registerSvc(new ArmSvc() {
            @Override
            public int handle(Unicorn u, Emulator emulator) {
                DvmObject string = objectMap.get(UnicornPointer.register(u, ArmConst.UC_ARM_REG_R1).peer);
                log.debug("GetStringUTFLength string=" + string);
                String value = (String) string.getValue();
                byte[] data = value.getBytes(StandardCharsets.UTF_8);
                return data.length;
            }
        });

        Pointer _GetStringUTFChars = svcMemory.registerSvc(new ArmSvc() {
            @Override
            public int handle(Unicorn u, Emulator emulator) {
                StringObject string = (StringObject) objectMap.get(UnicornPointer.register(u, ArmConst.UC_ARM_REG_R1).peer);
                Pointer isCopy = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R2);
                log.debug("GetStringUTFChars string=" + string + ", isCopy=" + isCopy);
                if (isCopy != null) {
                    isCopy.setInt(0, JNI_TRUE);
                }
                String value = string.getValue();
                byte[] data = value.getBytes(StandardCharsets.UTF_8);
                UnicornPointer pointer = svcMemory.allocate(data.length);
                pointer.write(0, data, 0, data.length);
                string.utf = pointer;
                return (int) pointer.peer;
            }
        });

        Pointer _ReleaseStringUTFChars = svcMemory.registerSvc(new ArmSvc() {
            @Override
            public int handle(Unicorn u, Emulator emulator) {
                StringObject string = (StringObject) objectMap.get(UnicornPointer.register(u, ArmConst.UC_ARM_REG_R1).peer);
                Pointer utf = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R2);
                log.debug("ReleaseStringUTFChars string=" + string + ", utf=" + utf);
                if (utf.equals(string.utf)) {
                    svcMemory.free(string.utf);
                }
                return 0;
            }
        });

        Pointer _GetArrayLength = svcMemory.registerSvc(new ArmSvc() {
            @Override
            public int handle(Unicorn u, Emulator emulator) {
                ArrayObject array = (ArrayObject) objectMap.get(UnicornPointer.register(u, ArmConst.UC_ARM_REG_R1).peer);
                return array.getValue().length;
            }
        });

        Pointer _GetObjectArrayElement = svcMemory.registerSvc(new ArmSvc() {
            @Override
            public int handle(Unicorn u, Emulator emulator) {
                ArrayObject array = (ArrayObject) objectMap.get(UnicornPointer.register(u, ArmConst.UC_ARM_REG_R1).peer);
                int index = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R2)).intValue();
                return addObject(array.getValue()[index]);
            }
        });

        Pointer _NewStringUTF = svcMemory.registerSvc(new ArmSvc() {
            @Override
            public int handle(Unicorn u, Emulator emulator) {
                Pointer bytes = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R1);
                String utf = bytes.getString(0);
                log.debug("NewStringUTF bytes=" + bytes + ", utf=" + utf);
                return addObject(new StringObject(resolveClass("java/lang/String"), utf));
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
                    String methodName = name.getString(0);
                    String signatureValue = signature.getString(0);
                    log.debug("RegisterNatives dvmClass=" + dvmClass + ", name=" + methodName + ", signature=" + signatureValue + ", fnPtr=" + fnPtr);
                    dvmClass.nativesMap.put(methodName + signatureValue, (UnicornPointer) fnPtr);
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

        final UnicornPointer impl = svcMemory.allocate(0x3a4);
        for (int i = 0; i < 0x3a4; i += 4) {
            impl.setInt(i, i);
        }
        impl.setPointer(0x18, _FindClass);
        impl.setPointer(0x3c, _ExceptionOccurred);
        impl.setPointer(0x54, _NewGlobalRef);
        impl.setPointer(0x58, _DeleteGlobalRef);
        impl.setPointer(0x60, _IsSameObject);
        impl.setPointer(0x7c, _GetObjectClass);
        impl.setPointer(0x84, _GetMethodID);
        impl.setPointer(0x8c, _CallObjectMethodV);
        impl.setPointer(0x178, _GetFieldID);
        impl.setPointer(0x17c, _GetObjectField);
        impl.setPointer(0x1c4, _GetStaticMethodID);
        impl.setPointer(0x1cc, _CallStaticObjectMethodV);
        impl.setPointer(0x1d8, _CallStaticBooleanMethodV);
        impl.setPointer(0x208, _CallStaticIntMethodV);
        impl.setPointer(0x240, _GetStaticFieldID);
        impl.setPointer(0x244, _GetStaticObjectField);
        impl.setPointer(0x258, _GetStaticIntField);
        impl.setPointer(0x2a0, _GetStringUTFLength);
        impl.setPointer(0x2a4, _GetStringUTFChars);
        impl.setPointer(0x2a8, _ReleaseStringUTFChars);
        impl.setPointer(0x2ac, _GetArrayLength);
        impl.setPointer(0x29c, _NewStringUTF);
        impl.setPointer(0x2b4, _GetObjectArrayElement);
        impl.setPointer(0x35c, _RegisterNatives);
        impl.setPointer(0x390, _ExceptionCheck);
        impl.setPointer(0x3a0, _GetObjectRefType);

        _JNIEnv = svcMemory.allocate(4);
        _JNIEnv.setPointer(0, impl);

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

    @Override
    public int addObject(DvmObject object) {
        if (object == null) {
            return 0;
        } else {
            long hash = object.hashCode() & 0xffffffffL;
            objectMap.put(hash, object);
            return (int) hash;
        }
    }

    @Override
    public DvmClass resolveClass(String className) {
        long hash = className.hashCode() & 0xffffffffL;
        DvmClass clazz = classMap.get(hash);
        if (clazz != null) {
            return clazz;
        } else {
            clazz = new DvmClass(this, className);
            classMap.put(hash, clazz);
            return clazz;
        }
    }

    @Override
    public DvmClass findClass(String className) {
        return classMap.get(className.hashCode() & 0xffffffffL);
    }
}
