import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;

/** Read-only, source-bound candidate map. Roles are analyst labels, never lifecycle proof. */
public final class MapCareerBoundaries {
    private static final String GAME_SHA = "2fa65476a92611419ce28c0cfd158a0c7f741af235aa988b0bed00c44443bb1c";
    static final Set<String> TARGETS = Set.of("best/f.V()V", "best/f.aa()V", "best/f.ap()V", "best/f.at()V", "best/f.ax()V",
            "best/S.aY(I)Ljavax/swing/JPanel;", "best/h.mh()V", "best/h.mi()V", "a/cR.oR()V",
            "a/cS.<init>(La/cR;)V", "a/hk.oR()V", "a/ho.<init>(La/hk;)V", "java/util/Timer.schedule(Ljava/util/TimerTask;J)V",
            "javax/swing/JFrame.setDefaultCloseOperation(I)V");
    private MapCareerBoundaries() { }
    private static String hash(Path file) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }
    static String role(String reference) throws IOException {
        String[] fields = reference.split("\t", -1);
        if (fields.length != 7 || !Set.of("invoke", "putstatic", "putfield").contains(fields[0])
                || !fields[3].matches("\\d+") || !fields[4].matches("previousOpcode=-?\\d+")
                || !fields[5].matches("class.sha256=[a-f0-9]{64}") || !fields[6].matches("code.sha256=[a-f0-9]{64}")) {
            throw new IOException("Malformed static reference; no partial classification");
        }
        String target = fields[1];
        if (fields[0].equals("putstatic") && (target.equals("c/a.SRLbest/f;") || target.equals("c/a.afQLbest/ay;"))) return "root-write-not-ready-or-unload";
        if (!fields[0].equals("invoke")) return null;
        if (target.equals("c/a.<init>()V")) return "root-reset-caller";
        if (target.equals("c/a.c(Ljava/lang/String;Z)Z")) return "existing-read-caller";
        if (target.equals("c/a.ae(Ljava/lang/String;)V")) return "save-caller";
        if (target.equals("c/a.a(Lbest/f;)V")) return "root-setter-caller";
        if (target.equals("java/lang/System.exit(I)V")) return "process-exit-not-unload";
        if (target.equals("javax/swing/JFrame.dispose()V") || target.equals("a/gq.dispose()V")) return "window-close-not-unload";
        if (target.equals("javax/swing/JFrame.setDefaultCloseOperation(I)V")) return "window-close-policy-argument-unresolved";
        if (target.equals("best/S.aY(I)Ljavax/swing/JPanel;")) return "panel-switch-not-ready";
        if (target.equals("best/f.V()V") || target.equals("best/f.aa()V") || target.equals("best/f.ap()V") || target.equals("best/f.at()V") || target.equals("best/f.ax()V")) return "shared-progression-call";
        if (target.equals("a/cR.oR()V") || target.equals("a/cS.<init>(La/cR;)V")
                || target.equals("a/hk.oR()V") || target.equals("a/ho.<init>(La/hk;)V")
                || target.equals("java/util/Timer.schedule(Ljava/util/TimerTask;J)V")) return "scheduled-path-candidate";
        if (target.equals("best/h.mh()V") || target.equals("best/h.mi()V")) return "creation-entry-candidate";
        // Graphics.dispose and generic dialog close are intentionally not career boundaries.
        return null;
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IOException("Use executable new-output.tsv");
        Path executable = Path.of(args[0]).toRealPath(), output = Path.of(args[1]).toAbsolutePath();
        if (Files.exists(output)) throw new IOException("Choose new output; existing work is never overwritten");
        if (!hash(executable).equals(GAME_SHA)) throw new IOException("Unknown game identity; refused before inspection");
        int classes = 0;
        List<String> results = new ArrayList<>();
        Map<String, Integer> counts = new TreeMap<>();
        try (var zip = new ZipFile(executable.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) continue;
                if (entry.getSize() < 0 || entry.getSize() > 1024 * 1024) throw new IOException("Class exceeds inspection bound");
                try (var stream = zip.getInputStream(entry)) {
                    for (var reference : FindCareerReferences.inspect(stream.readAllBytes(), TARGETS)) {
                        String role = role(reference);
                        if (role != null) { results.add(role + "\t" + reference); counts.merge(role, 1, Integer::sum); }
                    }
                }
                classes++;
            }
        }
        if (!hash(executable).equals(GAME_SHA)) throw new IOException("Input changed; map refused");
        Collections.sort(results);
        try (var writer = Files.newBufferedWriter(output, StandardOpenOption.CREATE_NEW)) {
            writer.write("# executable.sha256=" + GAME_SHA + "; classes=" + classes + "; static direct references in principal ZIP only\n");
            writer.write("# Roles are analyst candidates, NOT runtime readiness/unload proof; excludes reflection/JNI/nested libraries and asynchronous causal linkage\n");
            writer.write("# role\toperation\ttarget\tcaller\tbytecodeOffset\tpreviousOpcode\tclass.sha256\tcode.sha256\n");
            for (var line : results) { writer.write(line); writer.newLine(); }
        }
        System.out.println("classes=" + classes + " candidateReferences=" + results.size());
        counts.forEach((role, count) -> System.out.println(role + "=" + count));
        System.out.println("Coverage: static candidates only; no game execution, no complete lifecycle or bounded-negative absence proof.");
    }
}
