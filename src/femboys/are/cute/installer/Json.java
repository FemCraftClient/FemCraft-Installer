package femboys.are.cute.installer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Json {

    private final String s;
    private int i;

    private Json(String s) {
        this.s = s;
    }

    public static Object parse(String text) {
        Json p = new Json(text);
        p.ws();
        Object v = p.value();
        p.ws();
        if (p.i != p.s.length()) throw p.err("Trailing data");
        return v;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> obj(Object o) {
        if (o instanceof Map) return (Map<String, Object>) o;
        throw new IllegalStateException("Expected JSON object");
    }

    @SuppressWarnings("unchecked")
    public static List<Object> arr(Object o) {
        if (o instanceof List) return (List<Object>) o;
        throw new IllegalStateException("Expected JSON array");
    }

    public static Map<String, Object> obj(Map<String, Object> m, String key) {
        Object o = m.get(key);
        return o instanceof Map ? obj(o) : null;
    }

    public static List<Object> arr(Map<String, Object> m, String key) {
        Object o = m.get(key);
        return o instanceof List ? arr(o) : new ArrayList<>();
    }

    public static String str(Map<String, Object> m, String key) {
        Object o = m.get(key);
        return o == null ? null : String.valueOf(o);
    }

    public static boolean bool(Map<String, Object> m, String key) {
        return Boolean.TRUE.equals(m.get(key));
    }

    public static long num(Map<String, Object> m, String key, long def) {
        Object o = m.get(key);
        return o instanceof Number ? ((Number) o).longValue() : def;
    }

    private RuntimeException err(String msg) {
        return new IllegalStateException(msg + " at JSON position " + i);
    }

    private void ws() {
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t' || c == '﻿') i++;
            else break;
        }
    }

    private Object value() {
        if (i >= s.length()) throw err("Unexpected end");
        char c = s.charAt(i);
        switch (c) {
            case '{': return object();
            case '[': return array();
            case '"': return string();
            case 't': lit("true"); return Boolean.TRUE;
            case 'f': lit("false"); return Boolean.FALSE;
            case 'n': lit("null"); return null;
            default: return number();
        }
    }

    private void lit(String w) {
        if (!s.startsWith(w, i)) throw err("Unexpected token");
        i += w.length();
    }

    private Map<String, Object> object() {
        Map<String, Object> m = new LinkedHashMap<>();
        i++;
        ws();
        if (peek() == '}') {
            i++;
            return m;
        }
        while (true) {
            ws();
            if (peek() != '"') throw err("Expected key");
            String k = string();
            ws();
            if (peek() != ':') throw err("Expected ':'");
            i++;
            ws();
            m.put(k, value());
            ws();
            char c = peek();
            i++;
            if (c == '}') return m;
            if (c != ',') throw err("Expected ',' or '}'");
        }
    }

    private List<Object> array() {
        List<Object> l = new ArrayList<>();
        i++;
        ws();
        if (peek() == ']') {
            i++;
            return l;
        }
        while (true) {
            ws();
            l.add(value());
            ws();
            char c = peek();
            i++;
            if (c == ']') return l;
            if (c != ',') throw err("Expected ',' or ']'");
        }
    }

    private char peek() {
        if (i >= s.length()) throw err("Unexpected end");
        return s.charAt(i);
    }

    private String string() {
        i++;
        StringBuilder b = new StringBuilder();
        while (true) {
            char c = peek();
            i++;
            if (c == '"') return b.toString();
            if (c == '\\') {
                char e = peek();
                i++;
                switch (e) {
                    case 'n': b.append('\n'); break;
                    case 't': b.append('\t'); break;
                    case 'r': b.append('\r'); break;
                    case 'b': b.append('\b'); break;
                    case 'f': b.append('\f'); break;
                    case 'u':
                        if (i + 4 > s.length()) throw err("Bad escape");
                        b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                        break;
                    default: b.append(e);
                }
            } else {
                b.append(c);
            }
        }
    }

    private Number number() {
        int st = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
        String n = s.substring(st, i);
        if (n.isEmpty()) throw err("Unexpected character");
        if (n.contains(".") || n.contains("e") || n.contains("E")) return Double.parseDouble(n);
        try {
            return Long.parseLong(n);
        } catch (NumberFormatException ex) {
            return Double.parseDouble(n);
        }
    }

    public static String write(Object o) {
        StringBuilder b = new StringBuilder();
        write(b, o, 0);
        return b.append('\n').toString();
    }

    private static void indent(StringBuilder b, int n) {
        for (int k = 0; k < n; k++) b.append("  ");
    }

    private static void write(StringBuilder b, Object o, int d) {
        if (o == null) {
            b.append("null");
        } else if (o instanceof Map) {
            Map<?, ?> m = (Map<?, ?>) o;
            if (m.isEmpty()) {
                b.append("{}");
                return;
            }
            b.append("{\n");
            int k = 0;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                indent(b, d + 1);
                quote(b, String.valueOf(e.getKey()));
                b.append(": ");
                write(b, e.getValue(), d + 1);
                if (++k < m.size()) b.append(',');
                b.append('\n');
            }
            indent(b, d);
            b.append('}');
        } else if (o instanceof List) {
            List<?> l = (List<?>) o;
            if (l.isEmpty()) {
                b.append("[]");
                return;
            }
            b.append("[\n");
            for (int k = 0; k < l.size(); k++) {
                indent(b, d + 1);
                write(b, l.get(k), d + 1);
                if (k + 1 < l.size()) b.append(',');
                b.append('\n');
            }
            indent(b, d);
            b.append(']');
        } else if (o instanceof Number || o instanceof Boolean) {
            b.append(o);
        } else {
            quote(b, String.valueOf(o));
        }
    }

    private static void quote(StringBuilder b, String v) {
        b.append('"');
        for (int k = 0; k < v.length(); k++) {
            char c = v.charAt(k);
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default:
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
            }
        }
        b.append('"');
    }
}
