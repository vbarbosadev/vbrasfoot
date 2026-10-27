package br.vbrasfoot;

import br.vbrasfoot.api.GameAccess.*;
import java.io.*;
import java.lang.ref.WeakReference;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.SwingUtilities;

/** Internal qualified-profile adapter, used by the loader only; no public gameplay capability. */
final class LegacyLoanAdapter {
    private final LegacyRuntime runtime;
    private final Class<?> state, clubType, playerType;
    private final LegacyFinanceAdapter finance;
    private final java.util.function.BooleanSupplier selectionGuard;
    LegacyLoanAdapter(LegacyRuntime runtime) throws Exception {
        this(runtime, () -> true);
    }
    LegacyLoanAdapter(LegacyRuntime runtime, java.util.function.BooleanSupplier selectionGuard) throws Exception {
        this.runtime = Objects.requireNonNull(runtime);
        this.selectionGuard = Objects.requireNonNull(selectionGuard);
        fingerprint("best/l.class", "2f8a20d732fe49fc598eb480810640ae513968084878342cbc9bfb77e0986ef4");
        fingerprint("best/F.class", "844f6cd54eb997954f24ab0f4ca1089e703a1a2872d0a36fba421e53f9c6eaa8");
        fingerprint("best/ah.class", "9e12714ef123afe00636edd25a64334451ba272a9d702b5ce63c656cd4411e6f");
        fingerprint("components/t.class", "ef9a7fc5c5158032ce20bc614cf07e2fea6987d855fba2c8bd7ae8538d9d9594");
        fingerprint("best/f.class", "dbd5bd8633e3a1c2f211e8bd6c322db8f7401c5ccf7cc622cc4050f0624df71b");
        fingerprint("a/eg.class", "ca708c478ddd4ef77336e55b561862ea98cd2b7b89b0ed59c20b5c206c9b04fd");
        fingerprint("b/Q.class", "e8a7c5425aee3eca55d455529324997048bae17de66b1914d48d6319d8f6fdb5");
        state = Class.forName("c.a", false, runtime); clubType = runtime.loadClass("best.ah"); playerType = runtime.loadClass("best.F");
        fingerprint("best/k.class", "c00cca8768624d2a256b5b36609bd069359d9a429e05bcece4c499dcb829d7ba");
        finance = new LegacyFinanceAdapter(runtime);
        if (clubType.getClassLoader() != runtime || playerType.getClassLoader() != runtime) throw new IllegalStateException("Original defining loader required");
    }
    private void fingerprint(String resource, String expected) throws Exception {
        var url = runtime.findResource(resource); if (url == null) throw new IOException("Missing original loan class");
        byte[] bytes; try (var in = url.openStream()) { bytes = in.readNBytes(1024 * 1024 + 1); }
        if (bytes.length > 1024 * 1024 || !expected.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))))
            throw new IOException("Original loan class fingerprint mismatch");
    }
    CareerCommandDispatcher.Roots roots() throws Exception {
        edt(); return new CareerCommandDispatcher.Roots(state.getField("SR").get(null), state.getField("afQ").get(null));
    }
    /** Consistency of one still-active historical loan, NOT causal save/fee inclusion proof. */
    boolean historicalLoanPresent(LoanRequest request) throws Exception {
        edt(); var roots = roots();
        Object origin = resolve(roots, request.origin()), destination = resolve(roots, request.destination()), player = null;
        int matches = 0;
        for (Object candidate : list(destination, "kc")) {
            if (number(candidate, "gD") == request.player().id() && request.player().name().equals(call(candidate, "getNome"))) { player = candidate; matches++; }
        }
        if (matches != 1 || player == null || call(player, "fg") != destination) return false;
        int occurrences = 0, inspected = 0; long started = System.nanoTime();
        for (Object club : list(roots.primary(), "P")) for (String getter : List.of("kc", "ky")) {
            var roster = list(club, getter); if (roster.size() > 500) return false;
            for (Object candidate : roster) {
                if (++inspected > 1000000 || System.nanoTime() - started > 500000000L) return false;
                if (candidate == player) { if (club != destination || !getter.equals("kc")) return false; occurrences++; }
            }
        }
        if (occurrences != 1) return false;
        var trackers = list(roots.primary(), "bt"); if (trackers.size() > 10000) return false;
        int loans = 0;
        for (Object tracker : trackers) if (call(tracker, "x") == player) {
            if (call(tracker, "tP") != origin || ((Number) call(tracker, "tO")).longValue() != Math.addExact(request.clock(), 366L * 86400000L)) return false;
            loans++;
        }
        return loans == 1;
    }
    /** Narrow user-selected baseline: the zero-fee historical player is uniquely at origin, with no tracker. */
    boolean historicalLoanAtOrigin(LoanRequest request) throws Exception {
        edt(); if (request.fee() != 0) return false;
        var roots = roots(); Object origin = resolve(roots, request.origin()), player = null;
        int matches = 0, inspected = 0;
        for (Object club : list(roots.primary(), "P")) for (String roster : List.of("kc", "ky")) {
            var players = list(club, roster); if (players.size() > 500) return false;
            for (Object candidate : players) {
                if (++inspected > 1000000) return false;
                if (number(candidate, "gD") == request.player().id()) {
                    if (!request.player().name().equals(call(candidate, "getNome")) || club != origin || !roster.equals("kc")
                            || call(candidate, "fg") != origin) return false;
                    player = candidate; matches++;
                }
            }
        }
        if (matches != 1) return false;
        var trackers = list(roots.primary(), "bt"); if (trackers.size() > 10000) return false;
        for (Object tracker : trackers) if (call(tracker, "x") == player) return false;
        return true;
    }
    List<LoanHistoryBatch.Expected> invalidHistoricalLoans(List<LoanRequest> active, List<LoanRequest> retired) throws Exception {
        edt(); var roots = roots(); var expected = new ArrayList<LoanHistoryBatch.Expected>();
        var transfers = active.isEmpty() ? List.<LoanReturnEvidence.Transfer>of()
                : returnHistory(roots, active.stream().map(request -> request.player().id()).collect(java.util.stream.Collectors.toSet()));
        var zone = java.time.ZoneId.systemDefault();
        var today = java.time.Instant.ofEpochMilli(clock(roots)).atZone(zone).toLocalDate();
        for (var request : active) {
            Object origin = resolve(roots, request.origin()), destination = resolve(roots, request.destination());
            boolean returned = LoanReturnEvidence.returned(request.player().id(), number(origin, "lk"), number(destination, "lk"),
                    java.time.Instant.ofEpochMilli(request.clock()).atZone(zone).toLocalDate(), today, transfers);
            expected.add(new LoanHistoryBatch.Expected(request.player().id(), request.player().name(), destination, origin,
                    Math.addExact(request.clock(), 366L * 86400000L), false, returned));
        }
        for (var request : retired) {
            if (request.fee() != 0) throw new IllegalStateException("Retired historical fee is not qualified");
            var origin = resolve(roots, request.origin());
            expected.add(new LoanHistoryBatch.Expected(request.player().id(), request.player().name(), origin, origin, 0, true));
        }
        return LoanHistoryBatch.invalid(expected, new LoanHistoryBatch.Access() {
            public List<?> clubs() throws Exception { return list(roots.primary(), "P"); }
            public List<?> roster(Object club, boolean juniors) throws Exception { return list(club, juniors ? "ky" : "kc"); }
            public int id(Object player) throws Exception { return number(player, "gD"); }
            public String name(Object player) throws Exception { return String.valueOf(call(player, "getNome")); }
            public Object club(Object player) throws Exception { return call(player, "fg"); }
            public List<?> trackers() throws Exception { return list(roots.primary(), "bt"); }
            public Object trackedPlayer(Object tracker) throws Exception { return call(tracker, "x"); }
            public Object origin(Object tracker) throws Exception { return call(tracker, "tP"); }
            public long due(Object tracker) throws Exception { return ((Number)call(tracker, "tO")).longValue(); }
        });
    }
    private List<LoanReturnEvidence.Transfer> returnHistory(CareerCommandDispatcher.Roots roots, Set<Integer> targetIds) throws Exception {
        var history = list(roots.primary(), "bo");
        if (history.size() > 64000) throw new IllegalStateException("Native transfer history quota");
        var copied = new ArrayList<LoanReturnEvidence.Transfer>();
        for (Object row : history) {
            if (row == null || !row.getClass().getName().equals("best.ap") || row.getClass().getClassLoader() != runtime)
                throw new IllegalStateException("Original transfer history required");
            Object linked = call(row, "x");
            var kindField = row.getClass().getDeclaredField("ej"); kindField.setAccessible(true);
            int kind = kindField.getInt(row);
            if (linked == null ? kind != 1 : linked.getClass() != playerType) continue; // Coach ids are a separate namespace.
            int id = number(row, "gD");
            // New, unsaved rows hold the live player reference; decoded rows keep the scalar id.
            if (linked != null) {
                int linkedId = number(linked, "gD");
                if (id >= 0 && id != linkedId) throw new IllegalStateException("Transfer history player mismatch");
                id = linkedId;
            }
            if (!targetIds.contains(id)) continue;
            java.time.LocalDate date;
            try { date = java.time.LocalDate.parse(String.valueOf(call(row, "f")), java.time.format.DateTimeFormatter.ofPattern("d/M/uuuu").withResolverStyle(java.time.format.ResolverStyle.STRICT)); }
            catch (java.time.DateTimeException invalid) { throw new IllegalStateException("Invalid transfer history date", invalid); }
            copied.add(new LoanReturnEvidence.Transfer(id, number(row, "ma"), number(row, "lZ"), number(row, "lY"), date));
        }
        return List.copyOf(copied);
    }
    LoanRequest request(CareerCommandGate.Context context, String owner, String key, CareerBridge bridge,
                        PlayerRef player, ClubRef origin, ClubRef destination) throws Exception {
        return request(context.career(), context.binding(), context.save(), owner, key, bridge, player, origin, destination);
    }
    /** Capture metadata against an explicitly reviewed association; does not construct gate authority. */
    LoanRequest request(UUID career, UUID binding, CareerSidecarStore.SaveRevision save, String owner, String key, CareerBridge bridge,
                        PlayerRef player, ClubRef origin, ClubRef destination) throws Exception {
        edt(); var selected = bridge.loanSelection(player, origin, destination); var roots = roots();
        requireSelection(roots, selected.origin(), selected.destination(), selected.player());
        List<?> sourceRoster = list(selected.origin(), "kc");
        int index = identityIndex(sourceRoster, selected.player());
        return new LoanRequest(career, binding, save, owner, key,
                locator(roots, selected.origin()), locator(roots, selected.destination()), index,
                snapshot(sourceRoster), snapshot(list(selected.destination(), "kc")), clock(roots), Optional.of(
                        termsForPlayer(roots, selected.origin(), selected.destination(), selected.player())));
    }
    Plan prepare(LoanRequest request) throws Exception {
        edt(); Objects.requireNonNull(request); var roots = roots();
        Object origin = resolve(roots, request.origin()), destination = resolve(roots, request.destination());
        List<?> roster = list(origin, "kc");
        if (request.playerIndex() >= roster.size()) throw new IllegalStateException("Player locator expired");
        Object player = roster.get(request.playerIndex());
        requireSelection(roots, origin, destination, player);
        if (!matches(request, roots, origin, destination) || request.assessment().decision() != LoanClubAssessment.Decision.APPROVE)
            throw new IllegalStateException("Stale or unapproved loan request");
        if (nativeGuards(roots, player, destination, request.nativeListed() || request.terms().isEmpty()) != 1)
            throw new IllegalStateException("Limite de tres/elegibilidade nativa de destino mudou");
        if (request.terms().isPresent() && !request.nativeListed()) {
            predicted(request, true); predicted(request, false); // Validate balances/ledger arithmetic before any effect.
        }
        return new Plan(request, roots, origin, destination, player);
    }
    private LoanTerms termsForPlayer(CareerCommandDispatcher.Roots roots, Object origin, Object destination, Object player) throws Exception {
        if (!Boolean.TRUE.equals(call(player, "fz"))) return terms(roots, origin, destination);
        return nativeTerms(roots, origin, destination);
    }
    private LoanTerms nativeTerms(CareerCommandDispatcher.Roots roots, Object origin, Object destination) throws Exception {
        // No history/median/release policy or fee arithmetic for already-listed players.
        return new LoanTerms(number(roots.primary(), "H"), Collections.nCopies(list(origin, "kc").size(), -1),
                finance.snapshot(origin), finance.snapshot(destination), Boolean.TRUE.equals(call(origin, "jZ")), LoanTerms.NATIVE_POLICY);
    }
    private LoanTerms terms(CareerCommandDispatcher.Roots roots, Object origin, Object destination) throws Exception {
        Object sourceLedger = call(origin, "kL"), targetLedger = call(destination, "kL");
        if (sourceLedger == targetLedger
                || finance.ledgerMembership(origin, sourceLedger, roots) != FinanceLedgerMembership.Status.UNIQUE_IN_CLUB_LIST
                || finance.ledgerMembership(destination, targetLedger, roots) != FinanceLedgerMembership.Status.UNIQUE_IN_CLUB_LIST)
            throw new IllegalStateException("Ledgers de origem/destino precisam ser distintos e unicos");
        int season = number(roots.primary(), "H"), originId = number(origin, "lk");
        var perPlayer = new ArrayList<List<LoanClubHistory.Observation>>();
        for (Object player : list(origin, "kc")) {
            List<?> history = list(player, "gr"); if (history.size() > 500) throw new IllegalStateException("Historico de participacao excede limite");
            var observations = new ArrayList<LoanClubHistory.Observation>();
            for (Object row : history) {
                if (row == null || !row.getClass().getName().equals("best.k") || row.getClass().getClassLoader() != runtime)
                    throw new IllegalStateException("Historico original de participacao obrigatorio");
                observations.add(new LoanClubHistory.Observation(number(row, "ct"), number(row, "H"), number(row, "cD")));
            }
            perPlayer.add(List.copyOf(observations));
        }
        var copied = LoanClubHistory.capture(originId, season, perPlayer);
        return new LoanTerms(season, copied.currentGames(), finance.snapshot(origin), finance.snapshot(destination), Boolean.TRUE.equals(call(origin, "jZ")),
                LoanClubHistory.POLICY, Optional.of(copied));
    }
    private FinanceDelta.Snapshot predicted(LoanRequest request, boolean origin) {
        return request.terms().orElseThrow().predicted(origin, request.fee());
    }
    /** Read-only counterpart of the guards in the fingerprinted native routine; never invokes its mutator. */
    LoanDiagnostics.Eligibility eligibility(CareerBridge bridge, PlayerRef player, ClubRef origin, ClubRef destination) throws Exception {
        edt(); var selected = bridge.loanSelection(player, origin, destination); var roots = roots();
        requireSelection(roots, selected.origin(), selected.destination(), selected.player());
        Object candidate = selected.player(), target = selected.destination();
        boolean listed = Boolean.TRUE.equals(call(candidate, "fz"));
        int guards = nativeGuards(roots, candidate, target, listed);
        if (guards != 1) return new LoanDiagnostics.Eligibility(guards);
        if (listed) return new LoanDiagnostics.Eligibility(1, "Jogador listado: rotina original, sem taxa ou avaliacao adicional do mod; limite de tres mantido.", true, "");
        var source = snapshot(list(selected.origin(), "kc")); var targetRoster = snapshot(list(target, "kc"));
        var inputs = terms(roots, selected.origin(), target); int index = identityIndex(list(selected.origin(), "kc"), candidate);
        var release = inputs.release(index, source.stream().map(Player::strength).toList(), source.get(index).value());
        var destinationRule = LoanClubAssessment.evaluate(source.get(index).strength(), targetRoster.stream().map(Player::strength).toList());
        boolean enough = inputs.destinationFinance().balance() >= release.fee();
        String text = release.reason() + "\nTaxa: " + release.fee() + " / valor do jogador: " + source.get(index).value()
                + "\nParticipacao: " + inputs.games().get(index) + " (-1=desconhecida), temporada " + inputs.season()
                + "\nDestino: " + destinationRule.reason() + (enough ? "" : " / saldo insuficiente")
                + "\nIndicador nativo de disponibilidade nao e usado nesta regra; limites e elegibilidade de destino continuam ativos.";
        String reason = !release.approved() ? release.decision() == LoanReleasePolicy.Decision.INSUFFICIENT_DATA
                ? "historico insuficiente para avaliar." : "o clube nao liberou o jogador pelo historico de utilizacao."
                : destinationRule.decision() != LoanClubAssessment.Decision.APPROVE ? "o destino nao aprovou o jogador pelo nivel do elenco."
                : !enough ? "saldo insuficiente para a taxa de emprestimo." : "";
        return new LoanDiagnostics.Eligibility(nativeGuards(roots, candidate, target, false), text,
                release.approved() && destinationRule.decision() == LoanClubAssessment.Decision.APPROVE && enough, reason);
    }
    private int nativeGuards(CareerCommandDispatcher.Roots roots, Object candidate, Object target, boolean requireAvailability) throws Exception {
        int code;
        if (requireAvailability && !Boolean.TRUE.equals(call(candidate, "fz"))) code = 5;
        else if (call(candidate, "fg") == target) code = 2;
        else if (list(target, "kc").size() >= 35) code = 6;
        else {
            int loans = 0;
            for (Object tracker : list(roots.primary(), "bt")) {
                Object current = call(call(tracker, "x"), "fg");
                if (current == target) loans++;
            }
            if (LoanDestinationLimit.reached(loans)) code = 3;
            else code = Boolean.TRUE.equals(runtime.loadClass("best.l").getMethod("d", playerType, clubType).invoke(null, candidate, target)) ? 1 : 4;
        }
        return code;
    }
    private boolean matches(LoanRequest request, CareerCommandDispatcher.Roots roots, Object origin, Object destination) throws Exception {
        return request.origin().equals(locator(roots, origin)) && request.destination().equals(locator(roots, destination))
                && request.originRoster().equals(snapshot(list(origin, "kc")))
                && request.destinationRoster().equals(snapshot(list(destination, "kc"))) && request.clock() == clock(roots)
                && matchesTerms(request, roots, origin, destination);
    }
    private boolean matchesTerms(LoanRequest request, CareerCommandDispatcher.Roots roots, Object origin, Object destination) throws Exception {
        if (request.terms().isEmpty()) return true;
        if (request.nativeListed()) return Boolean.TRUE.equals(call(list(origin, "kc").get(request.playerIndex()), "fz"))
                && request.terms().orElseThrow().equals(nativeTerms(roots, origin, destination));
        return sameTerms(request.terms().orElseThrow(), terms(roots, origin, destination));
    }
    private static boolean sameTerms(LoanTerms recorded, LoanTerms current) {
        return recorded.equals(new LoanTerms(current.season(), current.games(), current.originFinance(), current.destinationFinance(), current.originHuman(), recorded.policy(),
                recorded.history().isPresent() ? current.history() : Optional.empty()));
    }
    private Object resolve(CareerCommandDispatcher.Roots roots, FinanceRequest.Club locator) throws Exception {
        if (roots.primary() == null || roots.secondary() == null) throw new IllegalStateException("No career roots");
        List<?> clubs = list(roots.primary(), "P");
        if (locator.index() >= clubs.size()) throw new IllegalStateException("Club locator expired");
        Object club = clubs.get(locator.index());
        if (!locator.equals(locator(roots, club))) throw new IllegalStateException("Club locator changed");
        return club;
    }
    private FinanceRequest.Club locator(CareerCommandDispatcher.Roots roots, Object club) throws Exception {
        if (club == null || club.getClass() != clubType) throw new IllegalStateException("Original club required");
        List<?> clubs = list(roots.primary(), "P");
        if (clubs.size() > 16384) throw new IllegalStateException("Club bound exceeded");
        int id = number(club, "gD"), index = -1, matches = 0;
        String name = (String) call(club, "getNome");
        for (int i = 0; i < clubs.size(); i++) {
            Object candidate = clubs.get(i);
            if (candidate == null || candidate.getClass() != clubType) throw new IllegalStateException("Invalid club graph");
            if (candidate == club) { if (index >= 0) throw new IllegalStateException("Repeated club object"); index = i; }
            if (id == number(candidate, "gD") && name.equals(call(candidate, "getNome"))) matches++;
        }
        if (index < 0 || matches != 1) throw new IllegalStateException("Ambiguous revision club locator");
        return new FinanceRequest.Club(index, id, name);
    }
    private void requireSelection(CareerCommandDispatcher.Roots roots, Object origin, Object destination, Object player) throws Exception {
        if (roots.primary() == null || roots.secondary() == null || origin == destination || player == null || player.getClass() != playerType)
            throw new IllegalStateException("Original distinct clubs/player/roots required");
        locator(roots, origin); locator(roots, destination);
        if (!Boolean.TRUE.equals(call(destination, "jZ")) || identityIndex(list(roots.primary(), "aN"), destination) < 0)
            throw new IllegalStateException("Exact human destination required");
        if (call(player, "fg") != origin) throw new IllegalStateException("Player club anchor changed");
        int playerId = number(player, "gD"), locatorMatches = 0;
        String playerName = (String) call(player, "getNome");
        for (Object candidate : list(origin, "kc")) if (number(candidate, "gD") == playerId && playerName.equals(call(candidate, "getNome"))) locatorMatches++;
        if (locatorMatches != 1) throw new IllegalStateException("Ambiguous player locator in origin roster");
        if (list(roots.primary(), "bt").size() > 10000) throw new IllegalStateException("Tracker bound exceeded");
        int occurrences = 0, inspected = 0; long start = System.nanoTime();
        for (Object club : list(roots.primary(), "P")) for (String method : List.of("kc", "ky")) {
            List<?> players = list(club, method);
            if (players.size() > 500) throw new IllegalStateException("Roster bound exceeded");
            for (Object value : players) {
                if (++inspected > 1000000 || System.nanoTime() - start > 500000000L) throw new IllegalStateException("Membership inspection budget exceeded");
                if (value == player) {
                    if (club != origin || !method.equals("kc")) throw new IllegalStateException("Player aliased outside origin professional roster");
                    occurrences++;
                }
            }
        }
        if (occurrences != 1) throw new IllegalStateException("Unique observed roster membership required");
        for (Object tracker : list(roots.primary(), "bt")) if (call(tracker, "x") == player)
            throw new IllegalStateException("Player already has a loan tracker");
    }
    final class Plan implements CareerCommandDispatcher.Handler {
        private final LoanRequest request;
        private final WeakReference<Object> primary, secondary, origin, destination, player;
        private final AtomicBoolean used = new AtomicBoolean();
        private int nativeCode = -1;
        private volatile String diagnostic = "Rotina nativa ainda nao chamada; consulte o resultado do dispatcher.";
        private LoanRosterRefresh.Status refreshStatus = LoanRosterRefresh.Status.NOT_VISIBLE;
        private java.util.function.BooleanSupplier policyGuard = () -> true;
        Plan policyGuard(java.util.function.BooleanSupplier guard) { policyGuard = Objects.requireNonNull(guard); return this; }
        Plan(LoanRequest request, CareerCommandDispatcher.Roots roots, Object origin, Object destination, Object player) {
            this.request = request; primary = new WeakReference<>(roots.primary()); secondary = new WeakReference<>(roots.secondary());
            this.origin = new WeakReference<>(origin); this.destination = new WeakReference<>(destination); this.player = new WeakReference<>(player);
        }
        @Override public boolean accepts(CareerCommandJournal.Intent intent) { return request.intent().equals(intent); }
        @Override public boolean acceptsContext(CareerCommandGate.Context context, String owner) { return request.matches(context, owner); }
        @Override public Optional<LoanRequest> persistentLoanRequest() { return Optional.of(request); }
        int nativeCode() { edt(); return nativeCode; }
        String diagnostic() { return diagnostic; }
        LoanRosterRefresh.Status refreshStatus() { edt(); return refreshStatus; }
        @Override public CareerCommandGate.Status apply() throws Exception {
            edt(); if (!used.compareAndSet(false, true)) { diagnostic = "Plano ja consumido; rotina nativa nao chamada novamente."; return CareerCommandGate.Status.REJECTED; }
            if (!selectionGuard.getAsBoolean() || !policyGuard.getAsBoolean()) { diagnostic = "Selecao ou janela de emprestimos mudou; nenhuma transferencia ou taxa iniciada."; return CareerCommandGate.Status.REJECTED; }
            var roots = roots(); Object from = origin.get(), to = destination.get(), selected = player.get();
            if (primary.get() == null || secondary.get() == null || roots.primary() != primary.get() || roots.secondary() != secondary.get()
                    || from == null || to == null || selected == null) { diagnostic = "Carreira ou referencias mudaram antes da chamada nativa; nenhum efeito executado."; return CareerCommandGate.Status.REJECTED; }
            // Before invocation failures are no-effect; never refresh stale request data to make it pass.
            try {
                requireSelection(roots, from, to, selected);
                if (list(from, "kc").get(request.playerIndex()) != selected || !matches(request, roots, from, to)) {
                    diagnostic = "Elenco, jogador ou data mudaram desde o pedido; rotina nativa nao chamada.";
                    return CareerCommandGate.Status.REJECTED;
                }
            } catch (IllegalStateException | IndexOutOfBoundsException stale) {
                diagnostic = "Pre-validacao recusou antes da chamada nativa: " + stale.getMessage();
                return CareerCommandGate.Status.REJECTED;
            }
            if (nativeGuards(roots, selected, to, request.nativeListed() || request.terms().isEmpty()) != 1) {
                diagnostic = "Limite/elegibilidade de destino mudou; nenhuma transferencia ou taxa aplicada.";
                return CareerCommandGate.Status.REJECTED;
            }
            var sourceBefore = new ArrayList<Object>(list(from, "kc")); var targetBefore = new ArrayList<Object>(list(to, "kc"));
            var trackersBefore = new ArrayList<>(list(roots.primary(), "bt")); var historyBefore = new ArrayList<>(list(roots.primary(), "bo"));
            long sourceBalance = ((Number) call(from, "kb")).longValue(), targetBalance = ((Number) call(to, "kb")).longValue();
            // Capture selection while the sorter still corresponds to the unchanged model.
            LoanRosterRefresh.Target view = LoanRosterRefresh.capture(runtime, from, to);
            // One original method invocation only; no direct roster/field mutation, duration override or rollback.
            diagnostic = "Chamada nativa iniciada; se interrompida, resultado incerto e nao deve ser repetido.";
            if (request.terms().isPresent() && !request.nativeListed()) {
                var sourceExpected = predicted(request, true); var targetExpected = predicted(request, false);
                try { playerType.getMethod("q", clubType).invoke(selected, to); } // Native effect, no availability-flag rewrite.
                finally { refreshStatus = LoanRosterRefresh.refresh(view); } // Also refresh a partial/uncertain move; never imply rollback.
                var sourceAfterLoan = new ArrayList<>(sourceBefore); sourceAfterLoan.remove(request.playerIndex());
                var targetAfterLoan = new ArrayList<>(targetBefore); targetAfterLoan.add(selected);
                var loanTrackers = list(roots.primary(), "bt"); var loanHistory = list(roots.primary(), "bo");
                if (call(selected, "fg") != to || !same(sourceAfterLoan, list(from, "kc")) || !same(targetAfterLoan, list(to, "kc"))
                        || loanTrackers.size() != trackersBefore.size() + 1 || !same(trackersBefore, loanTrackers.subList(0, trackersBefore.size()))
                        || loanHistory.size() != historyBefore.size() + 1 || !same(historyBefore, loanHistory.subList(0, historyBefore.size()))
                        || !finance.snapshot(from).equals(request.terms().orElseThrow().originFinance())
                        || !finance.snapshot(to).equals(request.terms().orElseThrow().destinationFinance())) {
                    diagnostic = "Efeito do emprestimo divergente antes da taxa; nao houve tentativa de cobranca. Resultado incerto, nao repetir.";
                    return CareerCommandGate.Status.OUTCOME_UNKNOWN;
                }
                if (request.fee() > 0) {
                    diagnostic = "Emprestimo observado; debito da taxa iniciado. Falha parcial e incerta, nunca repetir.";
                    clubType.getMethod("w", int.class, int.class).invoke(to, request.fee(), 7);
                    if (!finance.snapshot(to).equals(targetExpected)) {
                        diagnostic = "Debito da taxa divergente; credito nao iniciado. Resultado incerto, nao repetir.";
                        return CareerCommandGate.Status.OUTCOME_UNKNOWN;
                    }
                    diagnostic = "Emprestimo e debito observados; credito da taxa iniciado. Falha parcial e incerta, nunca repetir.";
                    clubType.getMethod("v", int.class, int.class).invoke(from, request.fee(), 6);
                }
                nativeCode = 1;
                diagnostic = "Regra NOVA do mod executada; taxa " + request.fee() + " do destino para origem. " + request.release().reason();
                if (!finance.snapshot(from).equals(sourceExpected) || !finance.snapshot(to).equals(targetExpected)) {
                    diagnostic += " / pos-validacao financeira divergente; nao repetir";
                    return CareerCommandGate.Status.OUTCOME_UNKNOWN;
                }
            } else {
                try { nativeCode = ((Number) runtime.loadClass("best.l").getMethod("a", playerType, clubType).invoke(null, selected, to)).intValue(); }
                finally { refreshStatus = LoanRosterRefresh.refresh(view); }
                diagnostic = LoanDiagnostics.nativeResult(nativeCode);
            }
            boolean common = roots().primary() == primary.get() && roots().secondary() == secondary.get() && clock(roots) == request.clock()
                    && Math.addExact(sourceBalance, request.fee()) == ((Number) call(from, "kb")).longValue()
                    && Math.subtractExact(targetBalance, request.fee()) == ((Number) call(to, "kb")).longValue();
            if (nativeCode != 1) {
                return common && nativeCode >= 2 && nativeCode <= 6 && call(selected, "fg") == from
                        && same(sourceBefore, list(from, "kc")) && same(targetBefore, list(to, "kc"))
                        && same(trackersBefore, list(roots.primary(), "bt")) && same(historyBefore, list(roots.primary(), "bo"))
                        ? CareerCommandGate.Status.REJECTED : CareerCommandGate.Status.OUTCOME_UNKNOWN;
            }
            sourceBefore.remove(request.playerIndex()); targetBefore.add(selected);
            List<?> trackers = list(roots.primary(), "bt"), history = list(roots.primary(), "bo");
            boolean changed = common && call(selected, "fg") == to && same(sourceBefore, list(from, "kc")) && same(targetBefore, list(to, "kc"))
                    && trackers.size() == trackersBefore.size() + 1 && same(trackersBefore, trackers.subList(0, trackersBefore.size()))
                    && history.size() == historyBefore.size() + 1 && same(historyBefore, history.subList(0, historyBefore.size()));
            if (!changed) return CareerCommandGate.Status.OUTCOME_UNKNOWN;
            Object tracker = trackers.get(trackers.size() - 1);
            boolean applied = call(tracker, "x") == selected && call(tracker, "tP") == from
                    && ((Number) call(tracker, "tO")).longValue() == Math.addExact(request.clock(), 366L * 86400000L);
            // UI failure is reported separately: the observed transfer must not be retried or called rolled back.
            refreshStatus = LoanRosterRefresh.refresh(view);
            return applied ? CareerCommandGate.Status.APPLIED : CareerCommandGate.Status.OUTCOME_UNKNOWN;
        }
    }
    private static boolean same(List<?> before, List<?> after) {
        if (before.size() != after.size()) return false;
        for (int i = 0; i < before.size(); i++) if (before.get(i) != after.get(i)) return false;
        return true;
    }
    private static int identityIndex(List<?> values, Object target) {
        int index = -1;
        for (int i = 0; i < values.size(); i++) if (values.get(i) == target) {
            if (index >= 0) throw new IllegalStateException("Repeated object identity"); index = i;
        }
        return index;
    }
    private static List<Player> snapshot(List<?> players) throws Exception {
        if (players.size() > 500) throw new IllegalStateException("Roster bound exceeded");
        var result = new ArrayList<Player>();
        for (Object player : players) result.add(new Player(number(player, "gD"), (String) call(player, "getNome"), number(player, "getIdade"),
                number(player, "getPosicao"), number(player, "fi"), number(player, "fk")));
        return result;
    }
    private static long clock(CareerCommandDispatcher.Roots roots) throws Exception { return ((Number) call(roots.primary(), "bc")).longValue(); }
    private static Object call(Object target, String method) throws Exception { return CareerBridge.call(target, method); }
    private static int number(Object target, String method) throws Exception { return ((Number) call(target, method)).intValue(); }
    private static List<?> list(Object target, String method) throws Exception { return (List<?>) call(target, method); }
    private static void edt() { if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Original loan access requires EDT"); }
}
