package femboys.are.cute.installer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class Installer {

    public static final String MANIFEST = "femcraft-installer.json";
    private static final Pattern DRIVE = Pattern.compile("^[A-Za-z]:.*");
    private static final Set<String> HOSTS = Set.of("cdn.modrinth.com", "github.com", "raw.githubusercontent.com", "gitlab.com");

    public interface Listener {
        void status(String text);

        void progress(double value);
    }

    public static final class Installed {
        public final String versionId;
        public final String versionNumber;
        public final String minecraft;
        public final boolean cheats;
        final Map<String, Object> files;
        final String versionDir;

        Installed(Map<String, Object> m) {
            versionId = Json.str(m, "versionId");
            versionNumber = Json.str(m, "versionNumber");
            minecraft = Json.str(m, "minecraft");
            cheats = Json.bool(m, "cheats");
            Map<String, Object> f = Json.obj(m, "files");
            files = f == null ? new LinkedHashMap<>() : f;
            versionDir = Json.str(m, "versionDir");
        }
    }

    private static final class Item {
        final String path;
        String url;
        String zipEntry;
        Path local;
        String sha1;
        String sha512;
        long size;
        boolean optional;

        Item(String path) {
            this.path = path;
        }
    }

    private final Target target;
    private final Remote.PackVersion pack;
    private final boolean cheats;
    private final Listener listener;
    private final byte[] icon;
    private final AtomicLong done = new AtomicLong();
    private long total = 1;

    public Installer(Target target, Remote.PackVersion pack, boolean cheats, byte[] icon, Listener listener) {
        this.target = target;
        this.pack = pack;
        this.cheats = cheats;
        this.icon = icon;
        this.listener = listener;
    }

    public static Installed installed(Target t, String mc) {
        Path m = t.gameDir(mc).resolve(MANIFEST);
        if (!Files.isRegularFile(m)) return null;
        try {
            return new Installed(Json.obj(Json.parse(Files.readString(m, StandardCharsets.UTF_8))));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    public static String runningLauncher() {
        long self = ProcessHandle.current().pid();
        try (Stream<ProcessHandle> s = ProcessHandle.allProcesses()) {
            return s.filter(p -> p.pid() != self)
                    .map(p -> p.info().command().orElse(""))
                    .map(c -> c.replace('\\', '/').toLowerCase(Locale.ROOT))
                    .filter(Installer::isLauncher)
                    .findFirst().map(c -> "Minecraft Launcher").orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    static boolean isLauncher(String cmd) {
        String name = cmd.substring(cmd.lastIndexOf('/') + 1);
        if (name.equals("java.exe") || name.equals("javaw.exe") || name.equals("java")) return false;
        if (name.equals("minecraftlauncher.exe") || name.equals("minecraft-launcher")) return true;
        if (name.equals("minecraft.exe")) {
            return cmd.contains("/minecraft launcher/") || cmd.contains("microsoft.4297127d64ec6") || cmd.contains("/xboxgames/");
        }
        return cmd.endsWith("/minecraft.app/contents/macos/launcher");
    }

    public void run() throws Exception {
        String mc = pack.minecraft;
        Path game = target.gameDir(mc);
        Files.createDirectories(game);
        if (target.kind == Target.Kind.MINECRAFT && runningLauncher() != null) {
            throw new IOException("Please close the Minecraft Launcher first, then try again.");
        }
        Path staging = game.resolve(".femcraft-staging");
        deleteTree(staging);
        Files.createDirectories(staging);
        try {
            install(mc, game, staging);
        } catch (FileSystemException e) {
            String why = e.getReason() == null || e.getReason().isBlank() ? e.getClass().getSimpleName() : e.getReason();
            throw new IOException("Could not write " + e.getFile() + " (" + why + "). Close Minecraft and your launcher, then try again.", e);
        } finally {
            deleteTree(staging);
        }
    }

    private void install(String mc, Path game, Path staging) throws Exception {
        listener.status("Downloading FemCraft " + pack.number);
        listener.progress(-1);
        Path mrpack = staging.resolve("pack.mrpack");
        Http.download(pack.url, mrpack, pack.sha1, pack.sha512, null);

        Installed old = installed(target, mc);
        Map<String, Item> plan = new LinkedHashMap<>();
        String loader;

        try (ZipFile zip = new ZipFile(mrpack.toFile(), StandardCharsets.UTF_8)) {
            ZipEntry ie = zip.getEntry("modrinth.index.json");
            if (ie == null) throw new IOException("Invalid modpack: modrinth.index.json missing");
            Map<String, Object> index;
            try (InputStream in = zip.getInputStream(ie)) {
                index = Json.obj(Json.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
            }
            if (Json.num(index, "formatVersion", 0) != 1 || !"minecraft".equals(Json.str(index, "game"))) {
                throw new IOException("Unsupported modpack format");
            }
            Map<String, Object> deps = Json.obj(index, "dependencies");
            if (deps == null) throw new IOException("Invalid modpack: no dependencies");
            String packMc = Json.str(deps, "minecraft");
            loader = Json.str(deps, "fabric-loader");
            if (loader == null) throw new IOException("This installer only supports Fabric modpacks");
            if (packMc != null && !packMc.equals(mc)) throw new IOException("Modpack is for Minecraft " + packMc + ", expected " + mc);

            for (Object o : Json.arr(index, "files")) {
                Map<String, Object> f = Json.obj(o);
                String path = safe(Json.str(f, "path"));
                Map<String, Object> env = Json.obj(f, "env");
                if (env != null && "unsupported".equals(Json.str(env, "client"))) continue;
                List<Object> dl = Json.arr(f, "downloads");
                String url = null;
                for (Object d : dl) {
                    String u = String.valueOf(d);
                    if (allowed(u)) {
                        url = u;
                        break;
                    }
                }
                if (url == null) throw new IOException("Modpack file has no trusted download: " + path);
                Map<String, Object> hashes = Json.obj(f, "hashes");
                Item it = new Item(path);
                it.url = url;
                it.sha1 = hashes == null ? null : Json.str(hashes, "sha1");
                it.sha512 = hashes == null ? null : Json.str(hashes, "sha512");
                if (it.sha1 == null) throw new IOException("Modpack file without checksum: " + path);
                it.size = Json.num(f, "fileSize", 0);
                plan.put(path, it);
            }

            listener.status("Unpacking configs");
            for (String prefix : new String[]{"overrides/", "client-overrides/"}) {
                List<? extends ZipEntry> entries = java.util.Collections.list(zip.entries());
                for (ZipEntry e : entries) {
                    if (e.isDirectory() || !e.getName().startsWith(prefix)) continue;
                    String path = safe(e.getName().substring(prefix.length()));
                    if (path.isEmpty()) continue;
                    Item it = new Item(path);
                    it.zipEntry = e.getName();
                    Path out = staging.resolve("zip").resolve(path);
                    Files.createDirectories(out.getParent());
                    try (InputStream in = zip.getInputStream(e)) {
                        Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
                    }
                    it.local = out;
                    it.sha1 = sha1(out);
                    it.size = Files.size(out);
                    plan.put(path, it);
                }
            }
        }

        if (cheats) {
            plan.values().removeIf(Installer::isAsteroid);
            Item meteor = new Item("mods/meteor-client-" + mc + ".jar");
            meteor.url = Remote.meteorUrl(mc);
            plan.put(meteor.path, meteor);
            Item addon = new Item("mods/" + Remote.ADDON_JAR);
            addon.url = Remote.updateUrl(mc, Remote.ADDON_JAR);
            addon.optional = true;
            plan.put(addon.path, addon);
        }

        listener.status("Checking for the newest FemCraft build");
        Path latest = staging.resolve("latest-" + Remote.CLIENT_JAR);
        boolean haveLatest;
        try {
            Http.download(Remote.updateUrl(mc, Remote.CLIENT_JAR), latest, null, null, null);
            haveLatest = isJar(latest);
        } catch (Http.NotFound e) {
            haveLatest = false;
        }
        if (haveLatest) {
            Item client = null;
            for (Item it : plan.values()) {
                if (isClient(it.path)) {
                    client = it;
                    break;
                }
            }
            if (client == null) {
                client = new Item("mods/" + Remote.CLIENT_JAR);
                plan.put(client.path, client);
            }
            client.url = null;
            client.zipEntry = null;
            client.local = latest;
            client.sha1 = sha1(latest);
            client.sha512 = null;
            client.size = Files.size(latest);
        }

        total = 1;
        for (Item it : plan.values()) total += it.size > 0 ? it.size : 1_000_000;
        done.set(0);
        listener.status("Downloading mods");
        listener.progress(0);

        Path stage = staging.resolve("files");
        List<Item> fetch = new ArrayList<>();
        for (Item it : plan.values()) {
            if (it.local != null) {
                tick(it);
                continue;
            }
            Path existing = game.resolve(it.path);
            if (it.sha1 != null && Files.isRegularFile(existing) && it.sha1.equalsIgnoreCase(sha1(existing))) {
                it.local = existing;
                tick(it);
                continue;
            }
            fetch.add(it);
        }

        ExecutorService pool = Executors.newFixedThreadPool(6);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (Item it : fetch) {
                futures.add(pool.submit(() -> {
                    Path out = stage.resolve(it.path);
                    try {
                        Http.download(it.url, out, it.sha1, it.sha512, n -> {
                            if (it.size > 0) report(done.addAndGet(n));
                        });
                    } catch (Http.NotFound e) {
                        if (it.optional) {
                            it.local = null;
                            it.url = null;
                            tick(it);
                            return null;
                        }
                        throw new IOException("Download not available: " + it.path, e);
                    }
                    if (it.size <= 0) report(done.addAndGet(1_000_000));
                    if (it.path.endsWith(".jar") && !isJar(out)) throw new IOException("Downloaded file is not a valid jar: " + it.path);
                    if (it.sha1 == null) it.sha1 = sha1(out);
                    it.local = out;
                    return null;
                }));
            }
            for (Future<?> f : futures) {
                try {
                    f.get();
                } catch (java.util.concurrent.ExecutionException e) {
                    Throwable c = e.getCause();
                    if (c instanceof Exception) throw (Exception) c;
                    throw e;
                }
            }
        } finally {
            pool.shutdownNow();
        }
        plan.values().removeIf(it -> it.local == null);

        listener.status("Installing");
        listener.progress(-1);
        Path backup = game.resolve("femcraft-backup").resolve(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")));
        Map<String, Object> oldFiles = old == null ? Map.of() : old.files;

        if (target.kind != Target.Kind.INSTANCES || old == null) {
            Path mods = game.resolve("mods");
            if (Files.isDirectory(mods)) {
                try (DirectoryStream<Path> ds = Files.newDirectoryStream(mods)) {
                    for (Path p : ds) {
                        if (!Files.isRegularFile(p)) continue;
                        String rel = "mods/" + p.getFileName();
                        if (oldFiles.containsKey(rel)) continue;
                        Item it = plan.get(rel);
                        if (it != null && it.local.equals(p)) continue;
                        Path dst = backup.resolve(rel);
                        Files.createDirectories(dst.getParent());
                        move(p, dst);
                    }
                }
            }
        }

        for (Item it : plan.values()) {
            Path dst = game.resolve(it.path);
            if (it.local.equals(dst) || oldFiles.containsKey(it.path) || it.path.startsWith("mods/")) continue;
            if (Files.isRegularFile(dst) && !it.sha1.equalsIgnoreCase(sha1(dst))) {
                Path b = backup.resolve(it.path);
                Files.createDirectories(b.getParent());
                Files.copy(dst, b, StandardCopyOption.REPLACE_EXISTING);
            }
        }

        for (String rel : oldFiles.keySet()) {
            if (plan.containsKey(rel)) continue;
            try {
                Files.deleteIfExists(game.resolve(safe(rel)));
            } catch (IOException ignored) {
            }
        }

        Map<String, Object> files = new LinkedHashMap<>();
        for (Item it : plan.values()) {
            Path dst = game.resolve(it.path);
            if (!it.local.equals(dst)) {
                Files.createDirectories(dst.getParent());
                retry(() -> Files.copy(it.local, dst, StandardCopyOption.REPLACE_EXISTING));
            }
            files.put(it.path, it.sha1);
        }

        String versionDir = null;
        if (target.kind == Target.Kind.MINECRAFT) {
            listener.status("Setting up Fabric " + loader);
            versionDir = writeFabricVersion(game, mc, loader);
            if (old != null && old.versionDir != null && !old.versionDir.equals(versionDir)) {
                deleteTree(game.resolve("versions").resolve(safe(old.versionDir)));
            }
            writeProfiles(game, Target.versionId(mc));
        } else if (target.kind == Target.Kind.INSTANCES) {
            listener.status("Creating launcher instance");
            target.writeInstance(mc, loader, icon);
        }

        Map<String, Object> man = new LinkedHashMap<>();
        man.put("installer", Main.VERSION);
        man.put("project", Remote.PROJECT);
        man.put("versionId", pack.id);
        man.put("versionNumber", pack.number);
        man.put("minecraft", mc);
        man.put("loader", loader);
        man.put("cheats", cheats);
        if (versionDir != null) man.put("versionDir", versionDir);
        man.put("installedAt", Instant.now().toString());
        man.put("files", files);
        writeAtomic(game.resolve(MANIFEST), Json.write(man));
        listener.progress(1);
    }

    private void tick(Item it) {
        report(done.addAndGet(it.size > 0 ? it.size : 1_000_000));
    }

    private void report(long value) {
        listener.progress(Math.min(1.0, value / (double) total));
    }

    private String writeFabricVersion(Path game, String mc, String loader) throws IOException {
        String id = Target.versionId(mc);
        Map<String, Object> profile = Json.obj(Http.json(Remote.fabricProfileUrl(mc, loader)));
        profile.put("id", id);
        Path dir = game.resolve("versions").resolve(id);
        Files.createDirectories(dir);
        writeAtomic(dir.resolve(id + ".json"), Json.write(profile));
        Path jar = dir.resolve(id + ".jar");
        if (!Files.exists(jar)) Files.write(jar, new byte[0]);
        return id;
    }

    private void writeProfiles(Path game, String versionId) throws IOException {
        String now = DateTimeFormatter.ISO_INSTANT.format(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
        String iconData = icon == null ? "Furnace" : "data:image/png;base64," + Base64.getEncoder().encodeToString(icon);
        for (String name : new String[]{"launcher_profiles.json", "launcher_profiles_microsoft_store.json"}) {
            Path file = game.resolve(name);
            if (!Files.isRegularFile(file)) continue;
            Map<String, Object> root;
            try {
                root = Json.obj(Json.parse(Files.readString(file, StandardCharsets.UTF_8)));
            } catch (RuntimeException e) {
                continue;
            }
            Files.copy(file, game.resolve(name + ".femcraft-backup"), StandardCopyOption.REPLACE_EXISTING);
            Map<String, Object> profiles = Json.obj(root, "profiles");
            if (profiles == null) {
                profiles = new LinkedHashMap<>();
                root.put("profiles", profiles);
            }
            Object existing = profiles.get("femcraft");
            Map<String, Object> p = existing instanceof Map ? Json.obj(existing) : new LinkedHashMap<>();
            if (!p.containsKey("created")) p.put("created", now);
            p.put("icon", iconData);
            p.put("lastUsed", now);
            p.put("lastVersionId", versionId);
            p.put("name", "FemCraft");
            p.put("type", "custom");
            p.remove("gameDir");
            profiles.put("femcraft", p);
            writeAtomic(file, Json.write(root));
        }
    }

    private static boolean isAsteroid(Item it) {
        String name = it.path.substring(it.path.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
        if (!it.path.startsWith("mods/")) return false;
        if (it.url != null && it.url.contains("/data/" + Remote.ASTEROID_PROJECT + "/")) return true;
        return name.startsWith("asteroid");
    }

    private static boolean isClient(String path) {
        if (!path.startsWith("mods/") || path.indexOf('/', 5) >= 0) return false;
        String name = path.substring(5).toLowerCase(Locale.ROOT);
        return name.startsWith("femcraft") && name.endsWith(".jar") && !name.startsWith("femcraft-meteor");
    }

    private static boolean allowed(String url) {
        try {
            java.net.URI u = java.net.URI.create(url);
            return "https".equalsIgnoreCase(u.getScheme()) && u.getHost() != null && HOSTS.contains(u.getHost().toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    static String safe(String path) throws IOException {
        if (path == null) throw new IOException("Invalid modpack: missing path");
        String p = path.replace('\\', '/');
        if (p.startsWith("/") || DRIVE.matcher(p).matches()) throw new IOException("Unsafe path in modpack: " + path);
        for (String part : p.split("/")) {
            if (part.equals("..") || part.equals(".")) throw new IOException("Unsafe path in modpack: " + path);
        }
        return p;
    }

    static boolean isJar(Path p) {
        try (InputStream in = Files.newInputStream(p)) {
            byte[] h = in.readNBytes(4);
            return h.length == 4 && h[0] == 'P' && h[1] == 'K';
        } catch (IOException e) {
            return false;
        }
    }

    static String sha1(Path p) throws IOException {
        try (InputStream in = Files.newInputStream(p)) {
            MessageDigest d = MessageDigest.getInstance("SHA-1");
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) d.update(buf, 0, n);
            return HexFormat.of().formatHex(d.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    static void writeAtomic(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (OutputStream out = Files.newOutputStream(tmp)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
        move(tmp, file);
    }

    interface IoAction {
        void run() throws IOException;
    }

    static void retry(IoAction action) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt < 20; attempt++) {
            try {
                action.run();
                return;
            } catch (java.nio.file.AtomicMoveNotSupportedException | java.nio.file.NoSuchFileException e) {
                throw e;
            } catch (FileSystemException e) {
                last = e;
                try {
                    Thread.sleep(250);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
        throw last;
    }

    static void move(Path src, Path dst) throws IOException {
        try {
            retry(() -> Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE));
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            retry(() -> Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING));
        } catch (FileSystemException e) {
            retry(() -> Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING));
            try {
                Files.deleteIfExists(src);
            } catch (IOException ignored) {
            }
        }
    }

    static void deleteTree(Path p) {
        if (!Files.exists(p)) return;
        try (Stream<Path> s = Files.walk(p)) {
            s.sorted(Comparator.reverseOrder()).forEach(x -> {
                try {
                    Files.deleteIfExists(x);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }
}
