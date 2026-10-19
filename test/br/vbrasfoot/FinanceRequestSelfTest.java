package br.vbrasfoot;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Own payloads only; no game, live authority or application/save-inclusion inference. */
public final class FinanceRequestSelfTest {
    private static int checks;
    private interface Action { void run() throws Exception; }
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    private static void rejects(Action action) throws Exception {
        try { action.run(); } catch (IOException | IllegalArgumentException | ArithmeticException expected) { checks++; return; }
        throw new AssertionError("Expected invalid request refusal");
    }
    private static FinanceRequest copy(FinanceRequest r, UUID career, UUID binding, CareerSidecarStore.SaveRevision save,
                                       String owner, String key, FinanceRequest.Club club, FinanceDelta.Direction direction, int amount,
                                       FinanceDelta.Snapshot before) {
        return new FinanceRequest(career, binding, save, owner, key, club, direction, amount, before);
    }
    public static void main(String[] args) throws Exception {
        if (args.length == 1) { System.out.print(FinanceRequest.decode(Files.readAllBytes(Path.of(args[0]))).digest()); return; }
        String local = System.getenv("LOCALAPPDATA");
        Path parent = local == null ? Files.createDirectories(Path.of("build")) : Files.createDirectories(Path.of(local, "Temp", "opencode"));
        Path root = Files.createTempDirectory(parent, "finance-request-");
        var fields = new LinkedHashMap<String, Long>();
        for (String field : new TreeSet<>(FinanceDelta.FIELDS)) fields.put(field, 0L);
        var before = new FinanceDelta.Snapshot(1000, fields);
        var save = new CareerSidecarStore.SaveRevision("a".repeat(64), 100);
        var r = new FinanceRequest(UUID.randomUUID(), UUID.randomUUID(), save, "br.owned.finance", "payment_001",
                new FinanceRequest.Club(2, 7, "Clube São João"), FinanceDelta.Direction.CREDIT_RAW_6, 23, before);
        check(FinanceRequest.decode(r.encode()).equals(r), "versioned canonical payload roundtrip");
        check(r.digest().equals(FinanceRequest.decode(r.encode()).digest()), "digest has no session nonce");
        Path payload = Files.write(root.resolve("request.bin"), r.encode());
        var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-cp",
                System.getProperty("java.class.path"), FinanceRequestSelfTest.class.getName(), payload.toString()).redirectErrorStream(true).start();
        boolean exited = process.waitFor(20, java.util.concurrent.TimeUnit.SECONDS);
        if (!exited) { process.destroyForcibly(); throw new AssertionError("Owned child JVM timeout"); }
        String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        check(process.exitValue() == 0 && output.equals(r.digest()), "fresh JVM reconstructs exact persisted digest");
        var variants = List.of(
                copy(r, UUID.randomUUID(), r.binding(), save, r.owner(), r.key(), r.club(), r.direction(), r.amount(), before),
                copy(r, r.career(), UUID.randomUUID(), save, r.owner(), r.key(), r.club(), r.direction(), r.amount(), before),
                copy(r, r.career(), r.binding(), new CareerSidecarStore.SaveRevision("b".repeat(64), 100), r.owner(), r.key(), r.club(), r.direction(), r.amount(), before),
                copy(r, r.career(), r.binding(), new CareerSidecarStore.SaveRevision(save.sha256(), 101), r.owner(), r.key(), r.club(), r.direction(), r.amount(), before),
                copy(r, r.career(), r.binding(), save, "br.other.finance", r.key(), r.club(), r.direction(), r.amount(), before),
                copy(r, r.career(), r.binding(), save, r.owner(), "payment_002", r.club(), r.direction(), r.amount(), before),
                copy(r, r.career(), r.binding(), save, r.owner(), r.key(), new FinanceRequest.Club(3, 7, r.club().name()), r.direction(), r.amount(), before),
                copy(r, r.career(), r.binding(), save, r.owner(), r.key(), new FinanceRequest.Club(2, 8, r.club().name()), r.direction(), r.amount(), before),
                copy(r, r.career(), r.binding(), save, r.owner(), r.key(), new FinanceRequest.Club(2, 7, "Outro clube"), r.direction(), r.amount(), before),
                copy(r, r.career(), r.binding(), save, r.owner(), r.key(), r.club(), FinanceDelta.Direction.DEBIT_RAW_7, r.amount(), before),
                copy(r, r.career(), r.binding(), save, r.owner(), r.key(), r.club(), r.direction(), 24, before),
                copy(r, r.career(), r.binding(), save, r.owner(), r.key(), r.club(), r.direction(), r.amount(), new FinanceDelta.Snapshot(1001, fields)));
        var digests = new HashSet<String>(); digests.add(r.digest());
        for (var variant : variants) check(digests.add(variant.digest()), "every scoped input changes identity");
        for (String field : FinanceDelta.FIELDS) {
            var changed = new HashMap<>(fields); changed.put(field, 1L);
            check(!r.digest().equals(copy(r, r.career(), r.binding(), save, r.owner(), r.key(), r.club(), r.direction(), r.amount(),
                    new FinanceDelta.Snapshot(before.balance(), changed)).digest()), "full ledger field contributes " + field);
        }
        var reversed = new LinkedHashMap<String, Long>(); var names = new ArrayList<>(fields.keySet()); Collections.reverse(names);
        for (String name : names) reversed.put(name, fields.get(name));
        check(r.digest().equals(copy(r, r.career(), r.binding(), save, r.owner(), r.key(), r.club(), r.direction(), r.amount(),
                new FinanceDelta.Snapshot(before.balance(), reversed)).digest()), "map iteration order does not affect encoding");
        var context = new CareerCommandGate.Context(UUID.randomUUID(), r.career(), r.binding(), 1, save);
        check(r.matches(context, r.owner()), "exact career/binding/revision/owner match");
        check(r.matches(new CareerCommandGate.Context(UUID.randomUUID(), r.career(), r.binding(), 22, save), r.owner()), "session token/generation are not durable payload identity");
        for (int i = 0; i < 5; i++) check(!variants.get(i).matches(context, r.owner()), "cross context refused " + i);
        rejects(() -> FinanceRequest.decode(null)); rejects(() -> FinanceRequest.decode(new byte[4097]));
        rejects(() -> FinanceRequest.decode(Arrays.copyOf(r.encode(), r.encode().length - 1)));
        rejects(() -> FinanceRequest.decode(Arrays.copyOf(r.encode(), r.encode().length + 1)));
        byte[] wrongVersion = r.encode(); wrongVersion[2] ^= 1; rejects(() -> FinanceRequest.decode(wrongVersion));
        byte[] wrongProfile = r.encode(); wrongProfile[2 + "vbrasfoot.finance-request/1".length() + 2] ^= 1;
        rejects(() -> FinanceRequest.decode(wrongProfile));
        rejects(() -> copy(r, r.career(), r.binding(), save, "../other", r.key(), r.club(), r.direction(), 23, before));
        rejects(() -> copy(r, r.career(), r.binding(), save, r.owner(), "../key", r.club(), r.direction(), 23, before));
        rejects(() -> copy(r, r.career(), r.binding(), save, r.owner(), r.key(), r.club(), r.direction(), 0, before));
        rejects(() -> copy(r, r.career(), r.binding(), save, r.owner(), r.key(), r.club(), FinanceDelta.Direction.DEBIT_RAW_7, 1001, before));
        rejects(() -> new FinanceRequest.Club(-1, 0, "club")); rejects(() -> new FinanceRequest.Club(0, 0, " "));
        var store = new CareerSidecarStore(root.resolve("journals")); var key = new CareerSidecarStore.Key(r.career(), r.binding(), r.owner());
        var journal = new CareerCommandJournal(store, key); var prepared = journal.prepare(save, CareerSidecarStore.EMPTY, r.intent());
        var completed = journal.complete(prepared.lease().orElseThrow(), CareerCommandJournal.State.APPLIED, prepared.snapshot().token());
        var reopened = new CareerCommandJournal(new CareerSidecarStore(root.resolve("journals")), key);
        check(reopened.prepare(save, completed.token(), FinanceRequest.decode(Files.readAllBytes(payload)).intent()).lease().isEmpty(),
                "reconstructed request never receives another lease for historical applied effect");
        rejects(() -> reopened.prepare(save, completed.token(), variants.get(10).intent()));
        check(reopened.read(save).equals(completed), "changed payload with same key does not overwrite historical receipt");
        System.out.println("PASS: " + checks + " persistent finance request checks; own payloads at " + root + "; not effect/save reconciliation.");
    }
}
