package com.github.hcsp;

import com.github.hcsp.demo.MyClassLoader;
import com.github.hcsp.demo.SimpleClass;
import com.github.zxh.classpy.classfile.ClassFile;
import com.github.zxh.classpy.classfile.MethodInfo;
import com.github.zxh.classpy.classfile.datatype.Table;

import java.util.List;

public class MiniJVMCLass {
    private String name;
    private MiniJVMClassLoader classLoader;
    private ClassFile classFile;

    public MiniJVMCLass(String name, MiniJVMClassLoader classLoader, ClassFile classFile) {
        this.name = name;
        this.classLoader = classLoader;
        this.classFile = classFile;
    }

    public MiniJVMClassLoader getClassLoader() {
        return classLoader;
    }

    public Object newInstance() {
        if (name.contains("MyClassLoader")) {
            return new MiniJVMObject(this, new MyClassLoader());
        } else if(name.contains("SimpleClass")) {
            return new MiniJVMObject(this, new SimpleClass());
        }else{
            throw new IllegalStateException("Not implemented yet!");
        }
    }

    public Table getMethods() {
        return classFile.getMethods();
    }

    public List<MethodInfo> getMethod(String methodName) {
        return classFile.getMethod(methodName);
    }

    public ClassFile getClassFile() {
        return classFile;
    }

    public String getName() {
        return name;
    }
}
