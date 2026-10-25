package com.home.tvlauncher;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * A tiny web page for sending a picture from a phone on the same Wi-Fi to the TV.
 * Runs only while the "Upload from phone" screen is open; one connection at a time.
 */
final class UploadServer {
    interface Listener { void onPicture(File f); }

    static final int PORT = 8765;
    private static final int MAX = 25 * 1024 * 1024;

    private final File dir;
    private final Listener listener;
    private volatile ServerSocket server;

    UploadServer(File dir, Listener listener) {
        this.dir = dir;
        this.listener = listener;
    }

    void start() {
        Thread t = new Thread(() -> {
            try {
                server = new ServerSocket(PORT);
                while (!server.isClosed()) {
                    try (Socket s = server.accept()) {
                        s.setSoTimeout(30_000);
                        handle(s);
                    } catch (Exception ignored) { }
                }
            } catch (Exception ignored) { }
        }, "lumen-upload");
        t.setDaemon(true);
        t.start();
    }

    void stop() {
        try { if (server != null) server.close(); } catch (Exception ignored) { }
    }

    private void handle(Socket s) throws Exception {
        InputStream in = s.getInputStream();
        String head = readHeaders(in);
        String first = head.substring(0, Math.max(0, head.indexOf("\r\n")));
        OutputStream out = s.getOutputStream();
        if (first.startsWith("POST")) {
            int len = intHeader(head, "content-length");
            String type = header(head, "content-type");
            int b = type.indexOf("boundary=");
            if (len <= 0 || len > MAX || b < 0) { reply(out, page("That picture is too large (max 25 MB).")); return; }
            String boundary = "--" + type.substring(b + 9).replace("\"", "").trim();
            byte[] body = readN(in, len);
            File saved = savePart(body, boundary);
            if (saved != null) {
                listener.onPicture(saved);
                reply(out, page("Done! Your picture is now on the TV. You can send another."));
            } else {
                reply(out, page("Couldn't read that file. Try a JPG or PNG photo."));
            }
        } else {
            reply(out, page(null));
        }
    }

    private File savePart(byte[] body, String boundary) throws Exception {
        String text = new String(body, StandardCharsets.ISO_8859_1);
        int start = text.indexOf(boundary);
        while (start >= 0) {
            int hEnd = text.indexOf("\r\n\r\n", start);
            if (hEnd < 0) return null;
            String partHead = text.substring(start, hEnd).toLowerCase(Locale.ROOT);
            int next = text.indexOf("\r\n" + boundary, hEnd + 4);
            if (next < 0) return null;
            if (partHead.contains("filename=")) {
                String ext = partHead.contains(".png") ? ".png" : partHead.contains(".webp") ? ".webp" : ".jpg";
                if (!partHead.contains("image/")) return null;
                File f = new File(dir, "Uploaded-" + System.currentTimeMillis() + ext);
                try (FileOutputStream fo = new FileOutputStream(f)) {
                    fo.write(body, hEnd + 4, next - (hEnd + 4));
                }
                return f.length() > 0 ? f : null;
            }
            start = next + 2;
        }
        return null;
    }

    private static String page(String note) {
        String msg = note == null ? "" : "<p class=n>" + note + "</p>";
        return "<!doctype html><html><head><meta charset=utf-8>"
                + "<meta name=viewport content='width=device-width,initial-scale=1'><title>Lumen</title>"
                + "<style>body{font-family:sans-serif;background:#201a22;color:#fff;margin:0;padding:28px;text-align:center}"
                + "h1{font-weight:500;font-size:24px}p{color:#ddd;line-height:1.5}"
                + ".n{background:#ffffff1a;border-radius:12px;padding:12px}"
                + "input[type=file]{margin:18px 0;color:#ddd;width:100%}"
                + "button{background:#fff;color:#201a22;border:0;border-radius:24px;padding:14px 28px;font-size:17px}"
                + "</style></head><body><h1>Lumen</h1><p>Send a photo to your TV as the background.</p>" + msg
                + "<form method=post enctype=multipart/form-data>"
                + "<input type=file name=photo accept='image/*' required><br>"
                + "<button type=submit>Send to TV</button></form>"
                + "<p style='font-size:13px;color:#aaa'>Landscape photos look best.</p></body></html>";
    }

    private static void reply(OutputStream out, String html) throws Exception {
        byte[] b = html.getBytes(StandardCharsets.UTF_8);
        out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: " + b.length
                + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(b);
        out.flush();
    }

    private static String readHeaders(InputStream in) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int c, state = 0;
        while ((c = in.read()) >= 0 && buf.size() < 16384) {
            buf.write(c);
            state = (c == '\r' && (state == 0 || state == 2)) ? state + 1 : (c == '\n' && (state == 1 || state == 3)) ? state + 1 : 0;
            if (state == 4) break;
        }
        return buf.toString("ISO-8859-1");
    }

    private static byte[] readN(InputStream in, int n) throws Exception {
        byte[] b = new byte[n];
        int off = 0;
        while (off < n) {
            int r = in.read(b, off, n - off);
            if (r < 0) break;
            off += r;
        }
        return b;
    }

    private static String header(String head, String name) {
        for (String line : head.split("\r\n")) {
            int i = line.indexOf(':');
            if (i > 0 && line.substring(0, i).trim().equalsIgnoreCase(name)) return line.substring(i + 1).trim();
        }
        return "";
    }

    private static int intHeader(String head, String name) {
        try { return Integer.parseInt(header(head, name)); } catch (Exception e) { return -1; }
    }
}
