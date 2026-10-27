package br.vbrasfoot;

import java.util.concurrent.atomic.AtomicInteger;

public final class SponsorNativeMessageFenceSelfTest {
    public static void main(String[] args) throws Exception {
        var edits = new AtomicInteger();
        try (var gate = new CareerCommandGate()) {
            boolean refused = false;
            try { gate.updateSponsorMessage(edits::incrementAndGet); } catch (IllegalStateException expected) { refused = true; }
            if (!refused || edits.get() != 0) throw new AssertionError("Off-EDT message edit accepted");
            CareerBridge.onEdt(() -> { gate.updateSponsorMessage(edits::incrementAndGet); return null; });
            if (edits.get() != 1) throw new AssertionError("Idle EDT message edit rejected");
            for (var kind : CareerCommandGate.IoKind.values()) {
                var fence = gate.beginOriginalIo(kind);
                try {
                    boolean denied = CareerBridge.onEdt(() -> {
                        try { gate.updateSponsorMessage(edits::incrementAndGet); return false; }
                        catch (IllegalStateException expected) { return true; }
                    });
                    if (!denied || edits.get() != 1) throw new AssertionError("Message edit overlapped " + kind);
                } finally { gate.endSave(fence); }
            }
            if (gate.contextInstalled() || !gate.receipts().isEmpty()) throw new AssertionError("Message update granted game command context/financial receipt");
        }
        System.out.println("PASS: native message edits require idle EDT and exclude every original I/O kind; no financial authority.");
    }
}
