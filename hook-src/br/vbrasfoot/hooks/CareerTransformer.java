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
        int[] counts = new int[9];
        reader.accept(new ClassVisitor(Opcodes.ASM8, writer) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                MethodVisitor method = super.visitMethod(access, name, descriptor, signature, exceptions);
                boolean load = name.equals("c") && descriptor.equals("(Ljava/lang/String;Z)Z");
                boolean save = name.equals("ae") && descriptor.equals("(Ljava/lang/String;)V");
                boolean setter = name.equals("a") && descriptor.equals("(Lbest/f;)V");
                boolean reset = name.equals("<init>") && descriptor.equals("()V");
                if (reset || setter) {
                    if (reset) counts[5]++;
                    return new MethodVisitor(Opcodes.ASM8, method) {
                        final Label start = new Label(), end = new Label(), handler = new Label();
                        boolean started;
                        private void begin() {
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, BRIDGE, "enterReset", "()V", false);
                            super.visitLabel(start); started = true;
                        }
                        @Override public void visitCode() { super.visitCode(); if (setter) begin(); }
                        @Override public void visitMethodInsn(int op, String owner, String called, String desc, boolean iface) {
                            super.visitMethodInsn(op, owner, called, desc, iface);
                            if (reset && !started && op == Opcodes.INVOKESPECIAL && owner.equals("java/lang/Object") && called.equals("<init>")) begin();
                        }
                        @Override public void visitFieldInsn(int op, String owner, String field, String desc) {
                            super.visitFieldInsn(op, owner, field, desc);
                            if (op == Opcodes.PUTSTATIC && owner.equals("c/a") && (field.equals("SR") || field.equals("afQ"))) {
                                counts[6]++;
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, BRIDGE, "rootAssigned", "()V", false);
                            }
                        }
                        @Override public void visitInsn(int op) {
                            if (op == Opcodes.RETURN) super.visitMethodInsn(Opcodes.INVOKESTATIC, BRIDGE, setter ? "rootSetterFinished" : "rootsReset", "()V", false);
                            super.visitInsn(op);
                        }
                        @Override public void visitMaxs(int stack, int locals) {
                            if (!started) throw new IllegalStateException("Constructor/reset boundary not validated");
                            super.visitLabel(end);
                            super.visitTryCatchBlock(start, end, handler, "java/lang/Throwable");
                            super.visitLabel(handler);
                            super.visitFrame(Opcodes.F_NEW, 0, new Object[0], 1, new Object[] {"java/lang/Throwable"});
                            super.visitInsn(Opcodes.DUP);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, BRIDGE, "abortReset", "(Ljava/lang/Throwable;)V", false);
                            super.visitInsn(Opcodes.ATHROW);
                            super.visitMaxs(stack, locals);
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
                        if (save && owner.equals("java/io/FileOutputStream") && name.equals("close") && desc.equals("()V")) {
                            counts[7]++; call("metadataClosed", "()V");
                        }
                        if (load && owner.equals("best/f") && name.equals("ap") && desc.equals("()V")) {
                            counts[8]++; call("loadInitializationPathFinished", "()V");
                        }
                        if (save && owner.equals("java/nio/file/Files") && name.equals("copy")
                                && desc.equals("(Ljava/nio/file/Path;Ljava/nio/file/Path;[Ljava/nio/file/CopyOption;)Ljava/nio/file/Path;")) {
                            counts[4]++; call("backupCopied", "()V");
                        }
                    }
                    @Override public void visitFieldInsn(int op, String owner, String name, String desc) {
                        super.visitFieldInsn(op, owner, name, desc);
                        if (load && op == Opcodes.PUTSTATIC && owner.equals("c/a") && (name.equals("SR") || name.equals("afQ"))) {
                            counts[6]++; call("rootAssigned", "()V");
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
        for (int i = 0; i < 6; i++) if (counts[i] != 1) throw new IOException("Expected exact load/save/close/copy sites; rejected before defineClass");
        // Real c.a has five observed root writes (two constructor + two decoder + setter).
        // The private synthetic fixture does not define those original fields.
        if (reader.getClassName().equals("c/a") && counts[6] != 5) throw new IOException("Root-assignment sites changed; refused");
        if (reader.getClassName().equals("c/a") && (counts[7] != 1 || counts[8] != 1)) throw new IOException("Metadata/load initialization sites changed; refused");
        return writer.toByteArray();
    }
}
