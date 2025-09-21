package work.multithreaded.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class CustomExecutorService implements ExecutorService {
    private final int poolSize;
    private final boolean useVirtualThreads;

    private final BlockingQueue<Runnable> workQueue;

    private final List<Thread> workers;

    private final boolean perTaskVirtualMode;
    private final ConcurrentLinkedQueue<Thread> spawnedPerTaskThreads;

    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    private final AtomicBoolean terminated = new AtomicBoolean(false);
    private final AtomicInteger runningWorkers = new AtomicInteger(0);

    /**
     * Основной конструктор пула.
     * @param corePoolSize количество рабочих потоков
     * @param useVirtualThreads если true — рабочие потоки будут виртуальными, иначе платформенными
     */
    public CustomExecutorService(int corePoolSize, boolean useVirtualThreads) {
        if (corePoolSize < 0) {
            throw new IllegalArgumentException("corePoolSize must be >= 0");
        }
        this.poolSize = corePoolSize;
        this.useVirtualThreads = useVirtualThreads;

        // per-task режим отключён в этом конструкторе
        this.perTaskVirtualMode = false;
        this.spawnedPerTaskThreads = null;

        this.workQueue = new LinkedBlockingQueue<>();
        this.workers = new ArrayList<>(corePoolSize);

        // Если размер пула > 0 — создаём и запускаем рабочих
        if (corePoolSize > 0) {
            for (int i = 0; i < corePoolSize; i++) {
                Thread worker = newWorker(this::workerLoop, i);
                workers.add(worker);
                worker.start();
            }
        } else {
            // Если размер = 0 — фактически пул не стартуем, executor сможет принимать задачи,
            // но они не будут выполняться (в этом варианте рекомендуется использовать фабричный метод для per-task).
        }
    }

    /**
     * Фабричный метод: виртуальный поток на каждую задачу, без пула.
     */
    public static CustomExecutorService newVirtualThreadPerTaskExecutor() {
        return new CustomExecutorService(true);
    }

    // Приватный конструктор для per-task virtual режима
    private CustomExecutorService(boolean perTaskVirtualMode) {
        this.poolSize = 0;
        this.useVirtualThreads = true;
        this.perTaskVirtualMode = perTaskVirtualMode; // true
        this.spawnedPerTaskThreads = new ConcurrentLinkedQueue<>();
        this.workQueue = null;
        this.workers = Collections.emptyList();
    }

    private Thread newWorker(Runnable target, int index) {
        String name = "custom-exec-worker-" + index;
        if (useVirtualThreads) {
            return Thread.ofVirtual().name(name).unstarted(target);
        } else {
            return Thread.ofPlatform().name(name).unstarted(target);
        }
    }

    private Thread newThreadPerTask(Runnable target) {
        // Для режима per-task — всегда виртуальный поток
        return Thread.ofVirtual().name("custom-exec-vt-task").unstarted(target);
    }

    private void workerLoop() {
        runningWorkers.incrementAndGet();
        try {
            while (true) {
                if (shutdown.get() && workQueue.isEmpty()) {
                    break;
                }
                try {
                    Runnable task = workQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (task != null) {
                        task.run();
                    }
                } catch (InterruptedException ie) {
                    // Если прервали во время shutdownNow — выходим, если очередь пуста
                    if (shutdown.get() && workQueue.isEmpty()) {
                        break;
                    }
                    // иначе продолжаем
                } catch (Throwable t) {
                    // Проглатываем исключение из задачи
                }
            }
        } finally {
            int left = runningWorkers.decrementAndGet();
            // Если включён shutdown и все рабочие завершились — помечаем terminated
            if (shutdown.get() && left == 0) {
                terminated.set(true);
            }
        }
    }

    @Override
    public void execute(Runnable command) {
        Objects.requireNonNull(command, "command");
        if (shutdown.get()) {
            throw new RejectedExecutionException("Executor is shutdown");
        }

        if (perTaskVirtualMode) {
            // Режим: виртуальный поток на каждую задачу
            Thread t = newThreadPerTask(wrapForPerTaskTracking(command));
            spawnedPerTaskThreads.add(t);
            t.start();
        } else {
            // Пул-режим
            if (poolSize <= 0) {
                throw new RejectedExecutionException("Pool size is 0. Use newVirtualThreadPerTaskExecutor() for per-task mode.");
            }
            if (!workQueue.offer(command)) {
                throw new RejectedExecutionException("Task queue is full");
            }
        }
    }

    private Runnable wrapForPerTaskTracking(Runnable command) {
        return () -> {
            try {
                command.run();
            } finally {
                // Поток завершится сам, в awaitTermination мы просто дождёмся завершения
            }
        };
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        Objects.requireNonNull(task, "task");
        if (shutdown.get()) {
            throw new RejectedExecutionException("Executor is shutdown");
        }
        FutureTask<T> ft = new FutureTask<>(task);
        dispatchFutureTask(ft);
        return ft;
    }

    @Override
    public Future<?> submit(Runnable task) {
        Objects.requireNonNull(task, "task");
        if (shutdown.get()) {
            throw new RejectedExecutionException("Executor is shutdown");
        }
        FutureTask<Void> ft = new FutureTask<>(task, null);
        dispatchFutureTask(ft);
        return ft;
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        Objects.requireNonNull(task, "task");
        if (shutdown.get()) {
            throw new RejectedExecutionException("Executor is shutdown");
        }
        FutureTask<T> ft = new FutureTask<>(task, result);
        dispatchFutureTask(ft);
        return ft;
    }

    private void dispatchFutureTask(FutureTask<?> ft) {
        if (perTaskVirtualMode) {
            Thread t = newThreadPerTask(() -> {
                try {
                    ft.run();
                } finally {
                    // nothing
                }
            });
            spawnedPerTaskThreads.add(t);
            t.start();
        } else {
            if (poolSize <= 0) {
                throw new RejectedExecutionException("Pool size is 0. Use newVirtualThreadPerTaskExecutor() for per-task mode.");
            }
            if (!workQueue.offer(ft)) {
                throw new RejectedExecutionException("Task queue is full");
            }
        }
    }

    @Override
    public void shutdown() {
        if (shutdown.compareAndSet(false, true)) {
            if (perTaskVirtualMode) {
                terminated.compareAndSet(false, spawnedPerTaskThreads.isEmpty());
            } else {
                // Для пула — если очередь пуста и нет работающих — terminated
                if (workQueue.isEmpty() && runningWorkers.get() == 0) {
                    terminated.set(true);
                }
            }
        }
    }

    @Override
    public List<Runnable> shutdownNow() {
        if (!shutdown.getAndSet(true)) {
            // первый вызов — дополнительно пытаемся прервать
        }
        List<Runnable> notExecuted = new ArrayList<>();
        if (perTaskVirtualMode) {
            // Прерываем живые пер-задачные потоки
            for (Thread t : spawnedPerTaskThreads) {
                if (t != null && t.isAlive()) {
                    t.interrupt();
                }
            }
            terminated.compareAndSet(false, true); // будем считать, что завершимся после остановки задач
        } else {
            // Слить очередь
            workQueue.drainTo(notExecuted);
            // Прервать рабочих
            for (Thread w : workers) {
                if (w != null && w.isAlive()) {
                    w.interrupt();
                }
            }
            // Завершится, когда workerLoop выйдет
        }
        return notExecuted;
    }

    @Override
    public boolean isShutdown() {
        return shutdown.get();
    }

    @Override
    public boolean isTerminated() {
        if (perTaskVirtualMode) {
            if (!shutdown.get()) return false;
            // Завершены ли все уже запущенные потоки
            for (Thread t : spawnedPerTaskThreads) {
                if (t != null && t.isAlive()) {
                    return false;
                }
            }
            return true;
        } else {
            if (!shutdown.get()) return false;
            // Завершены ли все рабочие
            if (runningWorkers.get() != 0) return false;
            // Очередь пуста — все задачи выполнены
            return workQueue.isEmpty();
        }
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        Objects.requireNonNull(unit, "unit");
        long deadline = System.nanoTime() + unit.toNanos(timeout);

        if (perTaskVirtualMode) {
            // Ждём завершения всех spawned потоков
            while (System.nanoTime() < deadline) {
                if (isTerminated()) return true;
                Thread.sleep(5);
            }
            return isTerminated();
        } else {
            // Ждём пока рабочие завершат цикл, очередь опустеет
            while (System.nanoTime() < deadline) {
                if (isTerminated()) return true;
                Thread.sleep(5);
            }
            return isTerminated();
        }
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        Objects.requireNonNull(tasks, "tasks");
        List<Future<T>> futures = new ArrayList<>(tasks.size());
        try {
            for (Callable<T> c : tasks) {
                futures.add(submit(c));
            }
            for (Future<T> f : futures) {
                try {
                    f.get();
                } catch (ExecutionException ignored) {
                    // Спецификация: возвращаем futures; исключения остаются внутри
                }
            }
            return futures;
        } catch (RuntimeException e) {
            for (Future<T> f : futures) {
                f.cancel(true);
            }
            throw e;
        }
    }

    @Override
    public <T> List<Future<T>> invokeAll(
            Collection<? extends Callable<T>> tasks,
            long timeout,
            TimeUnit unit) throws InterruptedException {

        Objects.requireNonNull(tasks, "tasks");
        Objects.requireNonNull(unit, "unit");
        long deadline = System.nanoTime() + unit.toNanos(timeout);

        List<Future<T>> futures = new ArrayList<>(tasks.size());
        try {
            for (Callable<T> c : tasks) {
                futures.add(submit(c));
            }
            for (Future<T> f : futures) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0L) {
                    // Время вышло — отменяем невыполненные
                    for (Future<T> g : futures) {
                        if (!g.isDone()) g.cancel(true);
                    }
                    break;
                }
                try {
                    f.get(remaining, TimeUnit.NANOSECONDS);
                } catch (TimeoutException te) {
                    for (Future<T> g : futures) {
                        if (!g.isDone()) g.cancel(true);
                    }
                    break;
                } catch (ExecutionException ignored) {
                    // продолжаем, спецификация позволяет
                }
            }
            return futures;
        } catch (RuntimeException e) {
            for (Future<T> f : futures) {
                f.cancel(true);
            }
            throw e;
        }
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
            throws InterruptedException, ExecutionException {
        try {
            return doInvokeAny(tasks, 0L, null);
        } catch (TimeoutException te) {
            // не должен возникать без таймаута
            throw new AssertionError(te);
        }
    }

    @Override
    public <T> T invokeAny(
            Collection<? extends Callable<T>> tasks,
            long timeout,
            TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return doInvokeAny(tasks, timeout, unit);
    }

    private <T> T doInvokeAny(Collection<? extends Callable<T>> tasks, Long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {

        Objects.requireNonNull(tasks, "tasks");
        if (tasks.isEmpty()) throw new IllegalArgumentException("tasks is empty");

        long deadline = (unit == null) ? 0L : System.nanoTime() + unit.toNanos(timeout);

        List<Future<T>> futures = new ArrayList<>(tasks.size());
        try {
            for (Callable<T> c : tasks) {
                futures.add(submit(c));
            }

            ExecutionException lastException = null;
            while (true) {
                for (Future<T> f : futures) {
                    if (f.isDone()) {
                        try {
                            T res = f.get();
                            // Отменяем остальных
                            for (Future<T> g : futures) {
                                if (g != f) g.cancel(true);
                            }
                            return res;
                        } catch (ExecutionException ee) {
                            lastException = ee;
                        }
                    }
                }
                if (unit != null) {
                    long remaining = deadline - System.nanoTime();
                    if (remaining <= 0L) {
                        for (Future<T> g : futures) g.cancel(true);
                        throw new TimeoutException("invokeAny timed out");
                    }
                }
                // Если все завершились исключениями — бросаем последнюю
                boolean allDone = true;
                for (Future<T> f : futures) {
                    if (!f.isDone()) {
                        allDone = false;
                        break;
                    }
                }
                if (allDone) {
                    if (lastException != null) throw lastException;
                    throw new ExecutionException(new RuntimeException("No task completed successfully"));
                }

                Thread.sleep(2);
            }
        } finally {
            for (Future<T> f : futures) {
                if (!f.isDone()) f.cancel(true);
            }
        }
    }
}
