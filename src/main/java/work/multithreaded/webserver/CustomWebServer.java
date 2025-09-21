package work.multithreaded.webserver;

import work.multithreaded.service.CustomExecutorService;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class CustomWebServer {
    private final int port;
    private final CustomExecutorService executor;
    private ServerSocket serverSocket;
    private volatile boolean running = false;
    private final Path staticRoot;
    private volatile Instant startedAt;
    private final AtomicLong totalRequests = new AtomicLong(0);
    private Thread acceptThread;

    public CustomWebServer(int port, int threadPoolSize, boolean useVirtualThreads) {
        this.port = port;
        this.executor = new CustomExecutorService(threadPoolSize, useVirtualThreads);
        this.staticRoot = Path.of("static");
    }

    public void start() throws IOException {
        if (running) return;
        running = true;
        this.startedAt = Instant.now();
        serverSocket = new ServerSocket(port);
        System.out.printf("Server started on port %d, static root: %s%n", port, staticRoot.toAbsolutePath().normalize());

        // Платформенный поток-акцептор удерживает JVM живой
        acceptThread = Thread.ofPlatform().name("acceptor").start(() -> {
            while (running) {
                try {
                    Socket client = serverSocket.accept();
                    // обработку клиента оставляем на ваш executor (виртуальные/платформенные — как настроено)
                    executor.execute(() -> handleClient(client));
                } catch (IOException e) {
                    if (running) System.err.println("Accept error: " + e.getMessage());
                }
            }
        });
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                try { serverSocket.close(); } catch (IOException ignore) { }
            }
            if (acceptThread != null && acceptThread.isAlive()) {
                try { acceptThread.join(2_000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) executor.shutdownNow();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                executor.shutdownNow();
            }
            System.out.println("Server stopped");
        }
    }

    public void join() throws InterruptedException {
        if (acceptThread != null) acceptThread.join();
    }

    private void handleClient(Socket clientSocket) {
        try (clientSocket;
             InputStream in = clientSocket.getInputStream();
             BufferedInputStream bin = new BufferedInputStream(in);
             OutputStream out = clientSocket.getOutputStream()) {

            clientSocket.setSoTimeout(10_000);

            HeadPart hp = readUntilDoubleCRLF(bin, 32 * 1024);
            if (hp == null) {
                writeError(out, 400, "Bad Request");
                return;
            }
            Request req = parseHead(hp.head());
            if (req == null || req.requestLine == null) {
                writeError(out, 400, "Bad Request");
                return;
            }

            String[] parts = req.requestLine.split("\\s+");
            if (parts.length < 3) {
                writeError(out, 400, "Bad Request");
                return;
            }
            String method = parts[0].toUpperCase(Locale.ROOT);
            String rawPath = parts[1];
            String version = parts[2];

            byte[] body = new byte[0];
            int contentLength = parseIntSafe(req.headers.get("content-length"), 0);
            if (("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method)) && contentLength > 0) {
                body = readFixedBytes(bin, hp.leftover(), contentLength);
                if (body == null || body.length < contentLength) {
                    writeError(out, 400, "Bad Request");
                    return;
                }
            }

            totalRequests.incrementAndGet();

            if ("GET".equals(method)) {
                routeGet(rawPath, req.headers, out, version);
            } else if ("POST".equals(method)) {
                routePost(rawPath, req.headers, body, out, version);
            } else {
                writeError(out, 405, "Method Not Allowed");
            }

        } catch (java.net.SocketTimeoutException ste) {
            try {
                OutputStream out = clientSocket.getOutputStream();
                writeError(out, 408, "Request Timeout");
            } catch (IOException ignore) { }
        } catch (Exception ex) {
            try {
                OutputStream out = clientSocket.getOutputStream();
                writeError(out, 500, "Internal Server Error");
            } catch (IOException ignore) { }
        }
    }

    private static final byte CR = 13;
    private static final byte LF = 10;

    private record Request(String requestLine, Map<String, String> headers) {}
    private record HeadPart(byte[] head, byte[] leftover) {}

    private Request parseHead(byte[] head) {
        String all = new String(head, StandardCharsets.UTF_8);
        String[] lines = all.split("\r\n");
        if (lines.length == 0) return null;

        String requestLine = lines[0];
        Map<String, String> headers = new LinkedHashMap<>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.isEmpty()) continue;
            int idx = line.indexOf(':');
            if (idx > 0) {
                String h = line.substring(0, idx).trim().toLowerCase(Locale.ROOT);
                String v = line.substring(idx + 1).trim();
                headers.put(h, v);
            }
        }
        return new Request(requestLine, headers);
    }

    private HeadPart readUntilDoubleCRLF(BufferedInputStream bin, int maxBytes) throws IOException {
        byte[] buf = new byte[Math.min(8192, maxBytes)];
        byte[] acc = new byte[0];
        int matched = 0;

        while (acc.length < maxBytes) {
            int r = bin.read(buf);
            if (r == -1) return null;
            for (int i = 0; i < r; i++) {
                byte b = buf[i];
                switch (matched) {
                    case 0 -> matched = (b == CR) ? 1 : 0;
                    case 1 -> matched = (b == LF) ? 2 : (b == CR ? 1 : 0);
                    case 2 -> matched = (b == CR) ? 3 : 0;
                    case 3 -> {
                        if (b == LF) {
                            int headLenInThisBuf = i + 1 - 4; // без CRLFCRLF
                            int totalHeadLen = acc.length + Math.max(0, headLenInThisBuf);
                            byte[] head = new byte[totalHeadLen];
                            System.arraycopy(acc, 0, head, 0, acc.length);
                            if (headLenInThisBuf > 0) {
                                System.arraycopy(buf, 0, head, acc.length, headLenInThisBuf);
                            }
                            int leftoverLen = r - (i + 1);
                            byte[] leftover = new byte[leftoverLen];
                            if (leftoverLen > 0) {
                                System.arraycopy(buf, i + 1, leftover, 0, leftoverLen);
                            }
                            return new HeadPart(head, leftover);
                        } else {
                            matched = 0;
                        }
                    }
                }
            }
            int oldLen = acc.length;
            acc = java.util.Arrays.copyOf(acc, oldLen + r);
            System.arraycopy(buf, 0, acc, oldLen, r);
        }
        return null;
    }

    private byte[] readFixedBytes(BufferedInputStream bin, byte[] leftover, int length) throws IOException {
        byte[] data = new byte[length];
        int off = 0;

        if (leftover != null && leftover.length > 0) {
            int copy = Math.min(length, leftover.length);
            System.arraycopy(leftover, 0, data, 0, copy);
            off += copy;
        }

        while (off < length) {
            int r = bin.read(data, off, length - off);
            if (r == -1) break;
            off += r;
        }
        if (off == length) return data;
        return java.util.Arrays.copyOf(data, off);
    }

    private void routeGet(String rawPath, Map<String, String> headers, OutputStream out, String version) throws IOException {
        String path = decodePath(rawPath);

        if ("/".equals(path)) {
            Path file = staticRoot.resolve("index.html").normalize();
            if (!isInStaticRoot(file) || !Files.exists(file)) {
                writeError(out, 404, "Not Found");
                return;
            }
            serveFile(out, version, file);
            return;
        }

        if (path.startsWith("/static/")) {
            String rel = path.substring("/static/".length());
            Path file = staticRoot.resolve(rel).normalize();
            if (!isInStaticRoot(file) || !Files.exists(file) || Files.isDirectory(file)) {
                writeError(out, 404, "Not Found");
                return;
            }
            serveFile(out, version, file);
            return;
        }

        if ("/api/time".equals(path)) {
            String json = "{\"time\":\"" + ZonedDateTime.now().toString() + "\"}";
            writeJson(out, version, 200, "OK", json);
            return;
        }

        if ("/api/stats".equals(path)) {
            long uptimeMs = Duration.between(startedAt, Instant.now()).toMillis();
            String json = "{"
                    + "\"requests\":" + totalRequests.get() + ","
                    + "\"uptimeMs\":" + uptimeMs + ","
                    + "\"startedAt\":\"" + startedAt + "\""
                    + "}";
            writeJson(out, version, 200, "OK", json);
            return;
        }

        writeError(out, 404, "Not Found");
    }

    private void routePost(String rawPath, Map<String, String> headers, byte[] body, OutputStream out, String version) throws IOException {
        String path = decodePath(rawPath);

        if ("/api/echo".equals(path)) {
            String contentType = headers.getOrDefault("content-type", "application/octet-stream");
            writeBytes(out, version, 200, "OK", contentType, body);
            return;
        }

        writeError(out, 404, "Not Found");
    }

    private void serveFile(OutputStream out, String version, Path file) throws IOException {
        String contentType = probeContentType(file);
        long length = Files.size(file);

        writeStatusLine(out, version, 200, "OK");
        writeCommonHeaders(out);
        writeHeader(out, "Content-Type", contentType);
        writeHeader(out, "Content-Length", String.valueOf(length));
        writeHeader(out, "Connection", "close");
        writeCRLF(out);

        try (InputStream fis = Files.newInputStream(file)) {
            fis.transferTo(out);
        }
        out.flush();
    }

    private boolean isInStaticRoot(Path file) {
        try {
            return file.toRealPath().startsWith(staticRoot.toRealPath());
        } catch (IOException e) {
            return false;
        }
    }

    private String decodePath(String raw) {
        try {
            int q = raw.indexOf('?');
            String p = (q >= 0) ? raw.substring(0, q) : raw;
            return URLDecoder.decode(p, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return raw;
        }
    }

    private String probeContentType(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".html") || name.endsWith(".htm")) return "text/html; charset=UTF-8";
        if (name.endsWith(".css")) return "text/css; charset=UTF-8";
        if (name.endsWith(".js")) return "application/javascript; charset=UTF-8";
        if (name.endsWith(".json")) return "application/json; charset=UTF-8";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".gif")) return "image/gif";
        if (name.endsWith(".ico")) return "image/x-icon";
        return "application/octet-stream";
    }

    private int parseIntSafe(String s, int def) {
        if (s == null) return def;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return def; }
    }

    private void writeJson(OutputStream out, String version, int code, String reason, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        writeBytes(out, version, code, reason, "application/json; charset=UTF-8", bytes);
    }

    private void writeBytes(OutputStream out, String version, int code, String reason, String contentType, byte[] body) throws IOException {
        writeStatusLine(out, version, code, reason);
        writeCommonHeaders(out);
        writeHeader(out, "Content-Type", contentType);
        writeHeader(out, "Content-Length", String.valueOf(body.length));
        writeHeader(out, "Connection", "close");
        writeCRLF(out);
        out.write(body);
        out.flush();
    }

    private void writeError(OutputStream out, int code, String reason) throws IOException {
        String body = "<html><head><title>" + code + " " + reason + "</title></head>"
                + "<body><h1>" + code + " " + reason + "</h1></body></html>";
        writeBytes(out, "HTTP/1.1", code, reason, "text/html; charset=UTF-8", body.getBytes(StandardCharsets.UTF_8));
    }

    private void writeStatusLine(OutputStream out, String version, int code, String reason) throws IOException {
        String v = (version == null || version.isBlank()) ? "HTTP/1.1" : version;
        out.write((v + " " + code + " " + reason + "\r\n").getBytes(StandardCharsets.UTF_8));
    }

    private void writeCommonHeaders(OutputStream out) throws IOException {
        writeHeader(out, "Date", DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now()));
        writeHeader(out, "Server", "CustomWebServer/1.0");
    }

    private void writeHeader(OutputStream out, String name, String value) throws IOException {
        out.write((name + ": " + value + "\r\n").getBytes(StandardCharsets.UTF_8));
    }

    private void writeCRLF(OutputStream out) throws IOException {
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }
}
