package femboys.are.cute.installer;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.function.LongConsumer;

public final class Http {

    public static final String USER_AGENT = "FemCraftClient/Installer/" + Main.VERSION + " (femboysarecut3.github.io)";

    private Http() {
    }

    public static final class NotFound extends IOException {
        public NotFound(String url) {
            super("Not found: " + url);
        }
    }

    private static HttpURLConnection open(String url) throws IOException {
        String current = url;
        for (int hop = 0; hop < 8; hop++) {
            URL u = URI.create(current).toURL();
            if (!"https".equalsIgnoreCase(u.getProtocol())) throw new IOException("Refusing insecure URL: " + current);
            HttpURLConnection c = (HttpURLConnection) u.openConnection();
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setRequestProperty("User-Agent", USER_AGENT);
            c.setRequestProperty("Accept-Encoding", "identity");
            int code = c.getResponseCode();
            if (code >= 300 && code < 400) {
                String loc = c.getHeaderField("Location");
                c.disconnect();
                if (loc == null) throw new IOException("Redirect without location: " + current);
                current = URI.create(current).resolve(loc).toString();
                continue;
            }
            if (code == 404 || code == 410) {
                c.disconnect();
                throw new NotFound(url);
            }
            if (code != 200) {
                c.disconnect();
                throw new IOException("HTTP " + code + " for " + url);
            }
            return c;
        }
        throw new IOException("Too many redirects: " + url);
    }

    public static byte[] bytes(String url) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                HttpURLConnection c = open(url);
                try (InputStream in = c.getInputStream()) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    in.transferTo(out);
                    return out.toByteArray();
                } finally {
                    c.disconnect();
                }
            } catch (NotFound | FileNotFoundException e) {
                throw e;
            } catch (IOException e) {
                last = e;
                pause(attempt);
            }
        }
        throw last;
    }

    public static String text(String url) throws IOException {
        return new String(bytes(url), StandardCharsets.UTF_8);
    }

    public static Object json(String url) throws IOException {
        return Json.parse(text(url));
    }

    public static void download(String url, Path target, String sha1, String sha512, LongConsumer progress) throws IOException {
        Files.createDirectories(target.getParent());
        Path tmp = target.resolveSibling(target.getFileName() + ".part");
        IOException last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            long written = 0;
            try {
                HttpURLConnection c = open(url);
                MessageDigest d1 = MessageDigest.getInstance("SHA-1");
                MessageDigest d512 = MessageDigest.getInstance("SHA-512");
                try (InputStream in = c.getInputStream(); OutputStream out = Files.newOutputStream(tmp)) {
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                        d1.update(buf, 0, n);
                        d512.update(buf, 0, n);
                        written += n;
                        if (progress != null) progress.accept(n);
                    }
                } finally {
                    c.disconnect();
                }
                if (sha1 != null && !HexFormat.of().formatHex(d1.digest()).equalsIgnoreCase(sha1)) {
                    throw new IOException("Checksum mismatch for " + target.getFileName());
                }
                if (sha512 != null && !HexFormat.of().formatHex(d512.digest()).equalsIgnoreCase(sha512)) {
                    throw new IOException("Checksum mismatch for " + target.getFileName());
                }
                Installer.move(tmp, target);
                return;
            } catch (NotFound e) {
                Files.deleteIfExists(tmp);
                throw e;
            } catch (java.security.NoSuchAlgorithmException e) {
                throw new IOException(e);
            } catch (IOException e) {
                Files.deleteIfExists(tmp);
                if (progress != null && written > 0) progress.accept(-written);
                last = e;
                pause(attempt);
            }
        }
        throw last;
    }

    private static void pause(int attempt) {
        try {
            Thread.sleep(800L * (attempt + 1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
