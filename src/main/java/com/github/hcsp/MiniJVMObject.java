package com.github.hcsp;

public class MiniJVMObject {
    private MiniJVMCLass klass;
    private Object realJavaObject;

    public MiniJVMObject(MiniJVMCLass klass, Object realJavaObject) {
        this.klass = klass;
        this.realJavaObject = realJavaObject;
    }

    public MiniJVMCLass getKlass() {
        return klass;
    }

    public Object getRealJavaObject() {
        return realJavaObject;
    }
}
