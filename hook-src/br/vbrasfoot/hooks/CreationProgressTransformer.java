package br.vbrasfoot.hooks;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.*;
import jdk.internal.org.objectweb.asm.*;

/** Bounded candidate-context tracing of original method bodies; no serialized schema change or readiness event. */
public final class CreationProgressTransformer {
    private static final String SHA = "dbd5bd8633e3a1c2f211e8bd6c322db8f7401c5ccf7cc622cc4050f0624df71b";
    private static final Map<String, Integer> METHODS = Map.of("V", Opcodes.ACC_PUBLIC, "aa", Opcodes.ACC_PRIVATE,
            "ap", Opcodes.ACC_PUBLIC, "at", Opcodes.ACC_PUBLIC, "ax", Opcodes.ACC_PUBLIC);
    private CreationProgressTransformer() { }
    public static byte[] transform(byte[] original) throws Exception {
        int java = Runtime.version().feature();
        if (java != 17 && java != 21) throw new IOException("Sonda suporta somente Java17/21");
        if (!SHA.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(original)))
                || !new ClassReader(original).getClassName().equals("best/f")) throw new IOException("Fingerprint/identidade best.f divergente; recusado");
        return transformFixture(original, true);
    }
    static byte[] transformFixture(byte[] original, boolean roots) throws IOException {
        var reader = new ClassReader(original); var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        Map<String, Integer> methods = new HashMap<>(), returns = new HashMap<>(), panels = new HashMap<>();
        reader.accept(new ClassVisitor(Opcodes.ASM8, writer) {
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                var method = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!METHODS.containsKey(name) || !descriptor.equals("()V")) return method;
                if (access != METHODS.get(name)) throw new IllegalArgumentException("Unexpected progression method access: " + name);
                methods.merge(name, 1, Integer::sum);
                return new MethodVisitor(Opcodes.ASM8, method) {
                    final Label start = new Label(), end = new Label(), handler = new Label();
                    void marker(String stage, boolean stackedPanel) {
                        if (stackedPanel) {
                            super.visitInsn(Opcodes.DUP); super.visitLdcInsn(name); super.visitInsn(Opcodes.SWAP);
                            super.visitLdcInsn(stage); super.visitInsn(Opcodes.SWAP);
                        } else { super.visitLdcInsn(name); super.visitLdcInsn(stage); super.visitInsn(Opcodes.ICONST_M1); }
                        // Legacy stack maps can drop this to TOP before return/catch.
                        // Observe receiver only at entry; never depend on discarded locals.
                        if (stage.equals("ENTER")) super.visitVarInsn(Opcodes.ALOAD, 0);
                        else super.visitInsn(Opcodes.ACONST_NULL);
                        if (roots) {
                            super.visitFieldInsn(Opcodes.GETSTATIC, "c/a", "SR", "Lbest/f;");
                            super.visitFieldInsn(Opcodes.GETSTATIC, "c/a", "afQ", "Lbest/ay;");
                        } else { super.visitInsn(Opcodes.ACONST_NULL); super.visitInsn(Opcodes.ACONST_NULL); }
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, "br/vbrasfoot/HookProbe", "creationProgress",
                                "(Ljava/lang/String;Ljava/lang/String;ILjava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V", false);
                    }
                    public void visitCode() { super.visitCode(); marker("ENTER", false); super.visitLabel(start); }
                    public void visitInsn(int opcode) {
                        if (opcode == Opcodes.RETURN) { returns.merge(name, 1, Integer::sum); marker("RETURN", false); }
                        super.visitInsn(opcode);
                    }
                    public void visitMethodInsn(int opcode, String owner, String called, String desc, boolean iface) {
                        boolean panel = opcode == Opcodes.INVOKESTATIC && owner.equals(roots ? "best/S" : reader.getClassName())
                                && called.equals(roots ? "aY" : "panel") && desc.equals("(I)Ljavax/swing/JPanel;");
                        if (panel) { panels.merge(name, 1, Integer::sum); marker("PANEL_REQUEST", true); }
                        super.visitMethodInsn(opcode, owner, called, desc, iface);
                        if (panel) marker("PANEL_RETURN", false); // Original JPanel remains on the operand stack.
                    }
                    public void visitMaxs(int stack, int locals) {
                        super.visitLabel(end); super.visitTryCatchBlock(start, end, handler, "java/lang/Throwable");
                        super.visitLabel(handler);
                        super.visitFrame(Opcodes.F_NEW, 0, new Object[0], 1, new Object[] {"java/lang/Throwable"});
                        marker("FAILED", false); super.visitInsn(Opcodes.ATHROW); super.visitMaxs(stack, locals);
                    }
                };
            }
        }, ClassReader.EXPAND_FRAMES);
        for (String name : METHODS.keySet()) {
            int expectedPanels = Set.of("ap", "at", "ax").contains(name) ? 1 : 0;
            if (methods.getOrDefault(name, 0) != 1 || returns.getOrDefault(name, 0) != 1 || panels.getOrDefault(name, 0) != expectedPanels) {
                throw new IOException("Progression method/return/panel sites changed: " + name);
            }
        }
        return writer.toByteArray();
    }
}
