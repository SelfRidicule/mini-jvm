package com.github.hcsp;

import com.github.hcsp.demo.MyClassLoader;
import com.github.zxh.classpy.classfile.ClassFile;
import com.github.zxh.classpy.classfile.ClassFileParser;
import com.github.zxh.classpy.classfile.MethodInfo;
import com.github.zxh.classpy.classfile.bytecode.Bipush;
import com.github.zxh.classpy.classfile.bytecode.Instruction;
import com.github.zxh.classpy.classfile.bytecode.InstructionCp1;
import com.github.zxh.classpy.classfile.bytecode.InstructionCp2;
import com.github.zxh.classpy.classfile.constant.*;
import com.github.zxh.classpy.classfile.datatype.U1CpIndex;
import com.github.zxh.classpy.common.FilePart;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Stack;

/**
 * 这是一个用来学习的JVM
 */
public class MiniJVM {
    private String mainClassName;
    private MiniJVMClassLoader appClassLoader;

    public static void main(String[] args) throws ClassNotFoundException {
        new MiniJVM("target/classes", "com.github.hcsp.demo.SameClassLoaderClass").start();
    }

    /**
     * 创建一个迷你JVM，使用指定的classpath和main class
     *
     * @param classPath 启动时的classpath，使用{@link java.io.File#pathSeparator}的分隔符，我们支持文件夹
     */
    public MiniJVM(String classPath, String mainClassName) {
        this.mainClassName = mainClassName;
        this.appClassLoader = new MiniJVMClassLoader(classPath.split(File.pathSeparator), MiniJVMClassLoader.EXT_CLASSLOADED);
    }

    /**
     * 启动并运行该虚拟机
     */
    public void start() throws ClassNotFoundException {
        //
        MiniJVMCLass mainClass = appClassLoader.loadClass(mainClassName);
        //
        MethodInfo methodInfo = mainClass.getMethod("main").get(0);
        //
        Stack<StackFrame> methodStack = new Stack<>();
        //
        Object[] localVariablesForMainStackFrame = new Object[methodInfo.getMaxLocals()];
        localVariablesForMainStackFrame[0] = null;

        methodStack.push(new StackFrame(localVariablesForMainStackFrame, methodInfo, mainClass));

        PCRegister pcRegister = new PCRegister(methodStack);

        while (true) {
            Instruction instruction = pcRegister.getNextInstruction();
            if (instruction == null) {
                break;
            }
            switch (instruction.getOpcode()) {
                case getstatic: {
                    int fieldIndex = InstructionCp2.class.cast(instruction).getTargetFieldIndex();
                    ConstantPool constantPool = pcRegister.getTopFrameClassConstantPool();
                    ConstantFieldrefInfo fieldrefInfo = constantPool.getFieldrefInfo(fieldIndex);
                    ConstantClassInfo classInfo = fieldrefInfo.getClassInfo(constantPool);
                    ConstantNameAndTypeInfo nameAndTypeInfo = fieldrefInfo.getFieldNameAndTypeInfo(constantPool);

                    String className = constantPool.getUtf8String(classInfo.getNameIndex());
                    String fieldName = nameAndTypeInfo.getName(constantPool);

                    if ("java/lang/System".equals(className) && "out".equals(fieldName)) {
                        Object field = System.out;
                        pcRegister.getTopFrame().pushObjectToOperandStack(field);
                    } else {
                        throw new IllegalStateException("Not implemented yet!");
                    }
                }
                break;
                case invokestatic: {
                    String className = getClassNameFromInvokeInstruction(instruction, pcRegister.getTopFrameClassConstantPool());
                    String methodName = getMethodNameFromInvokeInstruction(instruction, pcRegister.getTopFrameClassConstantPool());
                    MiniJVMCLass classFile = appClassLoader.loadClass(className);
                    MethodInfo targetMethodInfo = classFile.getMethod(methodName).get(0);

                    //应该分析方法的参数，从操作数栈上弹出对应数量的参数放在新栈帧的局部变量表中
                    Object[] localVariables = new Object[targetMethodInfo.getMaxLocals()];
                    if (targetMethodInfo.getMaxLocals() > 0) {
                        for (int i = 0; i < targetMethodInfo.getMaxLocals(); i++) {
                            //把栈顶的栈桢，从操作数栈上弹出对应数量的参数，添加到新栈桢里的局部变量表
                            localVariables[i] = pcRegister.getTopFrame().popFromOperandStack();
                        }
                    }
                    StackFrame newFrame = new StackFrame(localVariables, targetMethodInfo, classFile);
                    methodStack.push(newFrame);
                }
                break;
                case bipush: {
                    Bipush bipush = (Bipush) instruction;
                    pcRegister.getTopFrame().pushObjectToOperandStack(bipush.getOperand());
                }
                break;
                case ireturn: {
                    Object returnValue = pcRegister.getTopFrame().popFromOperandStack();
                    pcRegister.popFrameFromMethodStack();
                    pcRegister.getTopFrame().pushObjectToOperandStack(returnValue);
                }
                break;
                case invokevirtual: {
                    String className = getClassNameFromInvokeInstruction(instruction, pcRegister.getTopFrameClassConstantPool());
                    String methodName = getMethodNameFromInvokeInstruction(instruction, pcRegister.getTopFrameClassConstantPool());
                    if ("java/io/PrintStream".equals(className) && "println".equals(methodName)) {
                        Object param = pcRegister.getTopFrame().popFromOperandStack();
                        Object thisObject = pcRegister.getTopFrame().popFromOperandStack();
                        System.out.println(param);
                    } else if ("com/github/hcsp/demo/MyClassLoader".equals(className) && "loadClass".equals(methodName)) {
                        String classNameParam = (String) pcRegister.getTopFrame().popFromOperandStack();
                        MiniJVMObject thisObject = (MiniJVMObject) pcRegister.getTopFrame().popFromOperandStack();
                        MiniJVMCLass result = ((MyClassLoader) thisObject.getRealJavaObject()).loadClass(classNameParam);
                        pcRegister.getTopFrame().pushObjectToOperandStack(result);
                    } else if ("com/github/hcsp/MiniJVMCLass".equals(className) && "newInstance".equals(methodName)) {
                        MiniJVMCLass thisObject = (MiniJVMCLass) pcRegister.getTopFrame().popFromOperandStack();
                        pcRegister.getTopFrame().pushObjectToOperandStack(thisObject.newInstance());
                    } else {
                        throw new IllegalStateException("Not implemented yet!");
                    }
                }
                break;
                case iload_0: {  //从当前栈桢局部变量表中0号位置得到int类型数据，加载到操作数栈
                    Object intValue = pcRegister.getTopFrame().getLocalVariables()[0];
                    pcRegister.getTopFrame().pushObjectToOperandStack(intValue);
                }
                break;
                case iconst_1: {//把常量1添加到操作数栈
                    pcRegister.getTopFrame().pushObjectToOperandStack(1);
                }
                break;
                case iconst_2: { //把常量2添加到操作数栈
                    pcRegister.getTopFrame().pushObjectToOperandStack(2);
                }
                break;
                case iconst_3: { //把常量3添加到操作数栈
                    pcRegister.getTopFrame().pushObjectToOperandStack(3);
                }
                break;
                case iconst_4: { //把常量4添加到操作数栈
                    pcRegister.getTopFrame().pushObjectToOperandStack(4);
                }
                break;
                case iconst_5: { //把常量5添加到操作数栈
                    pcRegister.getTopFrame().pushObjectToOperandStack(5);
                }
                break;
                case irem: { //value1和value2都必须是int类型。这些值是从操作数堆栈中弹出的。int结果是value1-（value1/value2）*value2。结果被推送到操作数堆栈上。
                    int value2 = (int) pcRegister.getTopFrame().popFromOperandStack();
                    int value1 = (int) pcRegister.getTopFrame().popFromOperandStack();
                    int result = value1 - (value1 / value2) * value2;
                    pcRegister.getTopFrame().pushObjectToOperandStack(result);
                }
                break;
                case ifne: { //当且仅当值≠0时，ifne成功
                    int value = (int) pcRegister.getTopFrame().popFromOperandStack();
                    if (value != 0) {
                        //得到当前栈桢里执行的指令
                        List<Instruction> stackInstructionList = pcRegister.getTopFrame().getMethodInfo().getCode();
                        //从instruction.getDesc获取到欲调转的指令号,使用空格进行分割
                        int pc = Integer.valueOf(instruction.getDesc().split(" ")[1]);
                        for (int i = 0; i < stackInstructionList.size(); i++) {
                            if (pc == stackInstructionList.get(i).getPc()) {
                                pcRegister.getTopFrame().setCurrentInstructionIndex(i);
                                break;
                            }
                        }
                    }
                }
                break;
                case sipush: { //立即无符号字节1和字节2的值被组合成一个中间短字节，其中短字节的值为（字节1<<8）|字节2。然后将中间值符号扩展为整型值。该值被推送到操作数堆栈上。
                    Integer returnValue = Integer.valueOf(instruction.getDesc().split(" ")[1]);
                    pcRegister.getTopFrame().pushObjectToOperandStack(returnValue);
                }
                break;
                case isub: { //value1和value2都必须是int类型。这些值是从操作数堆栈中弹出的。int结果是value1-value2。结果被推送到操作数堆栈上。
                    int value2 = (int) pcRegister.getTopFrame().popFromOperandStack();
                    int value1 = (int) pcRegister.getTopFrame().popFromOperandStack();
                    int result = value1 - value2;
                    pcRegister.getTopFrame().pushObjectToOperandStack(result);
                }
                break;
                case imul: { //value1和value2都必须是int类型。这些值来自操作数堆栈。int结果是value1*value2。结果被推送到操作数堆栈上。
                    int value2 = (int) pcRegister.getTopFrame().popFromOperandStack();
                    int value1 = (int) pcRegister.getTopFrame().popFromOperandStack();
                    int result = value1 * value2;
                    pcRegister.getTopFrame().pushObjectToOperandStack(result);
                }
                break;
                case _new: { //无符号indexbyte1和indexbyte2用于在当前类（§2.6）的运行时常量池中构造索引，其中索引的值为（indexbyte1<<8）| indexbyte2。索引处的运行时常量池项必须是对类或接口类型的符号引用。已解析命名的类或接口类型（§5.4.3.1），并应产生一个类类型。该类的新实例的内存从垃圾收集堆中分配，新对象的实例变量被初始化为其默认初始值（§2.3，§2.4）。objectref（对实例的引用）被推送到操作数堆栈上。
                    String className = getClassNameFromNewOrCheckCastInstruction(instruction, pcRegister.getTopFrameClassConstantPool());
                    MiniJVMCLass klass = pcRegister.getTopFrame().getKlass().getClassLoader().loadClass(className);
                    pcRegister.getTopFrame().pushObjectToOperandStack(klass.newInstance());
                }
                break;
                case dup: {  //复制操作数堆栈上的顶部值，并将复制的值推送到操作数堆栈上。除非值是1类计算类型的值（§2.11.1），否则不得使用dup指令。
                    pcRegister.getTopFrame().pushObjectToOperandStack(pcRegister.getTopFrame().peekOperandStack());
                }
                break;
                case invokespecial: { //...
                    pcRegister.getTopFrame().popFromOperandStack();
                }
                break;
                case astore_1: { //索引是一个无符号字节，它必须是当前帧的局部变量数组的索引（§2.6）。操作数堆栈顶部的objectref必须是returnAddress类型或reference类型。它从操作数堆栈中弹出，并且索引处的局部变量的值被设置为objectref
                    pcRegister.getTopFrame().astore(1);
                }
                break;
                case astore_2: { //索引是一个无符号字节，它必须是当前帧的局部变量数组的索引（§2.6）。操作数堆栈顶部的objectref必须是returnAddress类型或reference类型。它从操作数堆栈中弹出，并且索引处的局部变量的值被设置为objectref
                    pcRegister.getTopFrame().astore(2);
                }
                break;
                case aload_1: { //索引是一个无符号字节，它必须是当前帧的局部变量数组的索引（§2.6）。索引处的局部变量必须包含引用。索引处局部变量中的objectref被推送到操作数堆栈上。
                    pcRegister.getTopFrame().aload(1);
                }
                break;
                case aload_2: { //索引是一个无符号字节，它必须是当前帧的局部变量数组的索引（§2.6）。索引处的局部变量必须包含引用。索引处局部变量中的objectref被推送到操作数堆栈上。
                    pcRegister.getTopFrame().aload(2);
                }
                break;
                case ldc: { //从运行时常量池推送到操作数栈顶
                    FilePart filePart = InstructionCp1.class.cast(instruction).getParts().get(1);
                    U1CpIndex u1CpIndex = (U1CpIndex) filePart;
                    int constantPoolIndex = u1CpIndex.getValue();
                    String str = pcRegister.getTopFrameClassConstantPool().getConstantDesc(constantPoolIndex);
                    pcRegister.getTopFrame().pushObjectToOperandStack(str);
                }
                break;
                case checkcast: { //The objectref must be of type reference. The unsigned indexbyte1 and indexbyte2 are used to construct an index into the run-time constant pool of the current class (§2.6), where the value of the index is (indexbyte1 << 8) | indexbyte2. The run-time constant pool item at the index must be a symbolic reference to a class, array, or interface type.
                    String className = getClassNameFromNewOrCheckCastInstruction(instruction, pcRegister.getTopFrameClassConstantPool()).replace("/",".");
                    MiniJVMCLass targetClass = pcRegister.getTopFrame().getKlass().getClassLoader().loadClass(className);
                    MiniJVMObject objectOnStack = (MiniJVMObject) pcRegister.getTopFrame().peekOperandStack();
                    if (!objectOnStack.getKlass().getName().equals(targetClass.getName())
                            || objectOnStack.getKlass().getClassLoader() != targetClass.getClassLoader()) {
                        throw new ClassCastException("Cant cast type " + objectOnStack.getKlass().getName() + " to type " + targetClass.getName());
                    }
                }
                break;
                case _return:
                    pcRegister.popFrameFromMethodStack();
                    break;
                default:
                    throw new IllegalStateException("Opcode " + instruction + " not implemented yet!");
            }
        }
    }

    private String getClassNameFromNewOrCheckCastInstruction(Instruction instruction, ConstantPool constantPool) {
        int targetClassIndex = InstructionCp2.class.cast(instruction).getTargetClassIndex();
        ConstantClassInfo classInfo = constantPool.getClassInfo(targetClassIndex);
        return constantPool.getUtf8String(classInfo.getNameIndex());
    }

    private String getClassNameFromInvokeInstruction(Instruction instruction, ConstantPool constantPool) {
        int methodIndex = InstructionCp2.class.cast(instruction).getTargetMethodIndex();
        ConstantMethodrefInfo methodrefInfo = constantPool.getMethodrefInfo(methodIndex);
        ConstantClassInfo classInfo = methodrefInfo.getClassInfo(constantPool);
        return constantPool.getUtf8String(classInfo.getNameIndex());
    }

    private String getMethodNameFromInvokeInstruction(Instruction instruction, ConstantPool constantPool) {
        int methodIndex = InstructionCp2.class.cast(instruction).getTargetMethodIndex();
        ConstantMethodrefInfo methodrefInfo = constantPool.getMethodrefInfo(methodIndex);
        ConstantClassInfo classInfo = methodrefInfo.getClassInfo(constantPool);
        return methodrefInfo.getMethodNameAndType(constantPool).getName(constantPool);
    }

    private ClassFile tryLoad(String entry, String fqcn) {
        try {
            byte[] bytes = Files.readAllBytes(new File(entry, fqcn.replace('.', '/') + ".class").toPath());
            return new ClassFileParser().parse(bytes);
        } catch (IOException e) {
            return null;
        }
    }

    static class PCRegister {
        Stack<StackFrame> methodStack;

        public PCRegister(Stack<StackFrame> methodStack) {
            this.methodStack = methodStack;
        }

        public StackFrame getTopFrame() {
            return methodStack.peek();
        }

        public ConstantPool getTopFrameClassConstantPool() {
            return getTopFrame().getClassFile().getConstantPool();
        }

        public Instruction getNextInstruction() {
            if (methodStack.isEmpty()) {
                return null;
            } else {
                StackFrame frameAtTop = methodStack.peek();
                return frameAtTop.getNextInstruction();
            }
        }

        public void popFrameFromMethodStack() {
            methodStack.pop();
        }
    }

    static class StackFrame {
        Object[] localVariables;
        Stack<Object> operandStack = new Stack<>();
        MethodInfo methodInfo;
        MiniJVMCLass klass;

        int currentInstructionIndex;

        public Instruction getNextInstruction() {
            return methodInfo.getCode().get(currentInstructionIndex++);
        }

        public ClassFile getClassFile() {
            return klass.getClassFile();
        }

        public StackFrame(Object[] localVariables, MethodInfo methodInfo, MiniJVMCLass klass) {
            this.localVariables = localVariables;
            this.methodInfo = methodInfo;
            this.klass = klass;
        }

        public void pushObjectToOperandStack(Object object) {
            operandStack.push(object);
        }

        public Object popFromOperandStack() {
            return operandStack.pop();
        }

        /**
         * get set
         */
        public Object[] getLocalVariables() {
            return localVariables;
        }

        public void setLocalVariables(Object[] localVariables) {
            this.localVariables = localVariables;
        }

        public Stack<Object> getOperandStack() {
            return operandStack;
        }

        public void setOperandStack(Stack<Object> operandStack) {
            this.operandStack = operandStack;
        }

        public MethodInfo getMethodInfo() {
            return methodInfo;
        }

        public void setMethodInfo(MethodInfo methodInfo) {
            this.methodInfo = methodInfo;
        }


        public int getCurrentInstructionIndex() {
            return currentInstructionIndex;
        }

        public void setCurrentInstructionIndex(int currentInstructionIndex) {
            this.currentInstructionIndex = currentInstructionIndex;
        }

        public MiniJVMCLass getKlass() {
            return klass;
        }

        public Object peekOperandStack() {
            return operandStack.peek();
        }

        public void astore(int i) {
            localVariables[i] = operandStack.pop();
        }

        public void aload(int i) {
            operandStack.push(localVariables[i]);
        }
    }
}
