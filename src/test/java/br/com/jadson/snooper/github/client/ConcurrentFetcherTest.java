package br.com.jadson.snooper.github.client;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

class ConcurrentFetcherTest {

    @Test
    void keepsTheOrderOfTheTasks() {
        ConcurrentFetcher fetcher = new ConcurrentFetcher(4);

        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            int value = i;
            // later tasks finish first
            tasks.add(() -> { Thread.sleep(40 - 2L * value); return value; });
        }

        List<Integer> results = fetcher.fetchAll(tasks);

        for (int i = 0; i < 20; i++)
            Assertions.assertEquals(i, results.get(i));
    }

    @Test
    void emptyTasks() {
        Assertions.assertTrue(new ConcurrentFetcher().fetchAll(List.<Callable<String>>of()).isEmpty());
    }

    @Test
    void requestsNeverExceedTheLimit() {
        ConcurrentFetcher fetcher = new ConcurrentFetcher(3);
        PeakCounter counter = new PeakCounter();

        fetcher.fetchAll(requests(fetcher, counter, 20, 30));

        Assertions.assertTrue(counter.peak() <= 3, "peak was " + counter.peak());
        Assertions.assertTrue(counter.peak() >= 2, "requests did not run in parallel");
    }

    @Test
    void changesTheLimitAtRuntime() {
        ConcurrentFetcher fetcher = new ConcurrentFetcher(5);

        fetcher.setMaxConcurrency(2);
        PeakCounter lower = new PeakCounter();
        fetcher.fetchAll(requests(fetcher, lower, 12, 30));
        Assertions.assertTrue(lower.peak() <= 2, "peak was " + lower.peak());

        fetcher.setMaxConcurrency(6);
        PeakCounter higher = new PeakCounter();
        fetcher.fetchAll(requests(fetcher, higher, 30, 100));
        Assertions.assertTrue(higher.peak() <= 6, "peak was " + higher.peak());
        Assertions.assertTrue(higher.peak() > 2, "limit was not raised, peak was " + higher.peak());

        Assertions.assertThrows(IllegalArgumentException.class, () -> fetcher.setMaxConcurrency(0));
    }

    @Test
    void failsFastWithTheOriginalException() {
        ConcurrentFetcher fetcher = new ConcurrentFetcher(10);
        AtomicBoolean slowTaskInterrupted = new AtomicBoolean(false);

        List<Callable<String>> tasks = List.of(
                () -> {
                    try {
                        Thread.sleep(10_000);
                    } catch (InterruptedException e) {
                        slowTaskInterrupted.set(true);
                        throw e;
                    }
                    return "slow";
                },
                () -> { throw new IllegalArgumentException("page 2 failed"); }
        );

        IllegalArgumentException error = Assertions.assertTimeoutPreemptively(Duration.ofSeconds(5),
                () -> Assertions.assertThrows(IllegalArgumentException.class, () -> fetcher.fetchAll(tasks)));

        Assertions.assertEquals("page 2 failed", error.getMessage());
        Assertions.assertTrue(slowTaskInterrupted.get(), "the other tasks should be cancelled");
    }

    @Test
    void wrapsCheckedExceptions() {
        ConcurrentFetcher fetcher = new ConcurrentFetcher();

        IllegalStateException error = Assertions.assertThrows(IllegalStateException.class,
                () -> fetcher.fetchAll(List.<Callable<String>>of(() -> { throw new IOException("network"); })));

        Assertions.assertInstanceOf(IOException.class, error.getCause());
    }

    // Tasks that make one "request" each through withPermit, like the interceptor does
    private static List<Callable<Integer>> requests(ConcurrentFetcher fetcher, PeakCounter counter, int count, long millis) {
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            tasks.add(() -> fetcher.withPermit(() -> {
                counter.enter();
                try {
                    Thread.sleep(millis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    counter.exit();
                }
                return 0;
            }));
        }
        return tasks;
    }

    private static final class PeakCounter {
        private final AtomicInteger current = new AtomicInteger();
        private final AtomicInteger peak = new AtomicInteger();

        void enter() { peak.accumulateAndGet(current.incrementAndGet(), Math::max); }
        void exit() { current.decrementAndGet(); }
        int peak() { return peak.get(); }
    }
}
