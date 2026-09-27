package br.com.jadson.snooper.github.client;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps concurrent requests inside the GitHub rate limits.
 *
 * - Tracks X-RateLimit-Remaining / X-RateLimit-Reset per resource (core, search, graphql), because each one has its own quota.
 * - When the remaining quota drops to the threshold, new requests wait until the reset time.
 * - On 403/429 caused by a rate limit (primary or secondary) it waits (Retry-After, reset time or backoff) and retries.
 *
 * https://docs.github.com/en/rest/using-the-rest-api/rate-limits-for-the-rest-api
 *
 * Share one instance per token: the quota belongs to the token, not to the executor.
 */
public class GitHubRateLimitInterceptor implements ClientHttpRequestInterceptor {

    /** How the interceptor waits. Replaced in tests to avoid real sleeps. */
    @FunctionalInterface
    public interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    public static final int DEFAULT_THRESHOLD = 10;
    public static final int DEFAULT_MAX_RETRIES = 3;

    /** GitHub recommends waiting at least one minute on a secondary rate limit without Retry-After */
    private static final Duration SECONDARY_LIMIT_BACKOFF = Duration.ofSeconds(60);

    private final int threshold;
    private final int maxRetries;
    private final Sleeper sleeper;
    private final Clock clock;

    private final Map<String, Quota> quotas = new ConcurrentHashMap<>();

    public GitHubRateLimitInterceptor() {
        this(DEFAULT_THRESHOLD, DEFAULT_MAX_RETRIES, d -> Thread.sleep(d.toMillis()), Clock.systemUTC());
    }

    public GitHubRateLimitInterceptor(int threshold, int maxRetries, Sleeper sleeper, Clock clock) {
        this.threshold = threshold;
        this.maxRetries = maxRetries;
        this.sleeper = sleeper;
        this.clock = clock;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {

        Quota quota = quotas.computeIfAbsent(resourceOf(request.getURI().getPath()), r -> new Quota());

        for (int attempt = 0; ; attempt++) {

            Duration wait = quota.waitBeforeRequest(threshold, clock.instant().getEpochSecond());
            if (!wait.isZero()) {
                System.out.println("GitHub rate limit almost exhausted, waiting " + wait.toSeconds() + "s until reset");
                sleep(wait);
            }

            ClientHttpResponse response = execution.execute(request, body);
            quota.update(response.getHeaders());

            int status = response.getStatusCode().value();
            if ((status == 403 || status == 429) && attempt < maxRetries) {
                Duration retryDelay = retryDelay(status, response.getHeaders(), attempt);
                if (retryDelay != null) {
                    response.close();
                    System.out.println("GitHub rate limit reached (HTTP " + status + "), retrying in " + retryDelay.toSeconds() + "s");
                    sleep(retryDelay);
                    continue;
                }
            }
            return response;
        }
    }

    /**
     * @return how long to wait before retrying, or null when the error is not caused by a rate limit
     */
    Duration retryDelay(int status, HttpHeaders headers, int attempt) {
        String retryAfter = headers.getFirst("Retry-After");
        if (retryAfter != null) {
            try {
                return Duration.ofSeconds(Long.parseLong(retryAfter.trim()));
            } catch (NumberFormatException ignored) { }
        }

        if ("0".equals(headers.getFirst("X-RateLimit-Remaining"))) {
            long reset = parseLong(headers.getFirst("X-RateLimit-Reset"), 0);
            long seconds = Math.max(reset - clock.instant().getEpochSecond(), 0) + 1;
            return Duration.ofSeconds(seconds);
        }

        // 429 is always a rate limit; a 403 without rate limit headers is a real permission error
        if (status == 429)
            return SECONDARY_LIMIT_BACKOFF.multipliedBy(1L << attempt);

        return null;
    }

    private void sleep(Duration duration) throws IOException {
        try {
            sleeper.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Interrupted while waiting for the GitHub rate limit");
        }
    }

    static String resourceOf(String path) {
        if (path == null) return "core";
        if (path.startsWith("/graphql")) return "graphql";
        if (path.startsWith("/search")) return "search";
        return "core";
    }

    private static long parseLong(String value, long defaultValue) {
        if (value == null) return defaultValue;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Quota of one resource. Responses of concurrent requests arrive out of order,
     * so inside the same reset window we keep the smallest remaining value seen.
     */
    static final class Quota {

        private long remaining = Long.MAX_VALUE;
        private long resetEpochSecond = 0;

        synchronized void update(HttpHeaders headers) {
            long newRemaining = parseLong(headers.getFirst("X-RateLimit-Remaining"), -1);
            long newReset = parseLong(headers.getFirst("X-RateLimit-Reset"), -1);
            if (newRemaining < 0 || newReset < 0)
                return;

            if (newReset > resetEpochSecond) {
                resetEpochSecond = newReset;
                remaining = newRemaining;
            } else if (newReset == resetEpochSecond) {
                remaining = Math.min(remaining, newRemaining);
            }
        }

        synchronized Duration waitBeforeRequest(int threshold, long nowEpochSecond) {
            if (remaining > threshold || resetEpochSecond <= nowEpochSecond)
                return Duration.ZERO;
            return Duration.ofSeconds(resetEpochSecond - nowEpochSecond + 1);
        }
    }
}
