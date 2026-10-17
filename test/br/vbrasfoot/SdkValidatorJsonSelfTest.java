package br.vbrasfoot;

import br.vbrasfoot.api.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.jar.*;

public final class SdkValidatorJsonSelfTest {
    private static int checks;
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    public static final class NeverInitialize implements Mod {
        static { if (System.nanoTime() != 0) throw new AssertionError("MUST NOT INITIALIZE"); }
        public NeverInitialize() { throw new AssertionError("MUST NOT CONSTRUCT"); }
        public void initialize(ModContext context) { throw new AssertionError("MUST NOT CALL"); }
    }
    private static String run(int expected, String... args) {
        var stdout = new ByteArrayOutputStream(); var stderr = new ByteArrayOutputStream();
        int code = SdkValidator.run(args, new PrintStream(stdout, true, StandardCharsets.UTF_8), new PrintStream(stderr, true, StandardCharsets.UTF_8));
        check(code == expected, "exit code");
        check(stderr.size() == 0, "JSON uses stdout only");
        String output = stdout.toString(StandardCharsets.UTF_8).trim();
        check(output.startsWith("{") && output.endsWith("}") && !output.contains("\n"), "single JSON document");
        return output;
    }
    public static void main(String[] args) throws Exception {
        String description = run(0, "--describe-sdk");
        check(description.contains("\"sandbox\":false") && description.contains("career.sidecars") && description.contains("events.career.observed"), "limits and conditional capabilities");
        check(run(1, "--json").contains("INVALID_ARGUMENTS"), "structured argument error");
        check(run(1, "--json", "--unsupported").contains("INVALID_ARGUMENTS"), "flag not treated as path");
        Path root = Files.createTempDirectory(Path.of("build"), "json-validator-");
        check(run(1, "--json", root.resolve("missing.jar").toString()).contains("MANIFEST_REJECTED"), "missing JAR stage");
        Path jar = root.resolve("mod.jar");
        String resource = NeverInitialize.class.getName().replace('.', '/') + ".class";
        try (var output = new JarOutputStream(Files.newOutputStream(jar));
             var source = SdkValidatorJsonSelfTest.class.getResourceAsStream("/" + resource)) {
            output.putNextEntry(new JarEntry(resource)); source.transferTo(output); output.closeEntry();
            output.putNextEntry(new JarEntry("vbrasfoot.mod.properties"));
            output.write(("id=json.fixture\nname=Quoted \\\"name\\\"\nversion=1\napiVersion=1\ngameProfile=brasfoot22-23\nmainClass="
                    + NeverInitialize.class.getName() + "\npermissions=game.read,ui.register\nrequiredCapabilities=query.scoped\n").getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        String result = run(0, "--json", jar.toString());
        check(result.contains("\"valid\":true") && result.contains("\"codeInitialized\":false") && result.contains("\"installedSetValidated\":false"), "no-init validation scoped honestly");
        check(result.contains("json.fixture") && result.contains("query.scoped"), "manifest exported");
        check(SdkValidator.quote("\"\\\n\t\u0000").equals("\"\\\"\\\\\\u000a\\u0009\\u0000\""), "JSON escapes control chars");
        check(SdkValidator.quote("\ud83d\ude00").equals("\"\\ud83d\\ude00\""), "surrogate escapes");
        check(SdkValidator.quote("açã").equals("\"a\\u00e7\\u00e3\""), "ASCII JSON independent of Windows/JDK output charset");
        Path invalid = root.resolve("broken.jar"); Files.write(invalid, new byte[] {1, 2, 3});
        check(run(1, "--json", invalid.toString()).contains("MANIFEST_REJECTED"), "broken JAR refused");
        Path wrongMain = root.resolve("wrong-main.jar");
        try (var output = new JarOutputStream(Files.newOutputStream(wrongMain))) {
            output.putNextEntry(new JarEntry("vbrasfoot.mod.properties"));
            output.write("id=wrong.main\nname=Wrong\nversion=1\napiVersion=1\ngameProfile=brasfoot22-23\nmainClass=fixture.Missing\n".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        check(run(1, "--json", wrongMain.toString()).contains("ENTRYPOINT_REJECTED"), "entrypoint stage distinct");
        System.out.println("PASS: " + checks + " SDK JSON author-tool checks; synthetic, no game/mod initialization.");
    }
}
