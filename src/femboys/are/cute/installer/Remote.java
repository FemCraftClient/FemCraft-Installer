package femboys.are.cute.installer;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Remote {

    public static final String PROJECT = "femcraftclient";
    public static final String UPDATE_BASE = "https://github.com/FemCraftClient/Update/raw/refs/heads/main/";
    public static final String CLIENT_JAR = "femcraft.jar";
    public static final String ADDON_JAR = "femcraft-meteor-addon.jar";
    public static final String ICON_URL = "https://avatars.githubusercontent.com/u/158457625?v=4";
    public static final String ASTEROID_PROJECT = "cFGzaOGg";

    private Remote() {
    }

    public static final class PackVersion {
        public final String id;
        public final String number;
        public final String name;
        public final String minecraft;
        public final String url;
        public final String sha1;
        public final String sha512;
        public final long size;
        public final boolean legacy;

        PackVersion(String id, String number, String name, String minecraft, String url, String sha1, String sha512, long size, boolean legacy) {
            this.legacy = legacy;
            this.id = id;
            this.number = number;
            this.name = name;
            this.minecraft = minecraft;
            this.url = url;
            this.sha1 = sha1;
            this.sha512 = sha512;
            this.size = size;
        }

        public String instance() {
            return legacy ? "FemCraft V1" : "FemCraft " + minecraft;
        }

        public String label() {
            return legacy ? "FemCraft V1 " + number : "FemCraft " + number;
        }
    }

    public static final class Packs {
        public final List<PackVersion> v2;
        public final List<PackVersion> v1;

        Packs(List<PackVersion> v2, List<PackVersion> v1) {
            this.v2 = v2;
            this.v1 = v1;
        }
    }

    public static boolean isLegacy(String number) {
        return number != null && number.startsWith("1.");
    }

    public static Packs packVersions() throws IOException {
        String loaders = URLEncoder.encode("[\"fabric\"]", StandardCharsets.UTF_8);
        Object res = Http.json("https://api.modrinth.com/v2/project/" + PROJECT + "/version?include_changelog=false&loaders=" + loaders);
        Map<String, PackVersion> best = new LinkedHashMap<>();
        Map<String, Integer> rank = new LinkedHashMap<>();
        List<PackVersion> legacy = new ArrayList<>();
        for (Object o : Json.arr(res)) {
            Map<String, Object> v = Json.obj(o);
            String status = Json.str(v, "status");
            if (status != null && !"listed".equals(status)) continue;
            Map<String, Object> file = null;
            for (Object f : Json.arr(v, "files")) {
                Map<String, Object> fm = Json.obj(f);
                String fn = Json.str(fm, "filename");
                if (fn != null && fn.endsWith(".mrpack") && (file == null || Json.bool(fm, "primary"))) file = fm;
            }
            if (file == null) continue;
            Map<String, Object> hashes = Json.obj(file, "hashes");
            String type = Json.str(v, "version_type");
            String number = Json.str(v, "version_number");
            if (isLegacy(number)) {
                String mc = null;
                for (Object g : Json.arr(v, "game_versions")) {
                    String gv = String.valueOf(g);
                    if (mc == null || compareVersions(gv, mc) > 0) mc = gv;
                }
                if (mc == null) continue;
                legacy.add(new PackVersion(Json.str(v, "id"), number, Json.str(v, "name"), mc, Json.str(file, "url"),
                        hashes == null ? null : Json.str(hashes, "sha1"), hashes == null ? null : Json.str(hashes, "sha512"),
                        Json.num(file, "size", 0), true));
                continue;
            }
            int r = "release".equals(type) ? 0 : "beta".equals(type) ? 1 : 2;
            for (Object g : Json.arr(v, "game_versions")) {
                String mc = String.valueOf(g);
                Integer have = rank.get(mc);
                if (have != null && have <= r) continue;
                rank.put(mc, r);
                best.put(mc, new PackVersion(
                        Json.str(v, "id"),
                        Json.str(v, "version_number"),
                        Json.str(v, "name"),
                        mc,
                        Json.str(file, "url"),
                        hashes == null ? null : Json.str(hashes, "sha1"),
                        hashes == null ? null : Json.str(hashes, "sha512"),
                        Json.num(file, "size", 0),
                        false));
            }
        }
        List<PackVersion> out = new ArrayList<>(best.values());
        out.sort(Comparator.comparing((PackVersion p) -> p.minecraft, Remote::compareVersions).reversed());
        legacy.sort(Comparator.comparing((PackVersion p) -> p.number, Remote::compareVersions).reversed());
        return new Packs(out, legacy);
    }

    public static int compareVersions(String a, String b) {
        String[] x = a.split("[.\\-+ ]");
        String[] y = b.split("[.\\-+ ]");
        for (int k = 0; k < Math.max(x.length, y.length); k++) {
            String p = k < x.length ? x[k] : "0";
            String q = k < y.length ? y[k] : "0";
            int c;
            try {
                c = Long.compare(Long.parseLong(p), Long.parseLong(q));
            } catch (NumberFormatException e) {
                c = p.compareTo(q);
            }
            if (c != 0) return c;
        }
        return 0;
    }

    public static List<String> meteorVersions() {
        List<String> out = new ArrayList<>();
        try {
            Map<String, Object> stats = Json.obj(Http.json("https://meteorclient.com/api/stats"));
            Map<String, Object> builds = Json.obj(stats, "builds");
            if (builds != null) out.addAll(builds.keySet());
        } catch (IOException | RuntimeException ignored) {
        }
        return out;
    }

    public static String meteorUrl(String mc) {
        return "https://meteorclient.com/api/download?version=" + URLEncoder.encode(mc, StandardCharsets.UTF_8);
    }

    public static String updateUrl(String mc, String file) {
        return UPDATE_BASE + mc + "/" + file;
    }

    public static String fabricProfileUrl(String mc, String loader) {
        return "https://meta.fabricmc.net/v2/versions/loader/" + enc(mc) + "/" + enc(loader) + "/profile/json";
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
