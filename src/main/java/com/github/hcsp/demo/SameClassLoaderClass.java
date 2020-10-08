package com.github.hcsp.demo;

import com.github.hcsp.MiniJVMCLass;

public class SameClassLoaderClass {
    public static void main(String[] args) throws ClassNotFoundException {
        MyClassLoader myClassLoader = new MyClassLoader();
        MiniJVMCLass klass = myClassLoader.loadClass("com.github.hcsp.demo.SimpleClass");
        //我们预期他会抛出ClassCastException
        SimpleClass simpleClass = (SimpleClass) klass.newInstance();
    }
}
