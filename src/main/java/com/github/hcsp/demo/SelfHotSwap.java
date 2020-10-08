package com.github.hcsp.demo;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;

/**
 * 热加载
 */
public class SelfHotSwap {

    public static void main(String[] args) throws ClassNotFoundException, InterruptedException, IOException, NoSuchMethodException {
        int resourceLength = 0;
        while (true){
            Thread.sleep(1000);
            byte[] bytes = Files.readAllBytes(new File("src/main/java/com/github/hcsp/BranchClass.class").toPath());
            if(bytes.length != resourceLength){
                //reload
                new Thread(()->{
                    try {
                        System.out.println("run");
                        Class cl = new SelfClassLoad().loadClass("com.github.hcsp.demo.BranchClass");
                        Method main = cl.getMethod("main" , String[].class);
                        main.invoke(null,new Object[]{null});
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }).start();
                //reset length
                resourceLength = bytes.length;
            }
        }
    }

    static class SelfClassLoad extends ClassLoader{
        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if(name.contains("BranchClass")){
                try {
                    byte[] bytes = Files.readAllBytes(new File("src/main/java/com/github/hcsp/BranchClass.class").toPath());
                    return defineClass(name , bytes, 0 , bytes.length);
                } catch (IOException e) {
                    throw new ClassNotFoundException(name);
                }
            }else{
                return super.loadClass(name);
            }
        }
    }

}
