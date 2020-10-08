package com.github.hcsp.demo;

import com.github.hcsp.MiniJVMCLass;
import com.github.hcsp.MiniJVMClassLoader;

public class MyClassLoader extends MiniJVMClassLoader {
    public MyClassLoader() {
        super(new String[]{"target/classes"}, null);
    }

    public MiniJVMCLass loadClass(String className) throws ClassNotFoundException {
        return findAndDefineClass(className);
    }
}
