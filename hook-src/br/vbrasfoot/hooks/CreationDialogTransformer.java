package br.vbrasfoot.hooks;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.*;
import jdk.internal.org.objectweb.asm.*;

/** Fingerprinted bV oq/pl diagnostics; observes the original dialog return without calling a dialog. */
public final class CreationDialogTransformer {
    private CreationDialogTransformer() { }
    public static byte[] transform(byte[] original) throws Exception {
        if (Runtime.version().feature() != 17 && Runtime.version().feature() != 21) throw new IOException("Sonda suporta somente Java17/21");
        if (!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(original))
                .equals("2845a9dee433a4757074e728b88bba7089790f62216df41772dcad95cad05314")
                || !new ClassReader(original).getClassName().equals("a/bV")) throw new IOException("Fingerprint/identidade bV divergente");
        return transformFixture(CreationPathTransformer.transform("a.bV", original), true);
    }
    static byte[] transformFixture(byte[] original, boolean roots) throws IOException {
        var reader = new ClassReader(original); var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        Map<String, Integer> found = new HashMap<>(), returns = new HashMap<>(); int[] dialogs = {0};
        reader.accept(new ClassVisitor(Opcodes.ASM8, writer) {
            @Override public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                var output = super.visitMethod(access, name, desc, signature, exceptions);
                if (!Set.of("oq", "pl").contains(name) || !desc.equals("()V")) return output;
                if (access != Opcodes.ACC_PUBLIC) throw new IllegalArgumentException("Unexpected dialog method access");
                found.merge(name, 1, Integer::sum);
                return new MethodVisitor(Opcodes.ASM8, output) {
                    final Label start = new Label(), end = new Label(), handler = new Label();
                    void marker(String stage, boolean result) {
                        // Keep original result below the marker arguments; never read legacy locals.
                        if (result) { super.visitInsn(Opcodes.DUP); super.visitLdcInsn(name); super.visitInsn(Opcodes.SWAP);
                            super.visitLdcInsn(stage); super.visitInsn(Opcodes.SWAP); }
                        else { super.visitLdcInsn(name); super.visitLdcInsn(stage); super.visitInsn(Opcodes.ICONST_0); }
                        if (roots) { super.visitFieldInsn(Opcodes.GETSTATIC, "c/a", "SR", "Lbest/f;");
                            super.visitFieldInsn(Opcodes.GETSTATIC, "c/a", "afQ", "Lbest/ay;"); }
                        else { super.visitInsn(Opcodes.ACONST_NULL); super.visitInsn(Opcodes.ACONST_NULL); }
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, "br/vbrasfoot/HookProbe", "creationDialog",
                                "(Ljava/lang/String;Ljava/lang/String;ILjava/lang/Object;Ljava/lang/Object;)V", false);
                    }
                    @Override public void visitCode() { super.visitCode(); marker("ENTER", false); super.visitLabel(start); }
                    @Override public void visitInsn(int op) {
                        if (op == Opcodes.RETURN) { returns.merge(name, 1, Integer::sum); marker("RETURN", false); }
                        super.visitInsn(op);
                    }
                    @Override public void visitMethodInsn(int op, String owner, String called, String descriptor, boolean iface) {
                        super.visitMethodInsn(op, owner, called, descriptor, iface);
                        boolean dialog = op == Opcodes.INVOKESTATIC && owner.equals(roots ? "javax/swing/JOptionPane" : reader.getClassName())
                                && called.equals(roots ? "showConfirmDialog" : "confirm")
                                && descriptor.equals(roots ? "(Ljava/awt/Component;Ljava/lang/Object;Ljava/lang/String;I)I" : "()I");
                        if (dialog) { if (!name.equals("pl")) throw new IllegalArgumentException("Unexpected dialog site");
                            dialogs[0]++; marker("DIALOG_RETURN", true); }
                    }
                    @Override public void visitMaxs(int stack, int locals) {
                        super.visitLabel(end); super.visitTryCatchBlock(start, end, handler, "java/lang/Throwable"); super.visitLabel(handler);
                        super.visitFrame(Opcodes.F_NEW, 0, new Object[0], 1, new Object[] {"java/lang/Throwable"});
                        marker("FAILED", false); super.visitInsn(Opcodes.ATHROW); super.visitMaxs(stack, locals);
                    }
                };
            }
        }, ClassReader.EXPAND_FRAMES);
        for (String name : List.of("oq", "pl")) if (found.getOrDefault(name, 0) != 1 || returns.getOrDefault(name, 0) != 1)
            throw new IOException("Dialog method/return changed: " + name);
        if (dialogs[0] != 1) throw new IOException("Dialog sites changed");
        return writer.toByteArray();
    }
}
