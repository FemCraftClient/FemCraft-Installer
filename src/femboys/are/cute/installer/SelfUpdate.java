package femboys.are.cute.installer;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class SelfUpdate {

    public static final String UPDATED_FLAG = "--updated";
    private static final String RELEASE_API = "https://api.github.com/repos/FemCraftClient/FemCraft-Installer/releases/latest";
    private static final String JAR = "FemCraftInstaller.jar";
    private static final String DIR = ".femcraft-update";

    private SelfUpdate() {
    }

    static boolean windows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    }

    static String assetName() {
        return windows() ? "FemCraftInstaller-Windows.zip" : "FemCraftInstaller-Other.zip";
    }

    static String scriptName() {
        return windows() ? "FemCraftInstaller.bat" : "FemCraftInstaller.sh";
    }

    static Path ownJar() {
        try {
            Path p = Paths.get(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return Files.isRegularFile(p) && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar") ? p : null;
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean launch(String[] args) {
        Path jar = ownJar();
        if (jar == null) return false;
        Path dir = jar.resolveSibling(DIR);
        for (String a : args) {
            if (UPDATED_FLAG.equals(a)) {
                Thread t = new Thread(() -> {
                    try {
                        Thread.sleep(4000);
                    } catch (InterruptedException ignored) {
                    }
                    Installer.deleteTree(dir);
                }, "femcraft-update-cleanup");
                t.setDaemon(true);
                t.start();
                return false;
            }
        }
        Installer.deleteTree(dir);
        try {
            Map<String, Object> release = Json.obj(Http.json(RELEASE_API));
            String tag = Json.str(release, "tag_name");
            if (tag == null) return false;
            tag = tag.trim().replaceFirst("^[vV]", "");
            if (Remote.compareVersions(tag, Main.VERSION) <= 0) return false;
            String url = null;
            for (Object o : Json.arr(release, "assets")) {
                Map<String, Object> a = Json.obj(o);
                if (assetName().equalsIgnoreCase(Json.str(a, "name"))) url = Json.str(a, "browser_download_url");
            }
            if (url == null) return false;

            Files.createDirectories(dir);
            Path zip = dir.resolve(assetName());
            Http.download(url, zip, null, null, null);
            Path newJar = dir.resolve("new-" + JAR);
            Path newScript = dir.resolve("new-" + scriptName());
            boolean haveScript = false;
            try (ZipFile z = new ZipFile(zip.toFile())) {
                for (ZipEntry e : java.util.Collections.list(z.entries())) {
                    if (e.isDirectory()) continue;
                    String name = e.getName().replace('\\', '/');
                    name = name.substring(name.lastIndexOf('/') + 1);
                    Path out = null;
                    if (name.equalsIgnoreCase(JAR)) out = newJar;
                    else if (name.equalsIgnoreCase(scriptName())) {
                        out = newScript;
                        haveScript = true;
                    }
                    if (out == null) continue;
                    try (InputStream in = z.getInputStream(e)) {
                        Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
            if (!Files.isRegularFile(newJar) || !Installer.isJar(newJar)) return false;

            Path helper = dir.resolve("updater.jar");
            Files.copy(jar, helper, StandardCopyOption.REPLACE_EXISTING);
            List<String> cmd = new ArrayList<>();
            cmd.add(javaBinary());
            cmd.add("-cp");
            cmd.add(helper.toString());
            cmd.add(SelfUpdate.class.getName());
            cmd.add(String.valueOf(ProcessHandle.current().pid()));
            cmd.add(newJar.toString());
            cmd.add(jar.toString());
            cmd.add(haveScript ? newScript.toString() : "-");
            cmd.add(jar.resolveSibling(scriptName()).toString());
            new ProcessBuilder(cmd)
                    .directory(jar.getParent().toFile())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            return true;
        } catch (Exception e) {
            Installer.deleteTree(dir);
            return false;
        }
    }

    static String javaBinary() {
        String cmd = ProcessHandle.current().info().command().orElse(null);
        if (cmd != null && Files.isRegularFile(Paths.get(cmd))) return cmd;
        Path bin = Paths.get(System.getProperty("java.home"), "bin");
        Path w = bin.resolve("javaw.exe");
        if (Files.isRegularFile(w)) return w.toString();
        Path e = bin.resolve("java.exe");
        return Files.isRegularFile(e) ? e.toString() : bin.resolve("java").toString();
    }

    public static void main(String[] args) throws Exception {
        long pid = Long.parseLong(args[0]);
        Path newJar = Paths.get(args[1]);
        Path jar = Paths.get(args[2]);
        String newScript = args[3];
        Path script = Paths.get(args[4]);
        ProcessHandle.of(pid).ifPresent(h -> {
            try {
                h.onExit().get(60, TimeUnit.SECONDS);
            } catch (Exception ignored) {
            }
        });
        Thread.sleep(500);
        boolean ok = true;
        try {
            Installer.retry(() -> Files.copy(newJar, jar, StandardCopyOption.REPLACE_EXISTING));
        } catch (Exception e) {
            ok = false;
        }
        if (ok && !"-".equals(newScript)) {
            try {
                Installer.retry(() -> Files.copy(Paths.get(newScript), script, StandardCopyOption.REPLACE_EXISTING));
                script.toFile().setExecutable(true);
            } catch (Exception ignored) {
            }
        }
        new ProcessBuilder(javaBinary(), "-jar", jar.toString(), UPDATED_FLAG)
                .directory(jar.getParent().toFile())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
    }
}
