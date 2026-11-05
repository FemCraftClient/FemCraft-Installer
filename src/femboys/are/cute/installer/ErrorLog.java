package femboys.are.cute.installer;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ErrorLog {

    public static final String FILE = "femcraft-installer-error.log";

    private ErrorLog() {
    }

    public static Path write(String what, Map<String, Object> context, Throwable error) {
        StringBuilder b = new StringBuilder();
        b.append("==== ").append(ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)).append(" ====\n");
        b.append("Error: ").append(what).append('\n');
        b.append("Installer: ").append(Main.VERSION).append('\n');
        b.append("Java: ").append(System.getProperty("java.version")).append(" (").append(System.getProperty("java.vendor")).append(") ").append(System.getProperty("java.home")).append('\n');
        b.append("OS: ").append(System.getProperty("os.name")).append(' ').append(System.getProperty("os.version")).append(' ').append(System.getProperty("os.arch")).append('\n');
        b.append("User home: ").append(System.getProperty("user.home")).append('\n');
        if (context != null) {
            for (Map.Entry<String, Object> e : context.entrySet()) {
                b.append(e.getKey()).append(": ").append(e.getValue()).append('\n');
            }
        }
        if (error != null) {
            StringWriter sw = new StringWriter();
            error.printStackTrace(new PrintWriter(sw));
            b.append('\n').append(sw);
        }
        b.append('\n');
        byte[] data = b.toString().getBytes(StandardCharsets.UTF_8);
        for (Path dir : candidates()) {
            try {
                Files.createDirectories(dir);
                Path file = dir.resolve(FILE);
                Files.write(file, data, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                return file;
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static List<Path> candidates() {
        List<Path> out = new ArrayList<>();
        try {
            Path self = Paths.get(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            out.add(Files.isDirectory(self) ? self : self.getParent());
        } catch (Exception ignored) {
        }
        String local = System.getenv("LOCALAPPDATA");
        if (local != null && !local.isBlank()) out.add(Paths.get(local, "FemCraft"));
        out.add(Paths.get(System.getProperty("user.home"), ".femcraft"));
        out.add(Paths.get(System.getProperty("java.io.tmpdir")));
        return out;
    }
}
