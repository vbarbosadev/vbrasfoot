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
        Files.writeString(clubSource, "package best; public class ah { public long nb; public C nx; }");
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
                public FinanceSerializationTracker.Argument referenced(Field field, Object receiver, Object value) {
                    if (receiver != club || !field.getName().equals("nx")) return FinanceSerializationTracker.Argument.IRRELEVANT;
                    return value == ledger ? FinanceSerializationTracker.Argument.MATCHED : FinanceSerializationTracker.Argument.MISMATCH;
                }
                public boolean tracksLedger(Object value) { return value == ledger; }
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
            tracker.observe(UUID.randomUUID(), new CareerCommandJournal.Intent("reference", CareerCommandGate.Operation.FINANCE_CREDIT, "b".repeat(64)), target, null);
            var edgeField = clubClass.getField("nx");
            check(edt(() -> tracker.finish(tracker.begin(primary, null), primary)).operations().get(0).edge().status() == FinanceSerializationTracker.Reference.MISSING,
                    "matching root alone cannot infer original ledger argument");
            for (String route : List.of("writeObject", "writeObjectOrNull")) {
                var edge = edt(() -> tracker.begin(primary, null));
                edt(() -> { tracker.referenced(edge, edgeField, new Object(), ledger, route, FinanceSerializationTracker.SerializerKind.FIELD_SERIALIZER);
                    tracker.referenced(edge, edgeField, club, ledger, route, FinanceSerializationTracker.SerializerKind.FIELD_SERIALIZER); return null; });
                var result = edt(() -> tracker.finish(edge, primary)).operations().get(0).edge();
                check(result.status() == FinanceSerializationTracker.Reference.EXPECTED_LEDGER_ARGUMENT_OBSERVED && result.observed() == 1 && result.routes().equals(Set.of(route)),
                        "exact ledger identity after supported route, unrelated receiver ignored: " + route);
                boolean edgeImmutable = false; try { result.routes().clear(); } catch (UnsupportedOperationException expected) { edgeImmutable = true; }
                check(edgeImmutable, "reference route report immutable");
            }
            for (Object wrong : Arrays.asList(null, ledgerClass.getConstructor().newInstance())) {
                var edge = edt(() -> tracker.begin(primary, null));
                edt(() -> { tracker.referenced(edge, edgeField, club, wrong, wrong == null ? "writeClass(null)" : "writeObject", wrong == null
                        ? FinanceSerializationTracker.SerializerKind.NONE : FinanceSerializationTracker.SerializerKind.FIELD_SERIALIZER); return null; });
                check(edt(() -> tracker.finish(edge, primary)).operations().get(0).edge().status() == FinanceSerializationTracker.Reference.REFERENCE_MISMATCH,
                        "null or replacement ledger never matched by values/class");
            }
            var repeatedEdge = edt(() -> tracker.begin(primary, null));
            edt(() -> { for (int i = 0; i < 4; i++) tracker.referenced(repeatedEdge, edgeField, club, ledger, "writeObject", FinanceSerializationTracker.SerializerKind.FIELD_SERIALIZER); return null; });
            var repeatedResult = edt(() -> tracker.finish(repeatedEdge, primary)).operations().get(0).edge();
            check(repeatedResult.status() == FinanceSerializationTracker.Reference.AMBIGUOUS && repeatedResult.observed() == 2, "repeated ledger edge is ambiguous with saturated bounded count");
            var custom = edt(() -> tracker.begin(primary, null));
            edt(() -> { tracker.referenced(custom, edgeField, club, ledger, "writeObject", FinanceSerializationTracker.SerializerKind.OTHER); return null; });
            check(edt(() -> tracker.finish(custom, primary)).operations().get(0).edge().status() == FinanceSerializationTracker.Reference.UNSUPPORTED_SERIALIZER,
                    "matching argument with custom serializer not supported");
            var profiled = edt(() -> tracker.begin(primary, null));
            edt(() -> { tracker.referenced(profiled, edgeField, club, ledger, "writeObject", FinanceSerializationTracker.SerializerKind.FIELD_SERIALIZER,
                    Optional.of(LedgerSerializerProfile.Status.SUPPORTED_SAMPLED_LAYOUT)); return null; });
            check(edt(() -> tracker.finish(profiled, primary)).operations().get(0).qualifications().serializerAfterReference().orElseThrow()
                    == LedgerSerializerProfile.Status.SUPPORTED_SAMPLED_LAYOUT, "manifest records exact supplied post-reference profile only for relevant target");
            var badProfile = edt(() -> tracker.begin(primary, null));
            edt(() -> { tracker.referenced(badProfile, edgeField, club, ledger, "writeObject", FinanceSerializationTracker.SerializerKind.OTHER,
                    Optional.of(LedgerSerializerProfile.Status.MEMORY_REGIONS)); return null; });
            check(edt(() -> tracker.finish(badProfile, primary)).operations().get(0).qualifications().serializerAfterReference().orElseThrow()
                    == LedgerSerializerProfile.Status.MEMORY_REGIONS, "negative region profile retained, not rewritten as absent or supported");
            var duplicateProfile = edt(() -> tracker.begin(primary, null));
            edt(() -> { for (int i = 0; i < 2; i++) tracker.referenced(duplicateProfile, edgeField, club, ledger, "writeObject", FinanceSerializationTracker.SerializerKind.FIELD_SERIALIZER,
                    Optional.of(LedgerSerializerProfile.Status.SUPPORTED_SAMPLED_LAYOUT)); return null; });
            var duplicatedProfile = edt(() -> tracker.finish(duplicateProfile, primary)).operations().get(0);
            check(duplicatedProfile.edge().status() == FinanceSerializationTracker.Reference.AMBIGUOUS && duplicatedProfile.qualifications().serializerAfterReference().isEmpty(),
                    "duplicate reference invalidates unique-profile attribution even if supplied status equals");
            var failedProfile = edt(() -> tracker.begin(primary, null));
            edt(() -> { tracker.referenced(failedProfile, edgeField, club, ledger, "writeObject", FinanceSerializationTracker.SerializerKind.FIELD_SERIALIZER,
                    Optional.of(LedgerSerializerProfile.Status.SUPPORTED_SAMPLED_LAYOUT)); return null; });
            tracker.referenceFailed(failedProfile);
            check(edt(() -> tracker.finish(failedProfile, primary)).operations().get(0).qualifications().serializerAfterReference().isEmpty(),
                    "failed reference attribution removes earlier positive profile, never retains orphan qualification");
            var offEdtEdge = edt(() -> tracker.begin(primary, null));
            tracker.referenced(offEdtEdge, edgeField, club, ledger, "writeObject", FinanceSerializationTracker.SerializerKind.FIELD_SERIALIZER);
            check(edt(() -> tracker.finish(offEdtEdge, primary)).operations().get(0).edge().status() == FinanceSerializationTracker.Reference.UNAVAILABLE, "off-EDT reference unavailable");
            var failedEdge = edt(() -> tracker.begin(primary, null)); tracker.referenceFailed(failedEdge);
            edt(() -> { tracker.referenced(failedEdge, edgeField, club, ledger, "writeObject", FinanceSerializationTracker.SerializerKind.FIELD_SERIALIZER); return null; });
            check(edt(() -> tracker.finish(failedEdge, primary)).operations().get(0).edge().status() == FinanceSerializationTracker.Reference.UNAVAILABLE, "callback/stream attribution failure stays unavailable after later match");
            var badRoute = edt(() -> tracker.begin(primary, null));
            edt(() -> { tracker.referenced(badRoute, edgeField, club, ledger, "custom", FinanceSerializationTracker.SerializerKind.FIELD_SERIALIZER); return null; });
            check(edt(() -> tracker.finish(badRoute, primary)).operations().get(0).edge().status() == FinanceSerializationTracker.Reference.UNAVAILABLE, "unknown reference route refused");
            var staleEdge = edt(() -> tracker.begin(primary, null)); tracker.invalidate();
            edt(() -> { tracker.referenced(staleEdge, edgeField, club, ledger, "writeObject", FinanceSerializationTracker.SerializerKind.FIELD_SERIALIZER); return null; });
            check(!edt(() -> tracker.finish(staleEdge, primary)).validWindow(), "invalidated reference window cannot recover by matching ledger");
            FinanceMutationTracker.Snapshot checkpoint = null; // This fixture qualifies reference events only, not mutation continuity.
            tracker.observe(UUID.randomUUID(), intentForTable(), target, checkpoint);
            var scoped = edt(() -> tracker.begin(primary, checkpoint));
            var scope = edt(() -> tracker.beginReferenceCall(scoped, edgeField, club, ledger));
            edt(() -> {
                tracker.referenceMarker(scoped, new Object(), 3, 8, 1, true); // Other identity cannot create our table definition.
                tracker.referenceMarker(scoped, ledger, 3, 8, 1, true); tracker.referenceContent(scoped, ledger, true); tracker.endReferenceCall(scope); return null;
            });
            var scopedCapture = edt(() -> tracker.finish(scoped, primary)); var table = scopedCapture.referenceTraces().get(0).summary();
            check(table.fieldResolution() == KryoLedgerReferenceTrace.Resolution.NEW_CONTENT_RETURNED && table.newMarkers() == 1 && table.contentReturns() == 1,
                    "scoped table events correlate only exact weak ledger target and opaque original field call");
            check(scopedCapture.referenceTraces().get(0).operationId().equals(scopedCapture.operations().get(0).operationId()), "table summary tied to exact operation UUID not runtime ID");
            var unrelatedScope = edt(() -> tracker.begin(primary, checkpoint));
            check(edt(() -> tracker.beginReferenceCall(unrelatedScope, edgeField, new Object(), ledger)) == null, "another club receiver cannot open our ledger field scope");
            check(edt(() -> tracker.finish(unrelatedScope, primary)).referenceTraces().get(0).summary().fieldResolution() == KryoLedgerReferenceTrace.Resolution.MISSING,
                    "unrelated field callback never invents a table trace");
            var outside = edt(() -> tracker.begin(primary, checkpoint));
            edt(() -> { tracker.referenceMarker(outside, ledger, 3, 9, 1, true); tracker.referenceContent(outside, ledger, true); return null; });
            var outsideScope = edt(() -> tracker.beginReferenceCall(outside, edgeField, club, ledger));
            edt(() -> { tracker.referenceMarker(outside, ledger, 2, 9, 11, true); tracker.endReferenceCall(outsideScope); return null; });
            check(edt(() -> tracker.finish(outside, primary)).referenceTraces().get(0).summary().fieldResolution() == KryoLedgerReferenceTrace.Resolution.PRIOR_CONTENT_REFERENCE,
                    "field scope may link earlier observed body without treating it as newly serialized");
            var offThread = edt(() -> tracker.begin(primary, checkpoint)); tracker.referenceMarker(offThread, ledger, 3, 9, 1, true);
            check(edt(() -> tracker.finish(offThread, primary)).referenceTraces().get(0).summary().fieldResolution() == KryoLedgerReferenceTrace.Resolution.UNAVAILABLE,
                    "off-EDT table callback fails closed without consulting target");
            var resetWindow = edt(() -> tracker.begin(primary, checkpoint));
            edt(() -> { tracker.referenceMarker(resetWindow, ledger, 3, 1, 1, true); tracker.referenceContent(resetWindow, ledger, true); tracker.referenceReset(resetWindow); tracker.referenceResetCompleted(resetWindow); return null; });
            var afterResetScope = edt(() -> tracker.beginReferenceCall(resetWindow, edgeField, club, ledger));
            edt(() -> { tracker.referenceMarker(resetWindow, ledger, 2, 1, 3, true); tracker.endReferenceCall(afterResetScope); return null; });
            check(edt(() -> tracker.finish(resetWindow, primary)).referenceTraces().get(0).summary().fieldResolution() == KryoLedgerReferenceTrace.Resolution.PRIOR_REFERENCE_UNPROVEN,
                    "table IDs cannot borrow body evidence across reset epochs");
            var revoked = edt(() -> tracker.begin(primary, checkpoint)); var revokedScope = edt(() -> tracker.beginReferenceCall(revoked, edgeField, club, ledger));
            tracker.invalidate(); tracker.endReferenceCall(revokedScope);
            check(!edt(() -> tracker.finish(revoked, primary)).validWindow(), "late field return cannot revive a revoked window");
            tracker.close(); java.lang.ref.Reference.reachabilityFence(target);
        }
        System.out.println("PASS: " + checks + " numeric/reference field emission checks; owned fixture " + root + "; no inclusion authority.");
    }
    private static CareerCommandJournal.Intent intentForTable() { return new CareerCommandJournal.Intent("table-scoped", CareerCommandGate.Operation.FINANCE_CREDIT, "c".repeat(64)); }
}
