import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;

/** Static candidates only: does not invoke any game command, calendar or transfer routine. */
public final class MapGameplayBoundaries {
    private static final String GAME_SHA = "2fa65476a92611419ce28c0cfd158a0c7f741af235aa988b0bed00c44443bb1c";
    static final Set<String> TARGETS = Set.of("best/ah.e(J)V", "best/ah.f(J)V", "best/ah.v(II)V", "best/ah.w(II)V",
            "best/C.b(J)V", "best/C.h(II)V", "best/C.i(II)V", "field:best/ah.nb:J", "best/f.az()I",
            "best/ah.a(Lbest/F;Z)Z", "best/ah.b(Lbest/F;Z)Z");
    private MapGameplayBoundaries() { }
    static String role(String reference) throws IOException {
        String[] fields = reference.split("\t", -1);
        if (fields.length != 7 || !Set.of("invoke", "putstatic", "putfield").contains(fields[0])
                || !fields[3].matches("\\d+") || !fields[4].matches("previousOpcode=-?\\d+")
                || !fields[5].matches("class.sha256=[a-f0-9]{64}") || !fields[6].matches("code.sha256=[a-f0-9]{64}"))
            throw new IOException("Invalid reference; no partial gameplay classification");
        if (fields[0].equals("putfield") && fields[1].equals("best/ah.nbJ")) return "balance-write-not-complete-financial-command";
        if (!fields[0].equals("invoke")) return null;
        return switch (fields[1]) {
            case "best/ah.e(J)V" -> "raw-balance-setter-not-bookkeeping-command";
            case "best/ah.f(J)V" -> "balance-subtraction-with-conditional-ledger-candidate";
            case "best/ah.v(II)V" -> "credit-with-conditional-ledger-candidate";
            case "best/ah.w(II)V" -> "debit-with-conditional-ledger-candidate";
            case "best/C.b(J)V", "best/C.h(II)V", "best/C.i(II)V" -> "financial-ledger-update-not-balance-command";
            case "best/f.az()I" -> "shared-calendar-progression-not-day-event";
            case "best/ah.a(Lbest/F;Z)Z", "best/ah.b(Lbest/F;Z)Z" -> "transfer-selection-predicate-candidate-not-commit";
            default -> null;
        };
    }
    private static String hash(Path path) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))); }
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IOException("Use executable new-output.tsv");
        Path executable = Path.of(args[0]).toRealPath(), output = Path.of(args[1]).toAbsolutePath();
        if (Files.exists(output) || !hash(executable).equals(GAME_SHA)) throw new IOException("Existing output or unknown executable; refused");
        List<String> findings = new ArrayList<>(); Map<String, Integer> counts = new TreeMap<>(); int classes = 0;
        try (var zip = new ZipFile(executable.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement(); if (!entry.getName().endsWith(".class")) continue;
                if (entry.getSize() < 0 || entry.getSize() > 1024 * 1024) throw new IOException("Oversized class");
                try (var stream = zip.getInputStream(entry)) {
                    for (var reference : FindCareerReferences.inspect(stream.readAllBytes(), TARGETS)) {
                        String role = role(reference);
                        if (role != null) { findings.add(role + "\t" + reference); counts.merge(role, 1, Integer::sum); }
                    }
                }
                classes++;
            }
        }
        if (!hash(executable).equals(GAME_SHA)) throw new IOException("Input changed; refused");
        Collections.sort(findings);
        try (var writer = Files.newBufferedWriter(output, StandardOpenOption.CREATE_NEW)) {
            writer.write("# executable.sha256=" + GAME_SHA + "; classes=" + classes + "; static principal-ZIP direct references only\n");
            writer.write("# No target execution, financial correctness, category interpretation, interception or complete transfer/calendar coverage established\n");
            writer.write("# role\toperation\ttarget\tcaller\tbytecodeOffset\tpreviousOpcode\tclass.sha256\tcode.sha256\n");
            for (var finding : findings) { writer.write(finding); writer.newLine(); }
        }
        System.out.println("classes=" + classes + " candidateReferences=" + findings.size()); counts.forEach((role, count) -> System.out.println(role + "=" + count));
        System.out.println("Coverage: static candidates, not an implemented or validated game command/event API.");
    }
}
