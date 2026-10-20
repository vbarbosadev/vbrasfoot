package br.vbrasfoot;

import java.nio.file.*;
import java.net.*;
import java.util.*;
import java.lang.reflect.Field;
import javax.tools.ToolProvider;

/** Generated owned classes, never original game classes. Numeric arguments are diagnostic, not inclusion. */
public final class FinanceFieldEmissionSelfTest {
    private static int checks;
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    private static <T> T edt(java.util.concurrent.Callable<T> action) throws Exception { return CareerBridge.onEdt(action); }
    public static void main(String[] args) throws Exception {
        Path base = Files.createDirectories(Path.of(System.getProperty("java.io.tmpdir"), "opencode"));
        Path root = Files.createTempDirectory(base, "finance-numeric-emission-"); Path source = Files.createDirectory(root.resolve("best"));
        Path clubSource = source.resolve("ah.java"), ledgerSource = source.resolve("C.java");
        Files.writeString(clubSource, "package best; public class ah { public long nb; }");
        StringBuilder ledgerText = new StringBuilder("package best; public class C {");
        for (String name : new TreeSet<>(FinanceDelta.FIELDS)) ledgerText.append("public long ").append(name).append(';');
        Files.writeString(ledgerSource, ledgerText.append('}').toString());
        check(ToolProvider.getSystemJavaCompiler().run(null, null, null, "-d", root.toString(), clubSource.toString(), ledgerSource.toString()) == 0, "owned fixture compilation");
        try (var loader = new URLClassLoader(new URL[]{root.toUri().toURL()}, null)) {
            var clubClass = Class.forName("best.ah", true, loader); var ledgerClass = Class.forName("best.C", true, loader);
            Object club = clubClass.getConstructor().newInstance(), ledger = ledgerClass.getConstructor().newInstance();
            Field balance = clubClass.getField("nb");
            FinanceSerializationTracker.Target target = new FinanceSerializationTracker.Target() {
                public FinanceSerializationTracker.Sample sample(Object primary) { return FinanceSerializationTracker.Sample.MATCHED_EXPECTED_STATE; }
                public FinanceSerializationTracker.Argument emitted(Field field, Object receiver, long value) {
                    if (receiver != club && receiver != ledger) return FinanceSerializationTracker.Argument.IRRELEVANT;
                    return value == 7 ? FinanceSerializationTracker.Argument.MATCHED : FinanceSerializationTracker.Argument.MISMATCH;
                }
            };
            var tracker = new FinanceSerializationTracker(); Object primary = new Object();
            tracker.observe(UUID.randomUUID(), new CareerCommandJournal.Intent("owned", CareerCommandGate.Operation.FINANCE_CREDIT, "a".repeat(64)), target, null);
            var missing = edt(() -> tracker.finish(tracker.begin(primary, null), primary));
            check(missing.operations().get(0).fields().status() == FinanceSerializationTracker.Emission.MISSING, "matching root samples alone never claim numeric emissions");
            var partial = edt(() -> tracker.begin(primary, null));
            edt(() -> { tracker.emitted(partial, balance, club, 7, "UnsafeLongField"); return null; });
            check(edt(() -> tracker.finish(partial, primary)).operations().get(0).fields().status() == FinanceSerializationTracker.Emission.PARTIAL, "one argument is partial not complete");
            var full = edt(() -> tracker.begin(primary, null));
            edt(() -> {
                tracker.emitted(full, balance, new Object(), 7, "UnsafeLongField");
                tracker.emitted(full, balance, club, 7, "UnsafeLongField");
                for (var field : ledgerClass.getFields()) tracker.emitted(full, field, ledger, 7, "ObjectLongField");
                return null;
            });
            var fields = edt(() -> tracker.finish(full, primary)).operations().get(0).fields();
            check(fields.status() == FinanceSerializationTracker.Emission.EXPECTED_NUMERIC_ARGUMENTS_OBSERVED && fields.observed() == 15, "exact unique fifteen arguments, unrelated receiver ignored");
            check(fields.routes().equals(Set.of("UnsafeLongField", "ObjectLongField")), "actual routes retained without numeric values");
            boolean immutable = false; try { fields.routes().clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
            check(immutable, "published route set is immutable");
            var mismatch = edt(() -> tracker.begin(primary, null));
            edt(() -> { tracker.emitted(mismatch, balance, club, -7, "AsmLongField"); return null; });
            check(edt(() -> tracker.finish(mismatch, primary)).operations().get(0).fields().status() == FinanceSerializationTracker.Emission.VALUE_MISMATCH, "exact argument mismatch even with matching root samples");
            var duplicate = edt(() -> tracker.begin(primary, null));
            edt(() -> { tracker.emitted(duplicate, balance, club, 7, "UnsafeLongField"); tracker.emitted(duplicate, balance, club, 7, "UnsafeLongField"); return null; });
            check(edt(() -> tracker.finish(duplicate, primary)).operations().get(0).fields().status() == FinanceSerializationTracker.Emission.AMBIGUOUS, "duplicate emission cannot count twice or silently overwrite");
            var wrongThread = edt(() -> tracker.begin(primary, null)); tracker.emitted(wrongThread, balance, club, 7, "UnsafeLongField");
            check(edt(() -> tracker.finish(wrongThread, primary)).operations().get(0).fields().status() == FinanceSerializationTracker.Emission.UNAVAILABLE, "off EDT invalidates numeric coverage");
            var failure = edt(() -> tracker.begin(primary, null)); tracker.emissionFailed(failure);
            check(edt(() -> tracker.finish(failure, primary)).operations().get(0).fields().status() == FinanceSerializationTracker.Emission.UNAVAILABLE, "failed callback/output attribution never positive");
            var unsupported = edt(() -> tracker.begin(primary, null));
            edt(() -> { tracker.emitted(unsupported, balance, club, 7, "custom"); return null; });
            check(edt(() -> tracker.finish(unsupported, primary)).operations().get(0).fields().status() == FinanceSerializationTracker.Emission.UNAVAILABLE, "unknown route never accepted");
            var stale = edt(() -> tracker.begin(primary, null)); tracker.invalidate();
            edt(() -> { tracker.emitted(stale, balance, club, 7, "UnsafeLongField"); return null; });
            check(!edt(() -> tracker.finish(stale, primary)).validWindow(), "stale lifecycle cannot collect emissions");
            tracker.close(); java.lang.ref.Reference.reachabilityFence(target);
        }
        System.out.println("PASS: " + checks + " numeric field emission checks; owned fixture " + root + "; no inclusion authority.");
    }
}
