package cn.banny.emulator.dvm;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class DvmClass {

    private static final Log log = LogFactory.getLog(DvmClass.class);

    final DalvikVM vm;
    final String className;

    DvmClass(DalvikVM vm, String className) {
        this.vm = vm;
        this.className = className;
    }

    final Map<Long, DvmMethod> staticMethodMap = new HashMap<>();

    int getStaticMethodID(String methodName, String args) {
        String name = className + "->" + methodName + args;
        long hash = name.hashCode() & 0xffffffffL;
        log.debug("getStaticMethodID name=" + name + ", hash=0x" + Long.toHexString(hash));
        staticMethodMap.put(hash, new DvmMethod(this, methodName, args));
        return (int) hash;
    }

    final Map<Long, DvmMethod> methodMap = new HashMap<>();

    int getMethodID(String methodName, String args) {
        String name = className + "->" + methodName + args;
        long hash = name.hashCode() & 0xffffffffL;
        log.debug("getMethodID name=" + name + ", hash=0x" + Long.toHexString(hash));
        methodMap.put(hash, new DvmMethod(this, methodName, args));
        return (int) hash;
    }

    final Map<Long, DvmField> fieldMap = new HashMap<>();

    int getFieldID(String fieldName, String fieldType) {
        String name = className + "->" + fieldName + ":" + fieldType;
        long hash = name.hashCode() & 0xffffffffL;
        log.debug("getFieldID name=" + name + ", hash=0x" + Long.toHexString(hash));
        fieldMap.put(hash, new DvmField(this, fieldName, fieldType));
        return (int) hash;
    }

    final Map<Long, DvmField> staticFieldMap = new HashMap<>();

    int getStaticFieldID(String fieldName, String fieldType) {
        String name = className + "->" + fieldName + ":" + fieldType;
        long hash = name.hashCode() & 0xffffffffL;
        log.debug("getStaticFieldID name=" + name + ", hash=0x" + Long.toHexString(hash));
        staticFieldMap.put(hash, new DvmField(this, fieldName, fieldType));
        return (int) hash;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DvmClass dvmClass = (DvmClass) o;
        return Objects.equals(className, dvmClass.className);
    }

    @Override
    public int hashCode() {
        return Objects.hash(className);
    }

    @Override
    public String toString() {
        return className;
    }
}
