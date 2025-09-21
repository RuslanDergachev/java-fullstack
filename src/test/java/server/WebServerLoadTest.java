package server;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.LongAdder;

public class WebServerLoadTest {

    static void main(String[] args) throws Exception {
        String urlVirtual = "http://localhost:8080/api/time";
        String urlPlatform = "http://localhost:8081/api/time";
        int concurrency = 200;      // одновременных запросов
        int durationSec = 10;       // длительность теста
        int timeoutSec = 5;        // таймаут запросов
        boolean warmup = true;     // прогрев перед замером

        System.out.printf(Locale.ROOT, "Load test: concurrency=%d, duration=%ds, timeout=%ds%n%n", concurrency,
                          durationSec, timeoutSec);

        if (warmup) {
            System.out.println("Warm-up both servers...");
            runScenario(urlVirtual, 50, 2, timeoutSec);
            runScenario(urlPlatform, 50, 2, timeoutSec);
            System.out.println("Warm-up done.\n");
        }

        Result rVirt = runScenario(urlVirtual, concurrency, durationSec, timeoutSec);
        Result rPlat = runScenario(urlPlatform, concurrency, durationSec, timeoutSec);

        System.out.println("\n==== Summary ====");
        printResult("Virtual threads server", rVirt);
        printResult("Platform threads server", rPlat);

        System.out.println("\n==== Comparison ====");
        System.out.printf(Locale.ROOT, "Throughput: virtual=%.1f rps, platform=%.1f rps, ratio=%.2f%n", rVirt.rps(),
                          rPlat.rps(), safeDiv(rVirt.rps(), rPlat.rps()));
        System.out.printf(Locale.ROOT, "Avg latency: virtual=%.2f ms, platform=%.2f ms, ratio=%.2f%n", rVirt.avgMs(),
                          rPlat.avgMs(), safeDiv(rVirt.avgMs(), rPlat.avgMs()));
        System.out.printf(Locale.ROOT, "P95 latency: virtual=%d ms, platform=%d ms%n", rVirt.p95(), rPlat.p95());
        System.out.printf(Locale.ROOT, "P99 latency: virtual=%d ms, platform=%d ms%n", rVirt.p99(), rPlat.p99());
    }

    private static double safeDiv(double a, double b) {
        return b == 0 ? Double.NaN : a / b;
    }

    // Один прогон: шлём запросы заданной конкуренции в течение durationSec
    private static Result runScenario(String url, int concurrency, int durationSec, int timeoutSec) throws Exception {
        System.out.printf(Locale.ROOT, "Target: %s%n", url);

        // Клиент HTTP/1.1, keep-alive по умолчанию
        ExecutorService clientExec = Executors.newFixedThreadPool(concurrency);
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSec))
                .version(HttpClient.Version.HTTP_1_1)
                .executor(clientExec)
                .build();

        // Общая остановка по времени
        Instant stopAt = Instant.now().plusSeconds(durationSec);

        // Метрики
        List<Long> latenciesMs = Collections.synchronizedList(new ArrayList<>(concurrency * durationSec * 2));
        LongAdder ok = new LongAdder();
        LongAdder fail = new LongAdder();

        // Воркеры, непрерывно отправляющие запросы, пока не истекло время
        ExecutorService workers = Executors.newFixedThreadPool(concurrency);
        CountDownLatch done = new CountDownLatch(concurrency);

        for (int i = 0; i < concurrency; i++) {
            workers.submit(() -> {
                try {
                    while (Instant.now().isBefore(stopAt)) {
                        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                                .timeout(Duration.ofSeconds(timeoutSec))
                                .GET()
                                .build();
                        long t0 = System.nanoTime();
                        try {
                            HttpResponse<Void> resp = client.send(req, HttpResponse.BodyHandlers.discarding());
                            long tMs = (System.nanoTime() - t0) / 1_000_000;
                            if (resp.statusCode() == 200) {
                                latenciesMs.add(tMs);
                                ok.increment();
                            } else {
                                fail.increment();
                            }
                        } catch (Exception ex) {
                            fail.increment();
                        }
                    }
                } finally {
                    done.countDown();
                }
            });
        }

        done.await();
        workers.shutdown();
        clientExec.shutdown();

        Result result = Result.of(url, ok.sum(), fail.sum(), latenciesMs, durationSec);
        printResult("Result", result);
        System.out.println();
        return result;
    }

    private static void printResult(String caption, Result r) {
        System.out.printf(Locale.ROOT, "%s for %s%n", caption, r.target());
        System.out.printf(Locale.ROOT, "  OK=%d, Fail=%d, RPS=%.1f%n", r.ok(), r.fail(), r.rps());
        System.out.printf(Locale.ROOT, "  Avg=%.2f ms, P50=%d ms, P95=%d ms, P99=%d ms, Max=%d ms%n", r.avgMs(),
                          r.p50(), r.p95(), r.p99(), r.max());
    }

    // Результат замера + вычисление перцентилей
    private record Result(String target, long ok, long fail, double avgMs, long p50, long p95, long p99, long max,
                          double rps) {
        static Result of(String target, long ok, long fail, List<Long> latenciesMs, int durationSec) {
            Objects.requireNonNull(latenciesMs);
            int n = latenciesMs.size();
            double avg = 0.0;
            long max = 0;
            if (n > 0) {
                long sum = 0;
                for (Long v : latenciesMs) {
                    if (v == null)
                        continue;
                    sum += v;
                    if (v > max)
                        max = v;
                }
                avg = sum * 1.0 / n;
            }
            // перцентильные значения
            long p50 = percentile(latenciesMs, 0.50);
            long p95 = percentile(latenciesMs, 0.95);
            long p99 = percentile(latenciesMs, 0.99);
            double rps = durationSec <= 0 ? 0.0 : ok * 1.0 / durationSec;
            return new Result(target, ok, fail, avg, p50, p95, p99, max, rps);
        }

        static long percentile(List<Long> data, double q) {
            if (data.isEmpty())
                return 0;
            List<Long> copy = new ArrayList<>(data);
            copy.sort(Long::compareTo);
            int idx = (int) Math.ceil(q * copy.size()) - 1;
            if (idx < 0)
                idx = 0;
            if (idx >= copy.size())
                idx = copy.size() - 1;
            return copy.get(idx);
        }
    }
}
