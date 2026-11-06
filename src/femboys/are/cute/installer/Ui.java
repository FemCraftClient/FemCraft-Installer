package femboys.are.cute.installer;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

public final class Ui {

    public static final Color WHITE = Color.WHITE;
    public static final Color ACCENT = new Color(0x9678DF);
    public static Font light = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    public static Font bold = new Font(Font.SANS_SERIF, Font.BOLD, 12);

    private Ui() {
    }

    public static void loadFonts() {
        light = font("/assets/fonts/OpenSans-Light.ttf", light);
        bold = font("/assets/fonts/Montserrat-Bold.ttf", bold);
    }

    private static Font font(String res, Font fallback) {
        try (InputStream in = Ui.class.getResourceAsStream(res)) {
            if (in == null) return fallback;
            return Font.createFont(Font.TRUETYPE_FONT, in);
        } catch (Exception e) {
            return fallback;
        }
    }

    public static BufferedImage image(String res) {
        try (InputStream in = Ui.class.getResourceAsStream(res)) {
            return in == null ? null : ImageIO.read(in);
        } catch (Exception e) {
            return null;
        }
    }

    static Graphics2D prep(Graphics g, float alpha) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setComposite(AlphaComposite.SrcOver.derive(Math.max(0f, Math.min(1f, alpha))));
        return g2;
    }

    static void centered(Graphics2D g, String text, int cx, int baseline) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, cx - fm.stringWidth(text) / 2, baseline);
    }

    static String spaced(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0) b.append(' ');
            b.append(s.charAt(i));
        }
        return b.toString();
    }

    public static class Fade extends JComponent {
        protected float alpha = 0f;
        private Timer timer;

        public Fade() {
            setOpaque(false);
            setVisible(false);
        }

        public void fade(float to, int ms, Runnable after) {
            if (timer != null) timer.stop();
            if (to > 0) setVisible(true);
            float from = alpha;
            long start = System.currentTimeMillis();
            timer = new Timer(15, null);
            timer.addActionListener(e -> {
                float t = ms <= 0 ? 1f : Math.min(1f, (System.currentTimeMillis() - start) / (float) ms);
                float eased = t < 0.5f ? 2 * t * t : 1 - (float) Math.pow(-2 * t + 2, 2) / 2;
                alpha = from + (to - from) * eased;
                repaint();
                if (t >= 1f) {
                    timer.stop();
                    if (to <= 0) setVisible(false);
                    if (after != null) after.run();
                }
            });
            timer.start();
        }

        public void snap(boolean v) {
            if (timer != null) timer.stop();
            alpha = v ? 1f : 0f;
            setVisible(v);
            repaint();
        }

        public boolean active() {
            return isVisible() && alpha > 0.6f;
        }
    }

    public static final class Group {
        private final List<Fade> items = new ArrayList<>();

        public Group add(Fade... f) {
            items.addAll(List.of(f));
            return this;
        }

        public void fade(float to, int ms, Runnable after) {
            for (int i = 0; i < items.size(); i++) items.get(i).fade(to, ms, i == 0 ? after : null);
            if (items.isEmpty() && after != null) after.run();
        }

        public void snap(boolean v) {
            for (Fade f : items) f.snap(v);
        }
    }

    public static final class Text extends Fade {
        private String text;
        private final float size;
        private final boolean caps;
        private final float base;

        public Text(String text, float size, boolean caps, float base) {
            this.text = text;
            this.size = size;
            this.caps = caps;
            this.base = base;
        }

        public void set(String t) {
            text = t;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (text == null || text.isEmpty()) return;
            Graphics2D g2 = prep(g, alpha * base);
            g2.setFont((caps ? bold : light).deriveFont(size));
            g2.setColor(WHITE);
            FontMetrics fm = g2.getFontMetrics();
            String t = caps ? spaced(text.toUpperCase()) : text;
            String[] lines = wrap(t, fm, getWidth() - 8);
            int lh = fm.getHeight();
            int y = (getHeight() - lh * lines.length) / 2 + fm.getAscent();
            for (String l : lines) {
                if (size < 30f) {
                    Graphics2D sh = (Graphics2D) g2.create();
                    sh.setComposite(AlphaComposite.SrcOver.derive(Math.max(0f, Math.min(1f, alpha * base * 0.22f))));
                    sh.setColor(new Color(10, 30, 50));
                    int[][] offs = {{0, 1}, {1, 1}, {-1, 1}, {0, 2}, {1, 2}, {-1, 2}, {0, 0}};
                    for (int[] o : offs) centered(sh, l, getWidth() / 2 + o[0], y + o[1]);
                    sh.dispose();
                }
                centered(g2, l, getWidth() / 2, y);
                y += lh;
            }
            g2.dispose();
        }

        private static String[] wrap(String t, FontMetrics fm, int width) {
            List<String> out = new ArrayList<>();
            for (String para : t.split("\n")) {
                StringBuilder cur = new StringBuilder();
                for (String w : para.split(" ")) {
                    while (fm.stringWidth(w) > width && w.length() > 1) {
                        if (cur.length() > 0) {
                            out.add(cur.toString());
                            cur = new StringBuilder();
                        }
                        int cut = w.length() - 1;
                        while (cut > 1 && fm.stringWidth(w.substring(0, cut)) > width) cut--;
                        out.add(w.substring(0, cut));
                        w = w.substring(cut);
                    }
                    String next = cur.length() == 0 ? w : cur + " " + w;
                    if (fm.stringWidth(next) > width && cur.length() > 0) {
                        out.add(cur.toString());
                        cur = new StringBuilder(w);
                    } else {
                        cur = new StringBuilder(next);
                    }
                }
                out.add(cur.toString());
            }
            return out.toArray(new String[0]);
        }
    }

    public static final class Button extends Fade {
        private String text;
        private Runnable action;
        private float hover;
        private final Timer anim;
        private boolean over;
        private boolean enabled = true;

        public Button(String text, Runnable action) {
            this.text = text;
            this.action = action;
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            anim = new Timer(15, e -> {
                float target = over && enabled ? 1f : 0f;
                hover += (target - hover) * 0.22f;
                if (Math.abs(target - hover) < 0.01f) {
                    hover = target;
                    ((Timer) e.getSource()).stop();
                }
                repaint();
            });
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    over = true;
                    anim.start();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    over = false;
                    anim.start();
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (enabled && active() && contains(e.getPoint()) && Button.this.action != null) Button.this.action.run();
                }
            });
        }

        public void set(String t) {
            text = t;
            repaint();
        }

        public void action(Runnable r) {
            action = r;
        }

        public void allow(boolean v) {
            enabled = v;
            setCursor(Cursor.getPredefinedCursor(v ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            anim.start();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = prep(g, alpha * (enabled ? 1f : 0.45f));
            int w = getWidth(), h = getHeight();
            if (hover > 0) {
                g2.setComposite(AlphaComposite.SrcOver.derive(alpha * hover));
                g2.setColor(WHITE);
                g2.fillRect(0, 0, w, h);
                g2.setComposite(AlphaComposite.SrcOver.derive(alpha * (enabled ? 1f : 0.45f)));
            }
            g2.setColor(WHITE);
            g2.setStroke(new BasicStroke(3f));
            g2.drawRect(1, 1, w - 3, h - 3);
            g2.setFont(bold.deriveFont(12f));
            g2.setColor(mix(WHITE, ACCENT, hover));
            FontMetrics fm = g2.getFontMetrics();
            centered(g2, spaced(text.toUpperCase()), w / 2, (h - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }

    static Color mix(Color a, Color b, float t) {
        return new Color(
                Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    public static final class Selector extends Fade {
        private String label;
        private final List<String> values = new ArrayList<>();
        private int index;
        private IntConsumer onChange;
        private Runnable onValueClick;
        private int hot = -1;

        public Selector(String label) {
            this.label = label;
            MouseAdapter m = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    int z = zone(e.getX(), e.getY());
                    if (z != hot) {
                        hot = z;
                        setCursor(Cursor.getPredefinedCursor(z >= 0 ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                        repaint();
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hot = -1;
                    repaint();
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (!active()) return;
                    int z = zone(e.getX(), e.getY());
                    if (z == 0) step(-1);
                    else if (z == 2) step(1);
                    else if (z == 1 && onValueClick != null) onValueClick.run();
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }

        private int zone(int x, int y) {
            if (y < 22) return -1;
            if (values.size() > 1 && x < 60) return 0;
            if (values.size() > 1 && x > getWidth() - 60) return 2;
            if (onValueClick != null && x >= 60 && x <= getWidth() - 60) return 1;
            return -1;
        }

        private void step(int d) {
            if (values.isEmpty()) return;
            index = (index + d + values.size()) % values.size();
            repaint();
            if (onChange != null) onChange.accept(index);
        }

        public void values(List<String> v, int selected) {
            values.clear();
            values.addAll(v);
            index = Math.max(0, Math.min(selected, v.size() - 1));
            repaint();
        }

        public void label(String l) {
            label = l;
            repaint();
        }

        public int index() {
            return index;
        }

        public void onChange(IntConsumer c) {
            onChange = c;
        }

        public void onValueClick(Runnable r) {
            onValueClick = r;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = prep(g, alpha * 0.75f);
            int w = getWidth();
            g2.setFont(bold.deriveFont(11f));
            g2.setColor(WHITE);
            centered(g2, spaced(label), w / 2, 13);
            g2.setComposite(AlphaComposite.SrcOver.derive(alpha));
            String v = values.isEmpty() ? "" : values.get(index);
            Font f = light.deriveFont(26f);
            g2.setFont(f);
            FontMetrics fm = g2.getFontMetrics();
            while (fm.stringWidth(v) > w - 140 && f.getSize2D() > 14f) {
                f = f.deriveFont(f.getSize2D() - 1f);
                g2.setFont(f);
                fm = g2.getFontMetrics();
            }
            int base = 22 + (getHeight() - 22 - fm.getHeight()) / 2 + fm.getAscent();
            centered(g2, v, w / 2, base);
            if (hot == 1) {
                int tw = fm.stringWidth(v);
                g2.fillRect(w / 2 - tw / 2, base + 5, tw, 1);
            }
            if (values.size() > 1) {
                int cy = 22 + (getHeight() - 22) / 2;
                arrow(g2, 30, cy, -1, hot == 0);
                arrow(g2, w - 30, cy, 1, hot == 2);
            }
            g2.dispose();
        }

        private void arrow(Graphics2D g2, int cx, int cy, int dir, boolean hi) {
            g2.setComposite(AlphaComposite.SrcOver.derive(alpha * (hi ? 1f : 0.6f)));
            g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(cx - 5 * dir, cy - 10, cx + 5 * dir, cy);
            g2.drawLine(cx + 5 * dir, cy, cx - 5 * dir, cy + 10);
        }
    }

    public static final class Check extends Fade {
        private final String label;
        private boolean checked;
        private boolean enabled = true;
        private Runnable onChange;
        private boolean over;

        public Check(String label) {
            this.label = label;
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    over = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    over = false;
                    repaint();
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (!enabled || !active() || !contains(e.getPoint())) return;
                    checked = !checked;
                    repaint();
                    if (onChange != null) onChange.run();
                }
            });
        }

        public boolean checked() {
            return checked;
        }

        public void set(boolean v) {
            checked = v;
            repaint();
        }

        public void allow(boolean v) {
            enabled = v;
            if (!v) checked = false;
            setCursor(Cursor.getPredefinedCursor(v ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            repaint();
        }

        public void onChange(Runnable r) {
            onChange = r;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = prep(g, alpha * (enabled ? (over ? 1f : 0.85f) : 0.4f));
            g2.setFont(bold.deriveFont(12f));
            FontMetrics fm = g2.getFontMetrics();
            String t = spaced(label.toUpperCase());
            int box = 20, gap = 14;
            int total = box + gap + fm.stringWidth(t);
            int x = (getWidth() - total) / 2;
            int y = (getHeight() - box) / 2;
            g2.setColor(WHITE);
            g2.setStroke(new BasicStroke(2.5f));
            g2.drawRect(x, y, box, box);
            if (checked) {
                g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine(x + 5, y + 10, x + 9, y + 14);
                g2.drawLine(x + 9, y + 14, x + 16, y + 6);
            }
            g2.drawString(t, x + box + gap, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }

    public static final class Spinner extends Fade {
        private final Timer spin;
        private float phase;

        public Spinner() {
            spin = new Timer(30, e -> {
                phase = (phase + 30f / 1900f) % 1f;
                repaint();
            });
        }

        @Override
        public void setVisible(boolean v) {
            super.setVisible(v);
            if (spin != null) {
                if (v) spin.start();
                else spin.stop();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = prep(g, alpha);
            double s = Math.min(getWidth(), getHeight()) / 100.0;
            g2.translate(getWidth() / 2.0, getHeight() / 2.0);
            g2.scale(s, s);
            for (int i = 0; i < 12; i++) {
                float o = (float) ((i / 12.0 - phase + 1.0) % 1.0);
                g2.setComposite(AlphaComposite.SrcOver.derive(alpha * o));
                g2.setColor(new Color(0xEAEAEA));
                g2.fill(new RoundRectangle2D.Double(-3, -26, 6, 12, 6, 6));
                g2.rotate(Math.PI / 6);
            }
            g2.dispose();
        }
    }

    public static final class Bar extends Fade {
        private double value = -1;
        private final Timer tick;
        private float phase;

        public Bar() {
            tick = new Timer(16, e -> {
                phase = (phase + 0.012f) % 1f;
                repaint();
            });
        }

        public void set(double v) {
            value = v;
            if (v < 0) tick.start();
            else tick.stop();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = prep(g, alpha * 0.3f);
            int w = getWidth(), h = getHeight();
            g2.setColor(WHITE);
            g2.fillRect(0, 0, w, h);
            g2.setComposite(AlphaComposite.SrcOver.derive(alpha));
            if (value < 0) {
                int seg = w / 4;
                int x = (int) ((w + seg) * phase) - seg;
                g2.fillRect(Math.max(0, x), 0, Math.min(seg, w - Math.max(0, x)), h);
            } else {
                g2.fillRect(0, 0, (int) Math.round(w * Math.min(1, value)), h);
            }
            g2.dispose();
        }
    }

    public static final class Sound extends Fade {
        private boolean muted;
        private final Runnable toggle;

        public Sound(Runnable toggle) {
            this.toggle = toggle;
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseReleased(MouseEvent e) {
                    muted = !muted;
                    Sound.this.toggle.run();
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = prep(g, alpha * 0.8f);
            g2.setColor(WHITE);
            int cy = getHeight() / 2;
            g2.fillRect(4, cy - 4, 5, 8);
            g2.fillPolygon(new int[]{9, 15, 15, 9}, new int[]{cy - 4, cy - 9, cy + 9, cy + 4}, 4);
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            if (muted) {
                g2.drawLine(19, cy - 5, 27, cy + 5);
                g2.drawLine(27, cy - 5, 19, cy + 5);
            } else {
                g2.drawArc(13, cy - 6, 8, 12, -50, 100);
                g2.drawArc(14, cy - 10, 14, 20, -50, 100);
            }
            g2.dispose();
        }
    }

    public static final class Backdrop extends JComponent {
        private final BufferedImage bg;
        private BufferedImage cache;

        public Backdrop(BufferedImage bg) {
            this.bg = bg;
            setOpaque(true);
        }

        @Override
        protected void paintComponent(Graphics g) {
            int w = getWidth(), h = getHeight();
            if (cache == null || cache.getWidth() != w || cache.getHeight() != h) {
                cache = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_RGB);
                Graphics2D c = prep(cache.getGraphics(), 1f);
                c.setColor(new Color(0x557FA1));
                c.fillRect(0, 0, w, h);
                if (bg != null) {
                    double s = Math.max(w / (double) bg.getWidth(), h / (double) bg.getHeight());
                    int iw = (int) Math.ceil(bg.getWidth() * s), ih = (int) Math.ceil(bg.getHeight() * s);
                    c.drawImage(bg, (w - iw) / 2, (h - ih) / 2, iw, ih, null);
                }
                c.setPaint(new GradientPaint(0, 0, new Color(116, 199, 205, 128), 0, h, new Color(85, 127, 161, 222)));
                c.fill(new Rectangle(0, 0, w, h));
                c.dispose();
            }
            g.drawImage(cache, 0, 0, null);
        }
    }
}
