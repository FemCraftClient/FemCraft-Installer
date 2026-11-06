package femboys.are.cute.installer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class Target {

    public enum Kind { MINECRAFT, INSTANCES, FOLDER }

    public final Kind kind;
    public final String label;
    public final Path root;
    public final Path instances;

    private Target(Kind kind, String label, Path root, Path instances) {
        this.kind = kind;
        this.label = label;
        this.root = root;
        this.instances = instances;
    }


    public static String versionId(String mc) {
        return "FemCraft-" + mc;
    }

    public Path instanceDir(String name) {
        return instances.resolve(name);
    }

    public Path gameDir(String name) {
        if (kind != Kind.INSTANCES) return root;
        Path inst = instanceDir(name);
        if (Files.isDirectory(inst.resolve("minecraft")) && !Files.isDirectory(inst.resolve(".minecraft"))) {
            return inst.resolve("minecraft");
        }
        return inst.resolve(".minecraft");
    }

    public String hint() {
        switch (kind) {
            case MINECRAFT: return "Installs into " + root + ". Other mods are moved to a backup folder.";
            case INSTANCES: return "Creates the instance \"<instance>\" in " + label + ".";
            default: return "Installs mods and configs only. Fabric must already be set up for this folder.";
        }
    }

    enum Os { WINDOWS, MAC, LINUX }

    static Os os() {
        String n = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (n.startsWith("windows")) return Os.WINDOWS;
        if (n.startsWith("mac") || n.contains("darwin")) return Os.MAC;
        return Os.LINUX;
    }

    static Path home() {
        return Paths.get(System.getProperty("user.home"));
    }

    static Path appData() {
        String a = System.getenv("APPDATA");
        if (a != null && !a.isBlank()) return Paths.get(a);
        return home().resolve("AppData").resolve("Roaming");
    }

    static Path dataDir(String name) {
        switch (os()) {
            case WINDOWS: return appData().resolve(name);
            case MAC: return home().resolve("Library").resolve("Application Support").resolve(name);
            default:
                String xdg = System.getenv("XDG_DATA_HOME");
                Path base = xdg != null && !xdg.isBlank() ? Paths.get(xdg) : home().resolve(".local").resolve("share");
                return base.resolve(name);
        }
    }

    public static Path dotMinecraft() {
        switch (os()) {
            case WINDOWS: return appData().resolve(".minecraft");
            case MAC: return home().resolve("Library").resolve("Application Support").resolve("minecraft");
            default: return home().resolve(".minecraft");
        }
    }

    public static List<Target> detect() {
        List<Target> out = new ArrayList<>();
        Path mc = dotMinecraft();
        if (Files.isDirectory(mc)) {
            Set<String> names = new LinkedHashSet<>();
            if (Files.exists(mc.resolve("launcher_profiles.json")) || Files.exists(mc.resolve("launcher_profiles_microsoft_store.json"))) {
                names.add("Minecraft Launcher");
            }
            Path tl = os() == Os.WINDOWS ? appData().resolve(".tlauncher") : os() == Os.MAC ? dataDir("tlauncher") : home().resolve(".tlauncher");
            if (Files.isDirectory(tl) || Files.exists(mc.resolve("TlauncherProfiles.json"))) names.add("TLauncher");
            if (Files.isDirectory(mc.resolve("sklauncher"))) names.add("SKLauncher");
            Path ll = os() == Os.WINDOWS ? appData().resolve(".legacylauncher") : home().resolve(".legacylauncher");
            if (Files.isDirectory(ll) || Files.isDirectory(tl.resolve("legacy"))) names.add("Legacy Launcher");
            if (names.isEmpty()) names.add("Minecraft Launcher");
            out.add(new Target(Kind.MINECRAFT, String.join(" / ", names), mc, null));
        }
        addInstanceLauncher(out, "Prism Launcher", dataDir("PrismLauncher"));
        addInstanceLauncher(out, "Prism Launcher", home().resolve(".var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher"));
        addInstanceLauncher(out, "PolyMC", dataDir("PolyMC"));
        addInstanceLauncher(out, "PolyMC", home().resolve(".var/app/org.polymc.PolyMC/data/PolyMC"));
        addInstanceLauncher(out, "MultiMC", dataDir("multimc"));
        addInstanceLauncher(out, "MultiMC", dataDir("MultiMC"));
        if (os() == Os.WINDOWS) {
            for (String base : new String[]{"", "Desktop", "Downloads", "Documents", "Games"}) {
                addInstanceLauncher(out, "MultiMC", home().resolve(base).resolve("MultiMC"));
            }
            addInstanceLauncher(out, "MultiMC", Paths.get("C:\\MultiMC"));
        }
        return out;
    }

    private static void addInstanceLauncher(List<Target> out, String label, Path dir) {
        Target t = instanceLauncher(label, dir);
        if (t == null) return;
        for (Target o : out) {
            if (o.root.toAbsolutePath().normalize().equals(t.root.toAbsolutePath().normalize())) return;
        }
        out.add(t);
    }

    private static Target instanceLauncher(String label, Path dir) {
        if (!Files.isDirectory(dir)) return null;
        Path cfg = null;
        for (String n : new String[]{"prismlauncher.cfg", "polymc.cfg", "multimc.cfg"}) {
            if (Files.isRegularFile(dir.resolve(n))) {
                cfg = dir.resolve(n);
                break;
            }
        }
        if (cfg == null && !Files.isDirectory(dir.resolve("instances"))) return null;
        Path inst = dir.resolve("instances");
        if (cfg != null) {
            try {
                for (String line : Files.readAllLines(cfg, StandardCharsets.UTF_8)) {
                    if (line.startsWith("InstanceDir=")) {
                        String v = line.substring("InstanceDir=".length()).trim();
                        if (!v.isEmpty()) {
                            Path p = Paths.get(v);
                            inst = p.isAbsolute() ? p : dir.resolve(p);
                        }
                    }
                }
            } catch (IOException ignored) {
            }
        }
        return new Target(Kind.INSTANCES, label, dir, inst);
    }

    public static Target fromFolder(Path dir) {
        if (Files.isRegularFile(dir.resolve("prismlauncher.cfg"))) return instanceLauncher("Prism Launcher", dir);
        if (Files.isRegularFile(dir.resolve("polymc.cfg"))) return instanceLauncher("PolyMC", dir);
        if (Files.isRegularFile(dir.resolve("multimc.cfg"))) return instanceLauncher("MultiMC", dir);
        if (Files.isDirectory(dir.resolve("versions")) || Files.exists(dir.resolve("launcher_profiles.json"))) {
            return new Target(Kind.MINECRAFT, "Minecraft folder: " + dir.getFileName(), dir, null);
        }
        return new Target(Kind.FOLDER, "Folder: " + dir.getFileName(), dir, null);
    }

    public Path iconsDir() {
        String icons = "icons";
        for (String n : new String[]{"prismlauncher.cfg", "polymc.cfg", "multimc.cfg"}) {
            Path cfg = root.resolve(n);
            if (!Files.isRegularFile(cfg)) continue;
            try {
                for (String line : Files.readAllLines(cfg, StandardCharsets.UTF_8)) {
                    if (line.startsWith("IconsDir=") && !line.substring(9).isBlank()) icons = line.substring(9).trim();
                }
            } catch (IOException ignored) {
            }
        }
        Path p = Paths.get(icons);
        return p.isAbsolute() ? p : root.resolve(p);
    }

    public void writeInstance(String name, String mc, String loader, byte[] icon) throws IOException {
        Path inst = instanceDir(name);
        Files.createDirectories(inst);
        Path cfg = inst.resolve("instance.cfg");
        List<String> lines = Files.exists(cfg) ? new ArrayList<>(Files.readAllLines(cfg, StandardCharsets.UTF_8)) : new ArrayList<>();
        Map<String, String> want = new LinkedHashMap<>();
        want.put("InstanceType", "OneSix");
        want.put("name", name);
        want.put("iconKey", "femcraft");
        for (int k = 0; k < lines.size(); k++) {
            String l = lines.get(k);
            int eq = l.indexOf('=');
            if (eq <= 0) continue;
            String key = l.substring(0, eq);
            if (want.containsKey(key)) lines.set(k, key + "=" + want.remove(key));
        }
        for (Map.Entry<String, String> e : want.entrySet()) lines.add(e.getKey() + "=" + e.getValue());
        Files.write(cfg, lines, StandardCharsets.UTF_8);

        Map<String, Object> pack = new LinkedHashMap<>();
        List<Object> comps = new ArrayList<>();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("important", Boolean.TRUE);
        m.put("uid", "net.minecraft");
        m.put("version", mc);
        comps.add(m);
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("uid", "net.fabricmc.fabric-loader");
        f.put("version", loader);
        comps.add(f);
        pack.put("components", comps);
        pack.put("formatVersion", 1L);
        Files.writeString(inst.resolve("mmc-pack.json"), Json.write(pack), StandardCharsets.UTF_8);

        if (icon != null) {
            Path icons = iconsDir();
            Files.createDirectories(icons);
            Files.write(icons.resolve("femcraft.png"), icon);
        }
    }
}
