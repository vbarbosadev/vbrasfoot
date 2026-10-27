package br.vbrasfoot.sponsormanager;

import java.util.*;
import java.io.*;

public final class SponsorOptionalMessageTest {
    public static void main(String[] args) throws Exception {
        var contract = new SponsorContract(UUID.randomUUID(), "owned", -1, "Clube", "Lubrax", 1000,
                SponsorContract.Cadence.ON_ACTIVATION, 0, false, 0, false, "", null, false, "Ola {clube}!\nOferta de {patrocinador}: {valor}");
        if (contract.logoPng().length != 0) throw new AssertionError("Logo must be optional");
        var decoded = SponsorStore.decode(SponsorStore.encode(List.of(contract))).get(0);
        if (!decoded.message().equals(contract.message()) || decoded.logoPng().length != 0) throw new AssertionError("Text and no-logo must roundtrip");
        if (!decoded.withEnded(true).message().equals(contract.message())) throw new AssertionError("Ending must retain text");
        var oldBytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(oldBytes)) {
            out.writeUTF("vbrasfoot.sponsor-manager/1"); out.writeInt(1);
            out.writeUTF(contract.id().toString()); out.writeUTF(contract.profile()); out.writeInt(-1); out.writeUTF("Clube"); out.writeUTF("Old");
            out.writeLong(1000); out.writeUTF("ON_ACTIVATION"); out.writeInt(0); out.writeBoolean(false); out.writeLong(0);
            out.writeBoolean(false); out.writeUTF(""); out.writeBoolean(false);
            var image = new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            var png = new ByteArrayOutputStream(); javax.imageio.ImageIO.write(image, "PNG", png);
            out.writeInt(png.size()); out.write(png.toByteArray());
        }
        var legacy = SponsorStore.decode(oldBytes.toByteArray()).get(0);
        if (!legacy.sponsorName().equals("Old") || legacy.logoPng().length == 0 || legacy.message().isBlank()) throw new AssertionError("Legacy codec compatibility");
        System.out.println("PASS: 4 optional logo/custom message/legacy codec checks.");
    }
}
