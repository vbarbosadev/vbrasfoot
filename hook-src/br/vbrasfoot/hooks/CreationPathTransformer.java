package br.vbrasfoot.hooks;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.*;
import jdk.internal.org.objectweb.asm.*;

/** Candidate UI-path diagnostics only. No readiness, save identity, or public lifecycle event. */
public final class CreationPathTransformer {
    private static final String BRIDGE = "br/vbrasfoot/HookProbe";
    private CreationPathTransformer() { }
    public static byte[] transform(String name, byte[] original) throws Exception {
        if (Runtime.version().feature() != 17 && Runtime.version().feature() != 21) throw new IOException("Sonda suporta somente Java17/21");
        String expected;
        Map<String, Integer> methods;
        switch (name) {
            case "a.bV" -> {
                expected = "2845a9dee433a4757074e728b88bba7089790f62216df41772dcad95cad05314";
                methods = Map.of("AB", Opcodes.ACC_PUBLIC);
            }
            case "a.cR" -> {
                expected = "ae233636feed8f34d56d701545b756eb0d0f701aa5a1ee5c7e72679b5905ecca";
                methods = Map.of("AC", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "pn", Opcodes.ACC_PUBLIC);
            }
            default -> throw new IOException("Classe de candidato nao permitida");
        }
        String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(original));
        if (!expected.equals(actual)) throw new IOException("Fingerprint do candidato incompativel: " + name);
        if (!new ClassReader(original).getClassName().equals(name.replace('.', '/'))) throw new IOException("Identidade de classe divergente");
        return transformFixture(original, methods, true);
    }
    static byte[] transformFixture(byte[] original, Map<String, Integer> selected) throws IOException {
        return transformFixture(original, selected, false);
    }
    private static byte[] transformFixture(byte[] original, Map<String, Integer> selected, boolean captureRoots) throws IOException {
        ClassReader reader = new ClassReader(original);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        Map<String, Integer> found = new HashMap<>(), returns = new HashMap<>();
        reader.accept(new ClassVisitor(Opcodes.ASM8, writer) {
            @Override public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                MethodVisitor method = super.visitMethod(access, name, desc, signature, exceptions);
                if (!selected.containsKey(name) || !desc.equals("()V")) return method;
                if (access != selected.get(name)) throw new IllegalArgumentException("Acesso inesperado: " + name);
                found.merge(name, 1, Integer::sum);
                String label = reader.getClassName().replace('/', '.') + "." + name;
                return new MethodVisitor(Opcodes.ASM8, method) {
                    final Label start = new Label(), end = new Label(), handler = new Label();
                    void marker(String called) {
                        super.visitLdcInsn(label);
                        if (captureRoots) {
                            super.visitFieldInsn(Opcodes.GETSTATIC, "c/a", "SR", "Lbest/f;");
                            super.visitFieldInsn(Opcodes.GETSTATIC, "c/a", "afQ", "Lbest/ay;");
                        } else { super.visitInsn(Opcodes.ACONST_NULL); super.visitInsn(Opcodes.ACONST_NULL); }
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, BRIDGE, called, "(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V", false);
                    }
                    @Override public void visitCode() {
                        super.visitCode(); marker("creationPathStarted"); super.visitLabel(start);
                    }
                    @Override public void visitInsn(int op) {
                        if (op == Opcodes.RETURN) { returns.merge(name, 1, Integer::sum); marker("creationPathReturned"); }
                        super.visitInsn(op);
                    }
                    @Override public void visitMaxs(int stack, int locals) {
                        super.visitLabel(end);
                        super.visitTryCatchBlock(start, end, handler, "java/lang/Throwable");
                        super.visitLabel(handler);
                        super.visitFrame(Opcodes.F_NEW, 0, new Object[0], 1, new Object[] {"java/lang/Throwable"});
                        marker("creationPathFailed"); // Preserve the original Throwable on the stack.
                        super.visitInsn(Opcodes.ATHROW);
                        super.visitMaxs(stack, locals);
                    }
                };
            }
        }, ClassReader.EXPAND_FRAMES);
        for (String name : selected.keySet()) {
            if (found.getOrDefault(name, 0) != 1 || returns.getOrDefault(name, 0) != 1) {
                throw new IOException("Metodo/retorno candidato divergente: " + name);
            }
        }
        return writer.toByteArray();
    }
}
