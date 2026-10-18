package br.vbrasfoot.hooks;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.HexFormat;
import jdk.internal.org.objectweb.asm.*;

/** Observes the original Kryo close/catch paths in memory; never changes exception policy. */
public final class OutputCloseTransformer {
    private static final String CLASS_SHA = "73ba15ba16dbcff3a2ce8456de5b70126077c1b62b4128359bcbb2d2dfc4e04a";
    private static final String BRIDGE = "br/vbrasfoot/HookProbe";
    private OutputCloseTransformer() { }

    public static byte[] transform(byte[] original) throws Exception {
        int java = Runtime.version().feature();
        if (java != 17 && java != 21) throw new IOException("Sonda suporta somente Java 17/21");
        if (!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(original)).equals(CLASS_SHA)) {
            throw new IOException("Fingerprint de Kryo Output incompativel; nenhuma transformacao aplicada");
        }
        return transformFixture(original);
    }

    static byte[] transformFixture(byte[] original) throws IOException {
        ClassReader reader = new ClassReader(original);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        int[] counts = new int[5];
        reader.accept(new ClassVisitor(Opcodes.ASM8, writer) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                MethodVisitor method = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!name.equals("close") || !descriptor.equals("()V")) return method;
                if ((access & Opcodes.ACC_STATIC) != 0) throw new IllegalArgumentException("Expected instance close");
                counts[0]++;
                return new MethodVisitor(Opcodes.ASM8, method) {
                    Label catchLabel;
                    boolean atCatch;
                    private void signal(String name) {
                        super.visitVarInsn(Opcodes.ALOAD, 0);
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, BRIDGE, name, "(Ljava/lang/Object;)V", false);
                    }
                    @Override public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
                        super.visitTryCatchBlock(start, end, handler, type);
                        if ("java/io/IOException".equals(type)) { catchLabel = handler; counts[3]++; }
                    }
                    @Override public void visitLabel(Label label) { super.visitLabel(label); atCatch = label == catchLabel; }
                    @Override public void visitVarInsn(int opcode, int variable) {
                        super.visitVarInsn(opcode, variable);
                        if (atCatch && opcode == Opcodes.ASTORE) {
                            atCatch = false; counts[4]++; signal("primaryStreamCloseFailed");
                        }
                    }
                    @Override public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean iface) {
                        super.visitMethodInsn(opcode, owner, name, descriptor, iface);
                        if (name.equals("flush") && descriptor.equals("()V")) counts[1]++;
                        if (owner.equals("java/io/OutputStream") && name.equals("close") && descriptor.equals("()V")) {
                            counts[2]++; signal("primaryStreamClosed");
                        }
                    }
                };
            }
        }, ClassReader.EXPAND_FRAMES);
        for (int count : counts) if (count != 1) throw new IOException("Kryo close/flush/catch sites changed; refused before defineClass");
        return writer.toByteArray();
    }
}
