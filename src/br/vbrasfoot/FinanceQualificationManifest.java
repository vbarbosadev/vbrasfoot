package br.vbrasfoot;

import java.io.*;
import java.util.*;

/** Versioned sampled facts, not a promise of confinement, exclusive graph ownership or operation inclusion. */
record FinanceQualificationManifest(int version, Optional<FinanceLedgerMembership.Status> membershipBefore,
                                    Optional<FinanceLedgerMembership.Status> membershipAfter,
                                    Optional<LedgerSerializerProfile.Status> serializerAfterReference) {
    FinanceQualificationManifest {
        Objects.requireNonNull(membershipBefore); Objects.requireNonNull(membershipAfter); Objects.requireNonNull(serializerAfterReference);
        if (version < 0 || version > 1 || version == 0 && (membershipBefore.isPresent() || membershipAfter.isPresent() || serializerAfterReference.isPresent()))
            throw new IllegalArgumentException("Unknown qualification version or invented legacy facts");
    }
    static FinanceQualificationManifest legacy() { return new FinanceQualificationManifest(0, Optional.empty(), Optional.empty(), Optional.empty()); }
    static FinanceQualificationManifest sampled(Optional<FinanceLedgerMembership.Status> before, Optional<FinanceLedgerMembership.Status> after,
                                                 Optional<LedgerSerializerProfile.Status> serializer) {
        return new FinanceQualificationManifest(1, before, after, serializer);
    }
    void write(DataOutputStream out) throws IOException {
        out.writeInt(version); write(out, membershipBefore); write(out, membershipAfter); write(out, serializerAfterReference);
    }
    static FinanceQualificationManifest read(DataInputStream in) throws IOException {
        int version = in.readInt(); if (version < 0 || version > 1) throw new IOException("Unknown qualification manifest version");
        return new FinanceQualificationManifest(version, read(in, FinanceLedgerMembership.Status.class), read(in, FinanceLedgerMembership.Status.class), read(in, LedgerSerializerProfile.Status.class));
    }
    private static void write(DataOutputStream out, Optional<? extends Enum<?>> value) throws IOException {
        out.writeBoolean(value.isPresent()); if (value.isPresent()) out.writeUTF(value.orElseThrow().name());
    }
    private static <E extends Enum<E>> Optional<E> read(DataInputStream in, Class<E> type) throws IOException {
        int present = in.readUnsignedByte(); if (present > 1) throw new IOException("Invalid qualification presence marker");
        return present == 0 ? Optional.empty() : Optional.of(Enum.valueOf(type, in.readUTF()));
    }
}
