package br.vbrasfoot.hooks;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.HexFormat;
import jdk.internal.org.objectweb.asm.*;

/** JDK 17/21 internal ASM, explicitly exported, only for opt-in diagnostic probes. */
public final class CareerTransformer {
    private static final String CLASS_SHA = "4e77bf19948d700acebf8a09bbc0c39fe0cb45901f4dd5eb304fcfddb4fbd19d";
    private static final String BRIDGE = "br/vbrasfoot/HookProbe";
    private CareerTransformer() { }

    public static byte[] transform(byte[] original) throws Exception {
        int java = Runtime.version().feature();
        if (java != 17 && java != 21) throw new IOException("Sonda suporta somente Java 17/21");
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(original));
        if (!hash.equals(CLASS_SHA)) throw new IOException("Fingerprint de c.a incompativel; nenhuma transformacao aplicada");
        return transformFixture(original);
    }

    // Package-private fixture path; production always goes through the exact SHA guard above.
    static byte[] transformFixture(byte[] original) throws IOException {
        ClassReader reader = new ClassReader(original);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        int[] counts = new int[6];
        reader.accept(new ClassVisitor(Opcodes.ASM8, writer) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                MethodVisitor method = super.visitMethod(access, name, descriptor, signature, exceptions);
                boolean load = name.equals("c") && descriptor.equals("(Ljava/lang/String;Z)Z");
                boolean save = name.equals("ae") && descriptor.equals("(Ljava/lang/String;)V");
                boolean reset = name.equals("<init>") && descriptor.equals("()V");
                if (reset) {
                    counts[5]++;
                    return new MethodVisitor(Opcodes.ASM8, method) {
                        @Override public void visitInsn(int op) {
                            if (op == Opcodes.RETURN) super.visitMethodInsn(Opcodes.INVOKESTATIC, BRIDGE, "rootsReset", "()V", false);
                            super.visitInsn(op);
                        }
                    };
                }
                if (!load && !save) return method;
                if ((access & Opcodes.ACC_STATIC) == 0) throw new IllegalArgumentException("Expected static method");
                counts[load ? 0 : 1]++;
                return new MethodVisitor(Opcodes.ASM8, method) {
                    final Label start = new Label(), end = new Label(), handler = new Label();
                    private void call(String name, String desc) { super.visitMethodInsn(Opcodes.INVOKESTATIC, BRIDGE, name, desc, false); }
                    @Override public void visitCode() {
                        super.visitCode();
                        super.visitVarInsn(Opcodes.ALOAD, 0);
                        if (load) super.visitVarInsn(Opcodes.ILOAD, 1);
                        call(load ? "enterLoad" : "enterSave", load ? "(Ljava/lang/String;Z)V" : "(Ljava/lang/String;)V");
                        super.visitLabel(start);
                    }
                    @Override public void visitMethodInsn(int op, String owner, String name, String desc, boolean iface) {
                        super.visitMethodInsn(op, owner, name, desc, iface);
                        if (load && owner.equals("com/esotericsoftware/kryo/io/Input") && name.equals("close") && desc.equals("()V")) {
                            counts[2]++; call("decodedLoad", "()V");
                        }
                        if (save && owner.equals("com/esotericsoftware/kryo/io/Output") && name.equals("close") && desc.equals("()V")) {
                            counts[3]++; call("primaryClosed", "()V");
                        }
                        if (save && owner.equals("java/nio/file/Files") && name.equals("copy")
                                && desc.equals("(Ljava/nio/file/Path;Ljava/nio/file/Path;[Ljava/nio/file/CopyOption;)Ljava/nio/file/Path;")) {
                            counts[4]++; call("backupCopied", "()V");
                        }
                    }
                    @Override public void visitInsn(int op) {
                        if (load && op == Opcodes.IRETURN) { super.visitInsn(Opcodes.DUP); call("exitLoad", "(Z)V"); }
                        if (save && op == Opcodes.RETURN) call("exitSave", "()V");
                        super.visitInsn(op);
                    }
                    @Override public void visitMaxs(int stack, int locals) {
                        // Append after legacy handlers so their catch/fallback behavior stays first.
                        super.visitLabel(end);
                        super.visitTryCatchBlock(start, end, handler, "java/lang/Throwable");
                        super.visitLabel(handler);
                        // Legacy handlers may deliberately drop all argument locals to TOP.
                        // Cleanup uses only the Throwable stack, so require no locals here.
                        super.visitFrame(Opcodes.F_NEW, 0, new Object[0], 1, new Object[] {"java/lang/Throwable"});
                        super.visitInsn(Opcodes.DUP);
                        call(load ? "abortLoad" : "abortSave", "(Ljava/lang/Throwable;)V");
                        super.visitInsn(Opcodes.ATHROW);
                        super.visitMaxs(stack, locals);
                    }
                };
            }
        }, ClassReader.EXPAND_FRAMES);
        for (int count : counts) if (count != 1) throw new IOException("Expected exact load/save/close/copy sites; rejected before defineClass");
        return writer.toByteArray();
    }
}
