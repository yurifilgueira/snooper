package br.com.jadson.snooper.github.client;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

/**
    Runs GitHub requests in parallel on virtual threads.

    There is one instance per token (inside GitHubClients), so the semaphore caps the total
    number of in-flight requests of the token, no matter how many executors or repositories
    are being mined at the same time.

    The permit is held only around each HTTP request (GitHubRateLimitInterceptor calls withPermit),
    not around a whole task. Tasks can then start other parallel fetches (fetchEach -> fetchAllPages)
    without a deadlock: an HTTP request never waits for another request while holding a permit.
 */
public class ConcurrentFetcher {

    @FunctionalInterface
    public interface IOCallable<T> {
        T call() throws IOException;
    }

    public static final int DEFAULT_MAX_CONCURRENCY = 10;

    private final ResizableSemaphore permits;
    private int maxConcurrency;

    public ConcurrentFetcher() {
        this(DEFAULT_MAX_CONCURRENCY);
    }

    public ConcurrentFetcher(int maxConcurrency) {
        validate(maxConcurrency);
        this.maxConcurrency = maxConcurrency;
        this.permits = new ResizableSemaphore(maxConcurrency);
    }

    public synchronized int getMaxConcurrency() {
        return maxConcurrency;
    }

    // Changes the limit at runtime. Requests already running are not interrupted
    public synchronized void setMaxConcurrency(int maxConcurrency) {
        validate(maxConcurrency);
        int delta = maxConcurrency - this.maxConcurrency;
        if (delta > 0)
            permits.release(delta);
        else if (delta < 0)
            permits.reducePermits(-delta);
        this.maxConcurrency = maxConcurrency;
    }

    /**
        Runs all tasks and returns their results in the same order of the tasks.

        Fails fast: on the first failure the other tasks are cancelled and the original
        exception is rethrown (RestClientException, for example). Checked exceptions are wrapped.
     */
    public <T> List<T> fetchAll(List<? extends Callable<T>> tasks) {
        if (tasks.isEmpty())
            return new ArrayList<>();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {

            CompletionService<T> completion = new ExecutorCompletionService<>(executor);
            Map<Future<T>, Integer> indexes = new HashMap<>();

            for (int i = 0; i < tasks.size(); i++)
                indexes.put(completion.submit(tasks.get(i)), i);

            List<T> results = new ArrayList<>(Collections.nCopies(tasks.size(), null));
            try {
                for (int done = 0; done < tasks.size(); done++) {
                    Future<T> future = completion.take();
                    results.set(indexes.get(future), future.get());
                }
            } catch (ExecutionException e) {
                indexes.keySet().forEach(f -> f.cancel(true));
                throw unwrap(e.getCause());
            } catch (InterruptedException e) {
                indexes.keySet().forEach(f -> f.cancel(true));
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while fetching from GitHub", e);
            }
            return results;
        }
    }

    // Runs one HTTP request holding a permit of the token
    public <T> T withPermit(IOCallable<T> request) throws IOException {
        try {
            permits.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Interrupted while waiting for a GitHub request permit");
        }
        try {
            return request.call();
        } finally {
            permits.release();
        }
    }

    private static RuntimeException unwrap(Throwable cause) {
        if (cause instanceof RuntimeException runtime)
            return runtime;
        if (cause instanceof Error error)
            throw error;
        return new IllegalStateException(cause);
    }

    private static void validate(int maxConcurrency) {
        if (maxConcurrency < 1)
            throw new IllegalArgumentException("Invalid max concurrency: " + maxConcurrency);
    }

    // Semaphore.reducePermits is protected
    private static final class ResizableSemaphore extends Semaphore {
        ResizableSemaphore(int permits) {
            super(permits, true);
        }

        @Override
        protected void reducePermits(int reduction) {
            super.reducePermits(reduction);
        }
    }
}