package femboys.are.cute.installer;

import javax.imageio.ImageIO;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLayeredPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Main {

    public static final String VERSION = "1.0.0";
    private static final int W = 1047;
    private static final int H = 581;

    private final JFrame frame = new JFrame("FemCraft Installer");
    private final JLayeredPane stage = new JLayeredPane();
    private final Ui.Spinner spinner = new Ui.Spinner();

    private final Ui.Text intro = new Ui.Text("Hello.", 48f, false, 1f);
    private final Ui.Text load = new Ui.Text("Just getting a few things ready", 48f, false, 1f);

    private final Ui.Text title = new Ui.Text("FemCraft Client", 48f, false, 1f);
    private final Ui.Selector launcher = new Ui.Selector("LAUNCHER");
    private final Ui.Selector version = new Ui.Selector("MINECRAFT");
    private final Ui.Text status = new Ui.Text("", 16f, false, 1f);
    private final Ui.Text hint = new Ui.Text("", 13f, false, 0.7f);
    private final Ui.Check cheats = new Ui.Check("Enable Cheats");
    private final Ui.Text cheatHint = new Ui.Text("", 13f, false, 0.7f);
    private final Ui.Button install = new Ui.Button("Install", null);
    private final Ui.Group main = new Ui.Group();

    private final Ui.Text working = new Ui.Text("", 48f, false, 1f);
    private final Ui.Text step = new Ui.Text("", 16f, false, 0.8f);
    private final Ui.Bar bar = new Ui.Bar();
    private final Ui.Group progress = new Ui.Group();

    private final Ui.Text done = new Ui.Text("Ok. I am finished installing.", 48f, false, 1f);
    private final Ui.Text doneHint = new Ui.Text("", 16f, false, 0.8f);
    private final Ui.Button close = new Ui.Button("Close Installer", () -> System.exit(0));
    private final Ui.Button back = new Ui.Button("Back", null);
    private final Ui.Group finish = new Ui.Group();

    private final Ui.Text error = new Ui.Text("Something went wrong.", 48f, false, 1f);
    private final Ui.Text errorText = new Ui.Text("", 16f, false, 0.85f);
    private final Ui.Button retry = new Ui.Button("Retry", null);
    private final Ui.Button errorClose = new Ui.Button("Close", () -> System.exit(0));
    private final Ui.Group failed = new Ui.Group();

    private final List<Target> targets = new ArrayList<>();
    private List<Remote.PackVersion> packs = new ArrayList<>();
    private List<String> meteor = new ArrayList<>();
    private volatile byte[] icon;
    private Clip clip;
    private volatile Path logPath;
    private volatile String lastStep = "";

    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        Ui.loadFonts();
        SwingUtilities.invokeLater(() -> new Main().start());
    }

    private void start() {
        applyIcon(Ui.image("/assets/textures/icon.png"));
        new Thread(() -> {
            try {
                BufferedImage avatar = ImageIO.read(new java.io.ByteArrayInputStream(Http.bytes(Remote.ICON_URL)));
                if (avatar != null) SwingUtilities.invokeLater(() -> applyIcon(avatar));
            } catch (Exception ignored) {
            }
        }, "femcraft-icon").start();
        Ui.Backdrop bg = new Ui.Backdrop(Ui.image("/assets/images/bg.png"));
        stage.setPreferredSize(new Dimension(W, H));
        bg.setBounds(0, 0, W, H);
        stage.add(bg, JLayeredPane.DEFAULT_LAYER);

        int top = (int) (H * 0.23);
        place(intro, 0, top, W, 70);
        place(load, 0, top, W, 70);

        place(title, 0, 50, W, 70);
        place(launcher, W / 2 - 300, 140, 600, 64);
        place(version, W / 2 - 300, 222, 600, 64);
        place(status, 60, 300, W - 120, 26);
        place(hint, 60, 326, W - 120, 22);
        place(cheats, W / 2 - 200, 372, 400, 30);
        place(cheatHint, 60, 404, W - 120, 22);
        place(install, W / 2 - 110, 452, 220, 54);
        main.add(title, launcher, version, status, hint, cheats, cheatHint, install);

        place(working, 0, top, W, 70);
        place(step, 60, top + 92, W - 120, 26);
        place(bar, W / 2 - 220, top + 140, 440, 3);
        progress.add(working, step, bar);

        place(done, 0, top, W, 70);
        place(doneHint, 60, top + 92, W - 120, 50);
        place(back, W / 2 - 230, top + 170, 220, 54);
        place(close, W / 2 + 10, top + 170, 220, 54);
        finish.add(done, doneHint, back, close);

        place(error, 0, top, W, 70);
        place(errorText, 80, top + 80, W - 160, 110);
        place(retry, W / 2 - 230, top + 205, 220, 54);
        place(errorClose, W / 2 + 10, top + 205, 220, 54);
        failed.add(error, errorText, retry, errorClose);
        errorText.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                Path log = logPath;
                if (log == null || !errorText.active()) return;
                try {
                    java.awt.Desktop.getDesktop().open(log.toFile());
                } catch (Exception ignored) {
                }
            }
        });

        place(spinner, W - 90, H - 90, 40, 40);

        launcher.onChange(i -> refresh());
        launcher.onValueClick(this::chooseFolderIfNeeded);
        version.onChange(i -> refresh());
        cheats.onChange(this::refreshCheatHint);
        install.action(this::doInstall);
        back.action(() -> finish.fade(0f, 600, () -> {
            refresh();
            main.fade(1f, 800, null);
        }));

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(false);
        frame.setContentPane(stage);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        music();
        spinner.fade(1f, 600, null);
        AtomicBoolean introDone = new AtomicBoolean();
        AtomicBoolean dataDone = new AtomicBoolean();
        Object[] failure = new Object[1];
        Runnable proceed = () -> {
            if (!introDone.get() || !dataDone.get()) return;
            load.fade(0f, 800, () -> {
                if (failure[0] != null) {
                    showError(String.valueOf(failure[0]), this::reload);
                } else {
                    showMain();
                }
            });
        };
        intro.fade(1f, 1000, () -> new Timer(2000, e -> {
            ((Timer) e.getSource()).stop();
            intro.fade(0f, 1000, () -> load.fade(1f, 1000, () -> {
                introDone.set(true);
                proceed.run();
            }));
        }).start());
        fetch(failure, () -> {
            dataDone.set(true);
            proceed.run();
        });
    }

    private void fetch(Object[] failure, Runnable after) {
        logPath = null;
        new Thread(() -> {
            String err = null;
            List<Remote.PackVersion> p = null;
            List<String> m = null;
            List<Target> t = null;
            try {
                p = Remote.packVersions();
                if (p.isEmpty()) err = "No FemCraft versions found on Modrinth.";
                m = Remote.meteorVersions();
                t = Target.detect();
            } catch (Exception e) {
                err = e instanceof java.net.UnknownHostException ? "No internet connection. Check your connection and try again." : "Could not reach Modrinth: " + message(e);
                logPath = ErrorLog.write(err, null, e);
            }
            if (err != null && logPath == null) logPath = ErrorLog.write(err, null, null);
            String fe = err;
            List<Remote.PackVersion> fp = p;
            List<String> fm = m;
            List<Target> ft = t;
            SwingUtilities.invokeLater(() -> {
                failure[0] = fe;
                if (fe == null) {
                    packs = fp;
                    meteor = fm;
                    targets.clear();
                    targets.addAll(ft);
                }
                after.run();
            });
        }, "femcraft-fetch").start();
    }

    private void reload() {
        failed.fade(0f, 600, () -> load.fade(1f, 800, () -> {
            Object[] failure = new Object[1];
            fetch(failure, () -> load.fade(0f, 800, () -> {
                if (failure[0] != null) showError(String.valueOf(failure[0]), this::reload);
                else showMain();
            }));
        }));
    }

    private void showMain() {
        List<String> mcs = new ArrayList<>();
        for (Remote.PackVersion p : packs) mcs.add(p.minecraft);
        int sel = 0;
        for (Target t : targets) {
            boolean found = false;
            for (int k = 0; k < packs.size(); k++) {
                Installer.Installed in = Installer.installed(t, packs.get(k).minecraft);
                if (in != null && packs.get(k).minecraft.equals(in.minecraft)) {
                    sel = k;
                    found = true;
                    break;
                }
            }
            if (found) break;
        }
        version.values(mcs, sel);
        launcherValues(0);
        refresh();
        spinner.fade(0f, 600, null);
        main.fade(1f, 1000, null);
    }

    private void launcherValues(int selected) {
        List<String> names = new ArrayList<>();
        for (Target t : targets) names.add(t.label);
        names.add("Choose folder…");
        launcher.values(names, selected);
    }

    private Target target() {
        int i = launcher.index();
        return i < targets.size() ? targets.get(i) : null;
    }

    private Remote.PackVersion pack() {
        return packs.get(Math.min(version.index(), packs.size() - 1));
    }

    private void chooseFolderIfNeeded() {
        if (target() != null) return;
        JFileChooser fc = new JFileChooser(Target.dotMinecraft().toFile());
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setDialogTitle("Choose your launcher or Minecraft folder");
        if (fc.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) return;
        Path dir = fc.getSelectedFile().toPath();
        Target t = Target.fromFolder(dir);
        if (t == null) return;
        targets.add(t);
        launcherValues(targets.size() - 1);
        refresh();
    }

    private void refresh() {
        Target t = target();
        Remote.PackVersion p = pack();
        boolean meteorOk = meteor.contains(p.minecraft);
        cheats.allow(meteorOk);
        if (t == null) {
            status.set("Click above to choose a folder");
            hint.set("Prism, MultiMC, PolyMC, ATLauncher, Modrinth App, CurseForge or any .minecraft folder");
            install.set("Install");
            install.allow(false);
            refreshCheatHint();
            return;
        }
        install.allow(true);
        hint.set(t.hint().replace("<version>", p.minecraft));
        Installer.Installed in = Installer.installed(t, p.minecraft);
        if (in == null) {
            status.set("Not installed \u00b7 FemCraft " + p.number);
            install.set("Install");
        } else if (!p.minecraft.equals(in.minecraft)) {
            status.set("Installed for Minecraft " + in.minecraft + " \u00b7 switches to " + p.minecraft);
            install.set("Install");
        } else if (p.id.equals(in.versionId)) {
            status.set("Installed \u00b7 FemCraft " + in.versionNumber + " \u00b7 up to date");
            install.set("Update");
        } else {
            status.set("Installed \u00b7 FemCraft " + in.versionNumber + " \u00b7 update to " + p.number + " available");
            install.set("Update");
        }
        if (meteorOk) cheats.set(in != null && p.minecraft.equals(in.minecraft) && in.cheats);
        refreshCheatHint();
    }

    private void refreshCheatHint() {
        Remote.PackVersion p = pack();
        if (meteor.isEmpty()) {
            cheatHint.set("Meteor Client could not be reached right now.");
        } else if (!meteor.contains(p.minecraft)) {
            cheatHint.set("Meteor Client is not available for Minecraft " + p.minecraft + ".");
        } else if (cheats.checked()) {
            cheatHint.set("Replaces Asteroid with Meteor Client and the FemCraft addon.");
        } else {
            cheatHint.set("Optional. Replaces Asteroid with Meteor Client.");
        }
    }

    private void doInstall() {
        Target t = target();
        if (t == null) {
            chooseFolderIfNeeded();
            return;
        }
        Remote.PackVersion p = pack();
        boolean ch = cheats.checked();
        boolean update = "Update".equals(statusIsUpdate(t, p));
        working.set(update ? "Updating FemCraft " + p.number : "Installing FemCraft " + p.number);
        step.set("");
        bar.set(-1);
        main.fade(0f, 600, () -> {
            spinner.fade(1f, 600, null);
            progress.fade(1f, 800, null);
            new Thread(() -> {
                Installer inst = new Installer(t, p, ch, icon, new Installer.Listener() {
                    @Override
                    public void status(String text) {
                        lastStep = text;
                        SwingUtilities.invokeLater(() -> step.set(text));
                    }

                    @Override
                    public void progress(double value) {
                        SwingUtilities.invokeLater(() -> bar.set(value));
                    }
                });
                Exception fail = null;
                try {
                    inst.run();
                } catch (Exception e) {
                    fail = e;
                    Map<String, Object> ctx = new LinkedHashMap<>();
                    ctx.put("Launcher", t.label + " (" + t.kind + ")");
                    ctx.put("Launcher folder", t.root);
                    ctx.put("Game folder", t.gameDir(p.minecraft));
                    ctx.put("Minecraft", p.minecraft);
                    ctx.put("FemCraft", p.number + " (" + p.id + ")");
                    ctx.put("Cheats", ch);
                    ctx.put("Step", lastStep);
                    logPath = ErrorLog.write(message(e), ctx, e);
                }
                Exception f = fail;
                SwingUtilities.invokeLater(() -> progress.fade(0f, 600, () -> {
                    spinner.fade(0f, 600, null);
                    if (f != null) {
                        showError(message(f), () -> failed.fade(0f, 600, () -> {
                            refresh();
                            main.fade(1f, 800, null);
                        }));
                    } else {
                        done.set(update ? "Ok. I am finished updating." : "Ok. I am finished installing.");
                        doneHint.set(startHint(t, p.minecraft));
                        done.fade(1f, 1000, () -> new Timer(1200, e -> {
                            ((Timer) e.getSource()).stop();
                            doneHint.fade(1f, 800, null);
                            back.fade(1f, 800, null);
                            close.fade(1f, 800, null);
                        }).start());
                    }
                }));
            }, "femcraft-install").start();
        });
    }

    private static String statusIsUpdate(Target t, Remote.PackVersion p) {
        Installer.Installed in = Installer.installed(t, p.minecraft);
        return in != null && p.minecraft.equals(in.minecraft) ? "Update" : "Install";
    }

    private static String startHint(Target t, String mc) {
        switch (t.kind) {
            case MINECRAFT: return "Open your launcher and start the profile \"FemCraft\" (version " + Target.versionId(mc) + ").";
            case INSTANCES: return "Open " + t.label + " and start the instance \"" + Target.instanceName(mc) + "\".";
            default: return "FemCraft was installed into " + t.root + ".";
        }
    }

    private void showError(String msg, Runnable onRetry) {
        spinner.fade(0f, 400, null);
        Path log = logPath;
        errorText.set(log == null ? msg : msg + "\nDetails were saved to " + log + " (click to open)");
        errorText.setCursor(java.awt.Cursor.getPredefinedCursor(log == null ? java.awt.Cursor.DEFAULT_CURSOR : java.awt.Cursor.HAND_CURSOR));
        retry.action(onRetry);
        failed.fade(1f, 800, null);
    }

    private static String message(Throwable e) {
        Throwable c = e;
        while (c.getCause() != null && (c.getMessage() == null || c.getMessage().isBlank())) c = c.getCause();
        String m = c.getMessage();
        if (c instanceof java.net.UnknownHostException) return "No internet connection.";
        if (c instanceof java.net.SocketTimeoutException) return "The connection timed out.";
        if (c instanceof java.nio.file.AccessDeniedException) return "Access denied: " + m;
        if (c instanceof java.nio.file.NoSuchFileException) return "File not found: " + m;
        if (c instanceof java.nio.file.FileSystemException) {
            java.nio.file.FileSystemException fe = (java.nio.file.FileSystemException) c;
            return "File error: " + fe.getFile() + (fe.getReason() != null ? " (" + fe.getReason() + ")" : "");
        }
        if (c instanceof javax.net.ssl.SSLException) return "Secure connection failed: " + m;
        return m == null || m.isBlank() ? c.getClass().getSimpleName() : m;
    }

    private void place(javax.swing.JComponent c, int x, int y, int w, int h) {
        c.setBounds(x, y, w, h);
        stage.add(c, JLayeredPane.PALETTE_LAYER);
    }

    private void music() {
        try (InputStream raw = Main.class.getResourceAsStream("/assets/sounds/audio.wav")) {
            if (raw == null) return;
            AudioInputStream in = AudioSystem.getAudioInputStream(new BufferedInputStream(raw));
            clip = AudioSystem.getClip();
            clip.open(in);
            clip.loop(Clip.LOOP_CONTINUOUSLY);
            Ui.Sound s = new Ui.Sound(() -> {
                if (clip.isRunning()) clip.stop();
                else clip.loop(Clip.LOOP_CONTINUOUSLY);
            });
            place(s, W - 56, 20, 32, 28);
            s.fade(1f, 800, null);
        } catch (Exception ignored) {
        }
    }

    private void applyIcon(BufferedImage img) {
        if (img == null) return;
        List<Image> sizes = new ArrayList<>();
        for (int sz : new int[]{16, 32, 48, 64, 128, 256}) sizes.add(img.getScaledInstance(sz, sz, Image.SCALE_SMOOTH));
        frame.setIconImages(sizes);
        byte[] b = png(img, 128);
        if (b != null) icon = b;
    }

    private static byte[] png(BufferedImage src, int size) {
        try {
            BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = out.createGraphics();
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(src.getScaledInstance(size, size, Image.SCALE_SMOOTH), 0, 0, null);
            g.dispose();
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            ImageIO.write(out, "png", b);
            return b.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }
}
