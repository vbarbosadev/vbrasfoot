package br.vbrasfoot;

import java.nio.file.*;
import java.nio.channels.FileChannel;

/** Explicit offline installation relocation; never touches a save or any financial/loan history. */
final class CareerPortableInstallationTool {
    public static void main(String[] args) throws Exception {
        if (args.length != 3 || !args[0].equals("--confirm-complete-installation-copy"))
            throw new IllegalArgumentException("Expected --confirm-complete-installation-copy <game-directory> <legacy-save-directory>");
        Path game = Path.of(args[1]).toRealPath(), saves = game.resolve("sav").toRealPath();
        Path home = game.resolve("vbrasfoot"); CareerSidecarStore.plain(home);
        Path lock = home.resolve(".runtime.lock"); CareerSidecarStore.plain(lock);
        try (var channel = FileChannel.open(lock, StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
             var held = channel.tryLock()) {
            if (held == null) throw new IllegalStateException("Close the game before relocating metadata");
            var associations = new CareerSaveAssociations(home.resolve("data/.career-state/associations"), saves);
            associations.enablePortable(Path.of(args[2]));
            for (var entry : associations.inventory())
                System.out.println("PRESERVED " + entry.handle() + " -> " + entry.directory().resolve(entry.name()));
            System.out.println("PORTABLE: IDs/history preserved; no save write, credit or effect reconciliation.");
        }
    }
}
