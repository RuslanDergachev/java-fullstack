package work.multithreaded.webserver;

import work.multithreaded.service.CustomExecutorService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
             OutputStream out = clientSocket.getOutputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {

            String requestLine = reader.readLine();
            if (requestLine == null || requestLine.isEmpty()) {
                writeError(out, 400, "Bad Request");
                return;
            }

            String[] parts = requestLine.split("\\s+");
            if (parts.length < 3) {
                writeError(out, 400, "Bad Request");
                return;
            }
            String method = parts[0].toUpperCase(Locale.ROOT);
            String rawPath = parts[1];
            String version = parts[2];

            Map<String, String> headers = new LinkedHashMap<>();
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                int idx = line.indexOf(':');
                if (idx > 0) {
                    String h = line.substring(0, idx).trim();
                    String v = line.substring(idx + 1).trim();
                    headers.put(h.toLowerCase(Locale.ROOT), v);
                }
            }

            byte[] body = new byte[0];
            if ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method)) {
                int contentLength = parseIntSafe(headers.get("content-length"), 0);
                if (contentLength > 0) {
                    body = in.readNBytes(contentLength);
                }
            }

            totalRequests.incrementAndGet();

            if ("GET".equals(method)) {
                routeGet(rawPath, headers, out, version);
            } else if ("POST".equals(method)) {
                routePost(rawPath, headers, body, out, version);
            } else {
                writeError(out, 405, "Method Not Allowed");
            }

        } catch (Exception ex) {
            try {
                OutputStream out = clientSocket.getOutputStream();
                writeError(out, 500, "Internal Server Error");
            } catch (IOException ignore) { }
        }
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
        out.write((v + " " + code + " " + reason + "\r\n").getBytes(StandardCharsets.UTF_8)); // UTF-8
    }

    private void writeCommonHeaders(OutputStream out) throws IOException {
        writeHeader(out, "Date", DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now()));
        writeHeader(out, "Server", "CustomWebServer/1.0");
    }

    private void writeHeader(OutputStream out, String name, String value) throws IOException {
        out.write((name + ": " + value + "\r\n").getBytes(StandardCharsets.UTF_8)); // UTF-8
    }

    private void writeCRLF(OutputStream out) throws IOException {
        out.write("\r\n".getBytes(StandardCharsets.UTF_8)); // UTF-8
    }
}
