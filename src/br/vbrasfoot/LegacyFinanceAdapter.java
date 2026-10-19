package br.vbrasfoot;

import java.io.*;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.SwingUtilities;

/** Internal experimental original-method adapter. Not connected to Main/ModContext or advertised as a capability. */
final class LegacyFinanceAdapter {
    private final LegacyRuntime runtime;
    private final Class<?> state, clubClass, ledgerClass;
    LegacyFinanceAdapter(LegacyRuntime runtime) throws Exception {
        this.runtime = Objects.requireNonNull(runtime);
        fingerprint("best/ah.class", "9e12714ef123afe00636edd25a64334451ba272a9d702b5ce63c656cd4411e6f");
        fingerprint("best/C.class", "e208455170e565d83ab0733a79ba2c10e4addfb9a07b583d6b06d46a460eded4");
        state = Class.forName("c.a", false, runtime); clubClass = Class.forName("best.ah", false, runtime); ledgerClass = Class.forName("best.C", false, runtime);
        if (clubClass.getClassLoader() != runtime || ledgerClass.getClassLoader() != runtime) throw new IllegalStateException("Original defining loader required");
    }
    private void fingerprint(String name, String expected) throws Exception {
        var resource = runtime.findResource(name); if (resource == null) throw new IOException("Missing original financial class");
        byte[] bytes; try (var input = resource.openStream()) { bytes = input.readNBytes(1024 * 1024 + 1); }
        if (bytes.length > 1024 * 1024 || !expected.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))))
            throw new IOException("Financial class fingerprint changed; refused");
    }
    CareerCommandDispatcher.Roots roots() throws Exception {
        requireEdt(); return new CareerCommandDispatcher.Roots(state.getField("SR").get(null), state.getField("afQ").get(null));
    }
    FinanceDelta.Snapshot snapshot(Object club) throws Exception {
        requireEdt(); if (club == null || club.getClass() != clubClass) throw new IllegalArgumentException("Original club required");
        Object ledger = CareerBridge.call(club, "kL");
        if (ledger == null || ledger.getClass() != ledgerClass) throw new IllegalStateException("Original ledger required");
        var values = new TreeMap<String, Long>();
        for (String name : FinanceDelta.FIELDS) { var field = ledgerClass.getDeclaredField(name); field.setAccessible(true); values.put(name, ((Number) field.get(ledger)).longValue()); }
        return new FinanceDelta.Snapshot(((Number) CareerBridge.call(club, "kb")).longValue(), values);
    }
    Plan prepare(Object club, FinanceDelta.Direction direction, int amount) throws Exception {
        requireEdt(); var roots = roots(); requireHumanClub(club, roots);
        FinanceDelta.Snapshot before = snapshot(club), after = FinanceDelta.predict(before, direction, amount);
        Object ledger = CareerBridge.call(club, "kL");
        return new Plan(club, ledger, roots, before, after, direction, amount, null);
    }
    /** Durable intent can be reconstructed, but live plans remain weak and one-shot. No implicit owner grant. */
    FinanceRequest request(CareerCommandGate.Context context, String owner, String key, Object club,
                           FinanceDelta.Direction direction, int amount) throws Exception {
        requireEdt(); var roots = roots(); requireHumanClub(club, roots);
        return new FinanceRequest(context.career(), context.binding(), context.save(), owner, key,
                locator(club, roots), direction, amount, snapshot(club));
    }
    Plan prepare(FinanceRequest request) throws Exception {
        requireEdt(); Objects.requireNonNull(request); var roots = roots();
        if (roots.primary() == null || roots.secondary() == null) throw new IllegalStateException("No live roots");
        var clubs = (List<?>) CareerBridge.call(roots.primary(), "P");
        if (request.club().index() >= clubs.size()) throw new IllegalStateException("Club locator no longer present");
        Object club = clubs.get(request.club().index()); requireHumanClub(club, roots);
        if (!request.club().equals(locator(club, roots)) || !request.before().equals(snapshot(club)))
            throw new IllegalStateException("Club or full financial precondition changed");
        return new Plan(club, CareerBridge.call(club, "kL"), roots, request.before(),
                FinanceDelta.predict(request.before(), request.direction(), request.amount()), request.direction(), request.amount(), request);
    }
    private FinanceRequest.Club locator(Object club, CareerCommandDispatcher.Roots roots) throws Exception {
        var clubs = (List<?>) CareerBridge.call(roots.primary(), "P");
        int id = ((Number) CareerBridge.call(club, "gD")).intValue(), index = -1, matches = 0;
        String name = (String) CareerBridge.call(club, "getNome");
        for (int i = 0; i < clubs.size(); i++) {
            Object candidate = clubs.get(i);
            if (candidate == null || candidate.getClass() != clubClass) throw new IllegalStateException("Invalid club list");
            if (((Number) CareerBridge.call(candidate, "gD")).intValue() == id
                    && Objects.equals(name, CareerBridge.call(candidate, "getNome"))) matches++;
            if (candidate == club) { if (index >= 0) throw new IllegalStateException("Repeated club root"); index = i; }
        }
        if (index < 0 || matches != 1) throw new IllegalStateException("Ambiguous club ID/name; request refused");
        return new FinanceRequest.Club(index, id, name);
    }
    private void requireHumanClub(Object club, CareerCommandDispatcher.Roots roots) throws Exception {
        if (roots.primary() == null || roots.secondary() == null || club == null || club.getClass() != clubClass)
            throw new IllegalStateException("No original career/club context");
        if (!((List<?>) CareerBridge.call(roots.primary(), "P")).stream().anyMatch(value -> value == club)
                || !((List<?>) CareerBridge.call(roots.primary(), "aN")).stream().anyMatch(value -> value == club)
                || !Boolean.TRUE.equals(CareerBridge.call(club, "jZ"))) throw new IllegalStateException("Exact current human club required");
    }
    private static void requireEdt() { if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Original financial access requires EDT"); }
    final class Plan implements CareerCommandDispatcher.Handler {
        private final WeakReference<Object> club, ledger, primary, secondary;
        private final FinanceDelta.Snapshot before, after;
        private final FinanceDelta.Direction direction;
        private final int amount;
        private final AtomicBoolean used = new AtomicBoolean();
        private final String digest;
        private final FinanceRequest request;
        Plan(Object club, Object ledger, CareerCommandDispatcher.Roots roots, FinanceDelta.Snapshot before, FinanceDelta.Snapshot after,
              FinanceDelta.Direction direction, int amount, FinanceRequest request) throws Exception {
            this.club = new WeakReference<>(club); this.ledger = new WeakReference<>(ledger);
            primary = new WeakReference<>(roots.primary()); secondary = new WeakReference<>(roots.secondary());
            this.before = before; this.after = after; this.direction = direction; this.amount = amount;
            this.request = request;
            if (request != null) digest = request.digest();
            else {
                // Compatibility fixture plan only: not a persistent request or cross-session identity.
                String canonical = UUID.randomUUID() + ":" + direction + ":" + amount + ":" + before.balance() + ":" + new TreeMap<>(before.ledger());
                digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
            }
        }
        CareerCommandJournal.Intent intent(String key) {
            if (request != null && !request.key().equals(key)) throw new IllegalArgumentException("Persistent request key cannot be replaced");
            return new CareerCommandJournal.Intent(key, direction == FinanceDelta.Direction.CREDIT_RAW_6 ? CareerCommandGate.Operation.FINANCE_CREDIT : CareerCommandGate.Operation.FINANCE_DEBIT, digest);
        }
        @Override public boolean accepts(CareerCommandJournal.Intent intent) {
            return intent != null && (request == null || request.key().equals(intent.key())) && intent.payloadDigest().equals(digest)
                    && intent.operation() == (direction == FinanceDelta.Direction.CREDIT_RAW_6 ? CareerCommandGate.Operation.FINANCE_CREDIT : CareerCommandGate.Operation.FINANCE_DEBIT);
        }
        @Override public boolean acceptsContext(CareerCommandGate.Context context, String owner) {
            return request == null || request.matches(context, owner);
        }
        @Override public Optional<FinanceRequest> persistentRequest() { return Optional.ofNullable(request); }
        FinanceDelta.Snapshot expected() { return after; }
        private boolean current(Object target, Object originalLedger, CareerCommandDispatcher.Roots roots) throws Exception {
            if (target == null || originalLedger == null || primary.get() == null || secondary.get() == null
                    || roots.primary() != primary.get() || roots.secondary() != secondary.get() || CareerBridge.call(target, "kL") != originalLedger) return false;
            requireHumanClub(target, roots);
            return request == null || request.club().equals(locator(target, roots));
        }
        @Override public CareerCommandGate.Status apply() throws Exception {
            requireEdt(); if (!used.compareAndSet(false, true)) return CareerCommandGate.Status.REJECTED;
            Object target = club.get(), originalLedger = ledger.get();
            if (!current(target, originalLedger, roots()) || !snapshot(target).equals(before)) return CareerCommandGate.Status.REJECTED;
            try { clubClass.getMethod(direction == FinanceDelta.Direction.CREDIT_RAW_6 ? "v" : "w", int.class, int.class)
                    .invoke(target, amount, direction == FinanceDelta.Direction.CREDIT_RAW_6 ? 6 : 7); }
            catch (InvocationTargetException failure) {
                if (failure.getCause() instanceof Exception exception) throw exception;
                if (failure.getCause() instanceof Error error) throw error;
                throw failure;
            }
            return current(target, originalLedger, roots()) && snapshot(target).equals(after)
                    ? CareerCommandGate.Status.APPLIED : CareerCommandGate.Status.OUTCOME_UNKNOWN;
        }
    }
}
