package br.vbrasfoot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

final class Profile {
    static final String ID = "brasfoot22-23";
    static final String SHA256 = "2fa65476a92611419ce28c0cfd158a0c7f741af235aa988b0bed00c44443bb1c";
    static final String ENTRYPOINT = "best.h2";
    static final String[] LIBRARIES = {
        "kryo-4.0.2.jar", "minlog-1.3.0.jar", "objenesis-2.1.jar", "reflectasm-1.10.1-shaded.jar"
    };

    static Path verify(Path gameDirectory) throws Exception {
        Path executable = gameDirectory.resolve("bf22-23.exe");
        if (!Files.isRegularFile(executable)) throw new IOException("Executavel nao encontrado: " + executable);
        String actual = digest(executable);
        if (!SHA256.equals(actual)) {
            throw new IOException("Versao nao suportada. Esperado SHA-256 " + SHA256 + "; encontrado " + actual
                    + ". Nenhum codigo do jogo foi iniciado.");
        }
        return executable;
    }

    static String digest(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(path)) {
            byte[] buffer = new byte[65536];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
