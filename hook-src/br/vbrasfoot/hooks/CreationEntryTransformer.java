package br.vbrasfoot.hooks;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.*;
import jdk.internal.org.objectweb.asm.*;

/** Links original settings preparation to the exact bV assigned by best.S; no constructor/schema edits. */
public final class CreationEntryTransformer {
    private CreationEntryTransformer() { }
    public static byte[] transform(String name, byte[] original) throws Exception {
        if (Runtime.version().feature() != 17 && Runtime.version().feature() != 21) throw new IOException("Sonda suporta somente Java17/21");
        String sha = switch (name) {
            case "a.hU" -> "37fb17d876aab1263756bca97c3f9488007b7afe59f8c68494cf711a0f61a234";
            case "best.S" -> "5214a1a13d24c947fcad6a0939168ba132e43535c7aaa5f01230685fc19d2643";
            default -> throw new IOException("Classe de origem nao permitida");
        };
        if (!sha.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(original)))
                || !new ClassReader(original).getClassName().equals(name.replace('.', '/'))) throw new IOException("Fingerprint/identidade de origem divergente");
        return transformFixture(original, name.equals("a.hU"), true);
    }
    static byte[] transformFixture(byte[] original, boolean preparation, boolean roots) throws IOException {
        var reader = new ClassReader(original); var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        int[] methods = {0}, returns = {0}, settings = {0}, panels = {0}, assignments = {0};
        reader.accept(new ClassVisitor(Opcodes.ASM8, writer) {
            @Override public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                var output = super.visitMethod(access, name, desc, signature, exceptions);
                if (!name.equals(preparation ? "oq" : "aY") || !desc.equals(preparation ? "()V" : "(I)Ljavax/swing/JPanel;")) return output;
                if (access != (preparation ? Opcodes.ACC_PRIVATE : Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)) throw new IllegalArgumentException("Unexpected origin method access");
                methods[0]++;
                return new MethodVisitor(Opcodes.ASM8, output) {
                    final Label start = new Label(), end = new Label(), handler = new Label();
                    void marker(String stage, boolean panel) {
                        if (panel) { super.visitLdcInsn(stage); super.visitInsn(Opcodes.SWAP); }
                        else { super.visitLdcInsn(stage); super.visitInsn(Opcodes.ACONST_NULL); }
                        if (roots) { super.visitFieldInsn(Opcodes.GETSTATIC, "c/a", "SR", "Lbest/f;");
                            super.visitFieldInsn(Opcodes.GETSTATIC, "c/a", "afQ", "Lbest/ay;"); }
                        else { super.visitInsn(Opcodes.ACONST_NULL); super.visitInsn(Opcodes.ACONST_NULL); }
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, "br/vbrasfoot/HookProbe", "creationEntry",
                                "(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V", false);
                    }
                    @Override public void visitCode() { super.visitCode(); if (preparation) { marker("ENTER", false); super.visitLabel(start); } }
                    @Override public void visitInsn(int op) {
                        if (preparation && op == Opcodes.RETURN) { returns[0]++; marker("RETURN", false); }
                        super.visitInsn(op);
                    }
                    @Override public void visitMethodInsn(int op, String owner, String called, String descriptor, boolean iface) {
                        super.visitMethodInsn(op, owner, called, descriptor, iface);
                        if (!preparation || op != Opcodes.INVOKESTATIC) return;
                        if (owner.equals(roots ? "best/p" : reader.getClassName()) && called.equals(roots ? "dg" : "settings") && descriptor.equals("()V")) {
                            settings[0]++; marker("SETTINGS_RETURN", false);
                        }
                        if (owner.equals(roots ? "best/S" : reader.getClassName()) && called.equals("aY") && descriptor.equals("(I)Ljavax/swing/JPanel;")) {
                            panels[0]++; marker("PANEL_RETURN", false); // Keep the original (possibly null) result on stack.
                        }
                    }
                    @Override public void visitFieldInsn(int op, String owner, String field, String descriptor) {
                        boolean selected = !preparation && op == Opcodes.PUTSTATIC && owner.equals(roots ? "best/S" : reader.getClassName())
                                && field.equals("gL") && descriptor.equals(roots ? "La/bV;" : "Ljava/lang/Object;");
                        if (selected) { assignments[0]++; super.visitInsn(Opcodes.DUP); }
                        super.visitFieldInsn(op, owner, field, descriptor);
                        if (selected) marker("PANEL_ASSIGNED", true);
                    }
                    @Override public void visitMaxs(int stack, int locals) {
                        if (preparation) {
                            super.visitLabel(end); super.visitTryCatchBlock(start, end, handler, "java/lang/Throwable"); super.visitLabel(handler);
                            super.visitFrame(Opcodes.F_NEW, 0, new Object[0], 1, new Object[] {"java/lang/Throwable"});
                            marker("FAILED", false); super.visitInsn(Opcodes.ATHROW);
                        }
                        super.visitMaxs(stack, locals);
                    }
                };
            }
        }, ClassReader.EXPAND_FRAMES);
        if (methods[0] != 1 || (preparation ? returns[0] != 1 || settings[0] != 1 || panels[0] != 1 : assignments[0] != 1))
            throw new IOException("Origin method/return/settings/panel/assignment changed");
        return writer.toByteArray();
    }
}
