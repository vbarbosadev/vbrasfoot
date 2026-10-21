package br.vbrasfoot;

import java.io.*;
import java.util.*;
import static br.vbrasfoot.CareerSidecarStore.*;
import static br.vbrasfoot.FinanceSerializationTracker.*;

/** Immutable observations only. Intentionally no INCLUDED state, write authority, target values or output path. */
final class FinanceSerializationEvidence {
    static final int MAX_OBSERVATIONS = 8;
    private static final Set<String> NUMERIC = Set.of("UnsafeIntField", "UnsafeLongField", "AsmIntField", "AsmLongField", "ObjectIntField", "ObjectLongField");
    private static final Set<String> REFERENCES = Set.of("writeObject", "writeObjectOrNull", "writeClass(null)");
    record Operation(FinanceSerializationTracker.Operation capture, FinanceExecutionWitnessStore.State execution, CareerCommandJournal.State receipt) {
        Operation {
            Objects.requireNonNull(capture); Objects.requireNonNull(execution); Objects.requireNonNull(receipt);
            Objects.requireNonNull(capture.operationId()); Objects.requireNonNull(capture.intent());
            Objects.requireNonNull(capture.before()); Objects.requireNonNull(capture.after()); Objects.requireNonNull(capture.executionToSerializer());
            if (execution == FinanceExecutionWitnessStore.State.NO_EFFECT_CONFIRMED || receipt == CareerCommandJournal.State.NO_EFFECT_CONFIRMED)
                throw new IllegalArgumentException("No-effect history contradicts an applied-plan serialization observation");
            if (capture.intent().operation() != CareerCommandGate.Operation.FINANCE_CREDIT && capture.intent().operation() != CareerCommandGate.Operation.FINANCE_DEBIT)
                throw new IllegalArgumentException("Financial operation required");
            validate(capture.fields(), capture.edge());
            validate(capture);
        }
    }
    private static void validate(FinanceSerializationTracker.Operation capture) {
        var facts = capture.qualifications();
        if (facts.version() == 0) return;
        if (capture.before() == Sample.MATCHED_EXPECTED_STATE && facts.membershipBefore().isPresent()
                && facts.membershipBefore().orElseThrow() != FinanceLedgerMembership.Status.UNIQUE_IN_CLUB_LIST
                || capture.after() == Sample.MATCHED_EXPECTED_STATE && facts.membershipAfter().isPresent()
                && facts.membershipAfter().orElseThrow() != FinanceLedgerMembership.Status.UNIQUE_IN_CLUB_LIST)
            throw new IllegalArgumentException("Matched root sample contradicts membership observation");
        if (facts.serializerAfterReference().isPresent()) {
            var profile = facts.serializerAfterReference().orElseThrow();
            if (capture.edge().observed() != 1 || capture.edge().status() == Reference.MISSING || capture.edge().status() == Reference.UNAVAILABLE || capture.edge().status() == Reference.AMBIGUOUS
                    || capture.edge().status() == Reference.EXPECTED_LEDGER_ARGUMENT_OBSERVED && profile != LedgerSerializerProfile.Status.SUPPORTED_SAMPLED_LAYOUT
                    || capture.edge().status() == Reference.UNSUPPORTED_SERIALIZER && profile == LedgerSerializerProfile.Status.SUPPORTED_SAMPLED_LAYOUT)
                throw new IllegalArgumentException("Reference observation contradicts sampled serializer profile");
        }
    }
    record Observation(UUID id, String session, long generation, long writerRevision, SaveRevision output,
                       String executionToken, String requestToken, String journalToken, boolean capacityExceeded,
                       FinanceMutationAssessment.Status serializerWindow, List<Operation> operations) {
        Observation {
            Objects.requireNonNull(id); Objects.requireNonNull(output); Objects.requireNonNull(serializerWindow);
            UUID.fromString(session);
            if (generation < 1 || writerRevision < 1 || !token(executionToken) || !token(requestToken) || !token(journalToken))
                throw new IllegalArgumentException("Committed history tokens and original writer identity required");
            operations = List.copyOf(operations);
            if (operations.isEmpty() || operations.size() > MAX_OPERATIONS) throw new IllegalArgumentException("Bounded nonempty observation required");
            var ids = new HashSet<UUID>(); var keys = new HashSet<String>();
            for (var operation : operations) if (!ids.add(operation.capture().operationId()) || !keys.add(operation.capture().intent().key()))
                throw new IllegalArgumentException("Duplicate operation in writer observation");
        }
        boolean operationInclusionProven() { return false; }
        boolean writeAuthority() { return false; }
    }
    private FinanceSerializationEvidence() { }
    private static boolean token(String value) { return value != null && value.matches("[a-f0-9]{64}"); }
    private static void validate(Fields fields, Edge edge) {
        Objects.requireNonNull(fields); Objects.requireNonNull(edge);
        Objects.requireNonNull(fields.status()); Objects.requireNonNull(edge.status());
        if (fields.observed() < 0 || fields.observed() > 15 || !NUMERIC.containsAll(fields.routes()) || edge.observed() < 0 || edge.observed() > 2 || !REFERENCES.containsAll(edge.routes())
                || fields.status() == Emission.EXPECTED_NUMERIC_ARGUMENTS_OBSERVED && (fields.observed() != 15 || fields.routes().isEmpty())
                || fields.status() == Emission.MISSING && fields.observed() != 0
                || fields.observed() == 0 && !fields.routes().isEmpty() || fields.observed() > 0 && fields.routes().isEmpty()
                || fields.status() == Emission.PARTIAL && (fields.observed() < 1 || fields.observed() >= 15)
                || (fields.status() == Emission.VALUE_MISMATCH || fields.status() == Emission.AMBIGUOUS) && fields.observed() == 0
                || edge.status() == Reference.EXPECTED_LEDGER_ARGUMENT_OBSERVED && (edge.observed() != 1 || edge.routes().size() != 1)
                || edge.status() == Reference.MISSING && edge.observed() != 0
                || edge.status() == Reference.MISSING && !edge.routes().isEmpty()
                || (edge.status() == Reference.REFERENCE_MISMATCH || edge.status() == Reference.UNSUPPORTED_SERIALIZER) && (edge.observed() != 1 || edge.routes().size() != 1)
                || edge.status() == Reference.AMBIGUOUS && edge.observed() != 2)
            throw new IllegalArgumentException("Inconsistent numeric/reference observation");
    }
    static void write(DataOutputStream out, List<Observation> observations) throws IOException {
        write(out, observations, 3);
    }
    static void write(DataOutputStream out, List<Observation> observations, int format) throws IOException {
        if (format != 2 && format != 3) throw new IOException("Unknown serialization observation format");
        out.writeInt(observations.size());
        for (var value : observations) {
            uuid(out, value.id()); out.writeUTF(value.session()); out.writeLong(value.generation()); out.writeLong(value.writerRevision());
            out.writeUTF(value.output().sha256()); out.writeLong(value.output().size());
            out.writeUTF(value.executionToken()); out.writeUTF(value.requestToken()); out.writeUTF(value.journalToken());
            out.writeBoolean(value.capacityExceeded()); out.writeUTF(value.serializerWindow().name()); out.writeInt(value.operations().size());
            for (var item : value.operations()) {
                var op = item.capture(); uuid(out, op.operationId()); out.writeUTF(op.intent().key()); out.writeUTF(op.intent().operation().name()); out.writeUTF(op.intent().payloadDigest());
                out.writeUTF(item.execution().name()); out.writeUTF(item.receipt().name()); out.writeUTF(op.before().name()); out.writeUTF(op.after().name()); out.writeUTF(op.executionToSerializer().name());
                out.writeUTF(op.fields().status().name()); out.writeInt(op.fields().observed()); routes(out, op.fields().routes());
                out.writeUTF(op.edge().status().name()); out.writeInt(op.edge().observed()); routes(out, op.edge().routes());
                if (format == 3) op.qualifications().write(out);
                else if (op.qualifications().version() != 0) throw new IOException("Cannot drop qualification manifest into legacy observation format");
            }
        }
    }
    static List<Observation> read(DataInputStream in) throws IOException {
        return read(in, 3);
    }
    static List<Observation> read(DataInputStream in, int format) throws IOException {
        if (format != 2 && format != 3) throw new IOException("Unknown serialization observation format");
        int count = bounded(in.readInt(), 1, MAX_OBSERVATIONS); var values = new ArrayList<Observation>(); var ids = new HashSet<UUID>();
        for (int i = 0; i < count; i++) {
            UUID id = uuid(in); String session = in.readUTF(); long generation = in.readLong(), revision = in.readLong();
            var output = new SaveRevision(in.readUTF(), in.readLong());
            String execution = in.readUTF(), request = in.readUTF(), journal = in.readUTF(); boolean capacity = in.readBoolean();
            var window = FinanceMutationAssessment.Status.valueOf(in.readUTF()); int size = bounded(in.readInt(), 1, MAX_OPERATIONS);
            var operations = new ArrayList<Operation>();
            for (int n = 0; n < size; n++) {
                UUID operation = uuid(in); var intent = new CareerCommandJournal.Intent(in.readUTF(), CareerCommandGate.Operation.valueOf(in.readUTF()), in.readUTF());
                var state = FinanceExecutionWitnessStore.State.valueOf(in.readUTF()); var receipt = CareerCommandJournal.State.valueOf(in.readUTF());
                var before = Sample.valueOf(in.readUTF()); var after = Sample.valueOf(in.readUTF()); var continuity = Continuity.valueOf(in.readUTF());
                var fields = new Fields(Emission.valueOf(in.readUTF()), in.readInt(), routes(in, NUMERIC));
                var edge = new Edge(Reference.valueOf(in.readUTF()), in.readInt(), routes(in, REFERENCES));
                var facts = format == 3 ? FinanceQualificationManifest.read(in) : FinanceQualificationManifest.legacy();
                operations.add(new Operation(new FinanceSerializationTracker.Operation(operation, intent, before, after, continuity, fields, edge, facts), state, receipt));
            }
            if (!ids.add(id)) throw new IOException("Duplicate writer observation");
            values.add(new Observation(id, session, generation, revision, output, execution, request, journal, capacity, window, operations));
        }
        return List.copyOf(values);
    }
    private static int bounded(int value, int min, int max) throws IOException { if (value < min || value > max) throw new IOException("Observation count outside bounds"); return value; }
    private static void routes(DataOutputStream out, Set<String> values) throws IOException { out.writeInt(values.size()); for (String value : new TreeSet<>(values)) out.writeUTF(value); }
    private static Set<String> routes(DataInputStream in, Set<String> allowed) throws IOException {
        int count = bounded(in.readInt(), 0, allowed.size()); var result = new HashSet<String>();
        for (int i = 0; i < count; i++) { String value = in.readUTF(); if (!allowed.contains(value) || !result.add(value)) throw new IOException("Invalid/duplicate route"); }
        return result;
    }
    private static UUID uuid(DataInputStream in) throws IOException { return new UUID(in.readLong(), in.readLong()); }
    private static void uuid(DataOutputStream out, UUID value) throws IOException { out.writeLong(value.getMostSignificantBits()); out.writeLong(value.getLeastSignificantBits()); }
}
