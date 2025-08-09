package work.multithreaded;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class DeadlocksDemo {

    static void main(String[] args) throws Exception {

        //deadlockIntrinsicLocks();
        // deadlockReadWriteLockUpgrade();
        // deadlockJoinCycle();
    }

    // 1) Взаимная блокировка на двух intrinsic-замках с разным порядком захвата
    // Почему дедлок:
    // - Поток A берет lock1, затем пытается взять lock2.
    // - Поток B берет lock2, затем пытается взять lock1.
    // - Каждый держит свой первый замок и навсегда ждёт второй — образуется цикл ожидания (circular wait).
    public static void deadlockIntrinsicLocks() throws InterruptedException {
        System.out.println("Demo #1: intrinsic synchronized, обратный порядок захвата");

        final Object lock1 = new Object();
        final Object lock2 = new Object();
        CountDownLatch bothStarted = new CountDownLatch(2);

        Thread t1 = Thread.ofVirtual().start(() -> {
            synchronized (lock1) {
                bothStarted.countDown();
                sleep(100);
                System.out.println("T1: держу lock1, жду lock2...");
                synchronized (lock2) {
                    System.out.println("T1: никогда сюда не дойдёт");
                }
            }
        });

        Thread t2 = Thread.ofVirtual().start(() -> {
            synchronized (lock2) {
                bothStarted.countDown();
                sleep(100);
                System.out.println("T2: держу lock2, жду lock1...");
                synchronized (lock1) {
                    System.out.println("T2: никогда сюда не дойдёт");
                }
            }
        });

        bothStarted.await();
        System.out.println("Оба потока захватили первый замок. Дедлок сформирован.");
        t1.join(); // никогда не завершится
        t2.join();
    }

    // 2) Deadlock при «апгрейде» ReentrantReadWriteLock: два читателя пытаются стать писателями
    // Почему дедлок:
    // - RWLock не поддерживает «апгрейд» (read -> write) без освобождения read-lock.
    // - Потоки T1 и T2 берут readLock и затем пытаются взять writeLock.
    // - WriteLock не может быть получен, пока существуют читатели, но оба читателя не отпускают readLock,
    //   т.к. ждут writeLock. Образуется взаимная блокировка.
    public static void deadlockReadWriteLockUpgrade() throws InterruptedException {
        System.out.println("Demo #2: ReentrantReadWriteLock upgrade (read -> write)");

        ReentrantReadWriteLock rw = new ReentrantReadWriteLock(true);
        var read = rw.readLock();
        var write = rw.writeLock();
        CountDownLatch bothReading = new CountDownLatch(2);

        Runnable readerToWriter = () -> {
            read.lock();
            try {
                System.out.println(Thread.currentThread().getName() + ": держу readLock, пытаюсь writeLock...");
                bothReading.countDown();
                // Ждём пока оба станут читателями, чтобы гарантировать дедлок
                sleep(100);
                write.lock(); // тут зависнем, т.к. другой поток тоже держит readLock
                try {
                    System.out.println("Не дойдём: writeLock получен");
                } finally {
                    write.unlock();
                }
            } finally {
                read.unlock();
            }
        };

        Thread t1 = Thread.ofVirtual().start(readerToWriter);
        Thread t2 = Thread.ofVirtual().start(readerToWriter);

        bothReading.await();
        System.out.println("Оба потока держат readLock и ждут writeLock. Дедлок сформирован.");
        t1.join();
        t2.join();
    }

    // 3) Взаимная блокировка через Thread.join()
    // Почему дедлок:
    // - T1 вызывает t2.join() (ждёт завершения T2).
    // - T2 вызывает t1.join() (ждёт завершения T1).
    // - Оба ждут друг друга и никогда не завершатся.
    public static void deadlockJoinCycle() throws InterruptedException {
        System.out.println("Demo #3: взаимный join() двух потоков");

        final Holder holder = new Holder();
        CountDownLatch ready = new CountDownLatch(2);

        Thread t1 = Thread.ofVirtual().unstarted(() -> {
            ready.countDown();
            await(ready);
            System.out.println("T1: жду T2.join()");
            try {
                holder.t2.join(); // ждём T2
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            System.out.println("T1: не дойдём");
        });

        Thread t2 = Thread.ofVirtual().unstarted(() -> {
            ready.countDown();
            await(ready);
            System.out.println("T2: жду T1.join()");
            try {
                holder.t1.join(); // ждём T1
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            System.out.println("T2: не дойдём");
        });

        holder.t1 = t1;
        holder.t2 = t2;

        t1.start();
        t2.start();

        t1.join(); // никогда не завершится
        t2.join();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException ignored) {
        }
    }

    private static class Holder {
        Thread t1;
        Thread t2;
    }
}

