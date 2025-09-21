package work;

import work.multithreaded.LargeShortArray;
import work.multithreaded.service.CustomExecutorService;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {
    static void main(String[] args) {

        int size = 100_000_000;
        short[] data = new short[size];
        for (int i = 0; i < size; i++) {
            data[i] = (short) (i % Short.MAX_VALUE);
        }

        LargeShortArray psa = new LargeShortArray(data);

        int[] threadCounts = {1, 10, 100, 1000};
        String outputFile = "src/test/java/results.txt";

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
            writer.write("ThreadCount,Method,TimeMs\n");

            for (int threads : threadCounts) {
                long start = System.nanoTime();
                long sumManual = psa.sumWithParallelThreads(threads);
                long elapsedManualMs = (System.nanoTime() - start) / 1_000_000;
                writer.write(String.format("%d,sumWithParallelThreads,%d%n", threads, elapsedManualMs));
                System.out.printf("Threads=%d parralel threads sum=%,d time=%dms%n", threads, sumManual, elapsedManualMs);

                start = System.nanoTime();
                long sumParallel = psa.sumWithParallelStream(threads);
                long elapsedParallelMs = (System.nanoTime() - start) / 1_000_000;
                writer.write(String.format("%d,sumWithParallelStream,%d%n", threads, elapsedParallelMs));
                System.out.printf("Threads=%d parallel stream sum=%,d time=%dms%n", threads, sumParallel, elapsedParallelMs);
            }

            System.out.println("Results written to " + outputFile);

        } catch (IOException ioe) {
            System.err.println("Error writing results file: " + ioe.getMessage());
        }

    }

    // Test 1: Performance comparison
    // Сравниваем фиксированный пул платформенных потоков и режим "виртуальный поток на каждую задачу"
    private static void performanceTest() {
        System.out.println("=== Performance Test ===");
        final int tasks = 10_000;
        final int sleepMs = 10;
        int[] poolSizes = {10, 50, 100, 500};

        for (int poolSize : poolSizes) {
            System.out.println("-- Pool size = " + poolSize + " --");

            // Платформенные потоки
            runPerfCase("Platform", new CustomExecutorService(poolSize, false), tasks, sleepMs);

            // Виртуальные потоки (пул рабочих виртуальных потоков)
            runPerfCase("Virtual", new CustomExecutorService(poolSize, true), tasks, sleepMs);

            System.out.println();
        }
    }

    private static void runPerfCase(String kind, CustomExecutorService executor, int tasks, int sleepMs) {
        try {
            // Минимизируем шум
            runGC();

            long beforeMem = usedMemory();
            Instant start = Instant.now();

            List<Future<?>> futures = new ArrayList<>(tasks);
            for (int i = 0; i < tasks; i++) {
                futures.add(executor.submit(() -> {
                    try {
                        Thread.sleep(sleepMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }));
            }

            // Дождаться завершения всех задач
            for (Future<?> f : futures) {
                try {
                    f.get(60, TimeUnit.SECONDS);
                } catch (ExecutionException | TimeoutException e) {
                    e.printStackTrace();
                }
            }

            Instant end = Instant.now();
            long afterMem = usedMemory();

            long elapsedMs = Duration.between(start, end).toMillis();
            long memUsedBytes = Math.max(0, afterMem - beforeMem);
            double memUsedMB = memUsedBytes / (1024.0 * 1024.0);

            System.out.printf("%s: time=%d ms, approx memory used=%.2f MB%n", kind, elapsedMs, memUsedMB);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            executor.shutdown();
            try {
                executor.awaitTermination(2, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void concurrentExecutionTest() {
        System.out.println("=== Concurrent Execution Test ===");
        final int taskCount = 1_000;

        // Платформенные потоки
        runConcurrentCase("Platform", new CustomExecutorService(8, false), taskCount);

        // Виртуальные потоки (пул рабочих виртуальных потоков)
        runConcurrentCase("Virtual", new CustomExecutorService(8, true), taskCount);

        System.out.println();
    }

    private static void runConcurrentCase(String kind, CustomExecutorService executor, int taskCount) {
        AtomicInteger counter = new AtomicInteger(0);
        List<Future<?>> futures = new ArrayList<>(taskCount);

        for (int i = 0; i < taskCount; i++) {
            futures.add(executor.submit(counter::incrementAndGet));
        }

        boolean ok = true;
        for (Future<?> f : futures) {
            try {
                f.get(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                ok = false;
                break;
            } catch (ExecutionException | TimeoutException e) {
                e.printStackTrace();
                ok = false;
                break;
            }
        }

        executor.shutdown();
        try {
            if (!executor.awaitTermination(1, TimeUnit.MINUTES)) {
                System.out.println("WARN: " + kind + " executor did not terminate in time");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        int value = counter.get();
        System.out.printf("%s: completed=%s, counter=%d, expected=%d%n", kind, ok, value, taskCount);
    }

    private static void runGC() throws InterruptedException {
        System.gc();
        Thread.sleep(200);
    }

    private static long usedMemory() {
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }

    // Test 3: Shutdown behavior
    // - shutdown(): блокирует приём новых задач, ждём завершения текущих
    // - shutdownNow(): возвращает невыполненные задачи, пытается прервать работающие
    private static void testShutdownBehavior() {
        System.out.println("=== Test 3: Shutdown behavior ===");

        // A) shutdown — завершаем текущие задачи, запрет новых
        {
            CustomExecutorService exec = new CustomExecutorService(2, false);
            List<Future<Integer>> futures = new ArrayList<>();

            // Несколько длинных задач
            for (int i = 0; i < 4; i++) {
                futures.add(exec.submit(() -> {
                    try {
                        Thread.sleep(300);
                    } catch (InterruptedException e) {
                        // При обычном shutdown не ожидаем прерываний
                        Thread.currentThread().interrupt();
                    }
                    return 1;
                }));
            }

            // Инициируем корректное завершение
            exec.shutdown();

            // Попытка добавить новую задачу — должно кидать RejectedExecutionException
            boolean rejected = false;
            try {
                exec.submit(() -> 42);
            } catch (RejectedExecutionException ree) {
                rejected = true;
            }

            // Дождёмся завершения
            boolean terminated = false;
            try {
                terminated = exec.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            int completed = 0;
            for (Future<Integer> f : futures) {
                try {
                    if (f.get() == 1)
                        completed++;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            System.out.printf("Shutdown: rejectedNewTasks=%s, completed=%d, terminated=%s%n", rejected, completed,
                              terminated);
        }

        // B) shutdownNow — немедленное завершение: часть задач может не стартовать, очередь возвращается
        {
            CustomExecutorService exec = new CustomExecutorService(2, false);

            // Отправим много задач, часть из них будет в очереди
            for (int i = 0; i < 50; i++) {
                exec.execute(() -> {
                    try {
                        // Блокируемся подольше, чтобы гарантировать наличие очереди
                        Thread.sleep(2_000);
                    } catch (InterruptedException e) {
                        // Ожидаем прерывание при shutdownNow
                        // Выходим из задачи
                        Thread.currentThread().interrupt();
                    }
                });
            }

            // Немедленно останавливаем
            List<Runnable> notExecuted = exec.shutdownNow();

            // Ждём завершения
            boolean terminated = false;
            try {
                terminated = exec.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            System.out.printf("ShutdownNow: notExecuted=%d, terminated=%s%n", notExecuted.size(), terminated);
        }


        // C) Режим виртуальных потоков на задачу: корректная остановка
        {
            CustomExecutorService vt = CustomExecutorService.newVirtualThreadPerTaskExecutor();
            List<Future<Integer>> futs = new ArrayList<>();
            for (int i = 0; i < 10_000; i++) {
                futs.add(vt.submit(() -> {
                    // Короткая работа
                    int x = 0;
                    for (int k = 0; k < 500; k++)
                        x += (k ^ 13);
                    return x;
                }));
            }

            vt.shutdown();

            // Новая задача — должна быть отклонена
            boolean rejected = false;
            try {
                vt.submit(() -> 7);
            } catch (RejectedExecutionException ree) {
                rejected = true;
            }

            // Дождёмся завершения
            boolean terminated = false;
            try {
                terminated = vt.awaitTermination(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            int done = 0;
            for (Future<Integer> f : futs) {
                try {
                    f.get(10, TimeUnit.SECONDS);
                    done++;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            System.out.printf("Virtual per task: rejectedNewTasks=%s, completed=%d, terminated=%s%n", rejected, done,
                              terminated);
        }
        System.out.println();
    }
}

