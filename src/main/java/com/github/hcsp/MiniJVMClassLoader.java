package com.github.hcsp;

import com.github.zxh.classpy.classfile.ClassFileParser;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class MiniJVMClassLoader {
    //启动类加载器
    public static MiniJVMClassLoader BOOTSTRAP_CLASSLOADER
            = new MiniJVMClassLoader(new String[]{System.getProperty("java.home") + "/lib/rt.jar"}, null);
    //扩展类加载器
    public static MiniJVMClassLoader EXT_CLASSLOADED
            = new MiniJVMClassLoader(
            Stream.of(new File(System.getProperty("java.home") + "/lib/ext").listFiles())
                    .filter(File::isFile)
                    .filter(f -> f.getName().endsWith(".jar"))
                    .map(File::getName)
                    .toArray(String[]::new), BOOTSTRAP_CLASSLOADER);

    //Bootstrap类加载器 从rt.jar
    //Ext类加载器 ext/
    //应用类加载器 -classpath
    private Map<String, MiniJVMCLass> loadedClasses = new ConcurrentHashMap<>();

    //不一定是-classpath
    //每个entry是一个jar包或者文件夹
    //对于Bootstrap类加载器 从rt.jar
    //对于Ext类加载器 ext/目录下的所有jar包
    //对于应用类加载器 -classpath传入的东西
    private String[] classPath;

    //null代表启动类加载器
    private MiniJVMClassLoader parent;

    public MiniJVMClassLoader(String[] classPath, MiniJVMClassLoader parent) {
        this.classPath = classPath;
        this.parent = parent;
    }

    /**
     * 加载class
     *
     * @param className
     * @return
     * @throws ClassNotFoundException
     */
    public MiniJVMCLass loadClass(String className) throws ClassNotFoundException {
        //存在就直接返回
        if (loadedClasses.containsKey(className)) {
            return loadedClasses.get(className);
        }
        //查找的class对象
        MiniJVMCLass result = null;
        //加载
        try {
            if (parent == null) {   //我是启动类加载器
                result = findAndDefineClass(className);
            } else {
                result = parent.loadClass(className);
            }
        } catch (ClassNotFoundException ignored) {
            if (parent == null) {
                throw ignored;
            }
        }
        //父亲没找到，尝试自己加载
        if (result == null && parent != null) {
            result = findAndDefineClass(className);
        }
        //存储加载的class对象
        loadedClasses.put(className, result);
        return result;
    }

    public MiniJVMCLass findAndDefineClass(String className) throws ClassNotFoundException {
        byte[] bytes = findClassBytes(className);
        return defineClass(className, bytes);
    }

    private MiniJVMCLass defineClass(String className, byte[] bytes) {
        return new MiniJVMCLass(className, this, new ClassFileParser().parse(bytes));
    }

    private byte[] findClassBytes(String className) throws ClassNotFoundException {
        String path = className.replace(".", "/") + ".class";
        for (String entry : classPath) {
            if (new File(entry).isDirectory()) {
                try {
                    return Files.readAllBytes(new File(entry, path).toPath());
                } catch (IOException ignored) {
                }
            } else if (entry.endsWith(".jar")) {
                try {
                    return readBytesFromJar(entry, path);
                } catch (IOException ignored) {
                }
            }
        }
        //
        throw new ClassNotFoundException(className);
    }

    private byte[] readBytesFromJar(String jar, String path) throws IOException {
        ZipFile zipFile = new ZipFile(jar);
        ZipEntry entry = zipFile.getEntry(path);
        if (entry == null) {
            throw new IOException("Not Found : " + path);
        }
        //读取
        InputStream is = zipFile.getInputStream(entry);
        return IOUtils.toByteArray(is);
    }

}
