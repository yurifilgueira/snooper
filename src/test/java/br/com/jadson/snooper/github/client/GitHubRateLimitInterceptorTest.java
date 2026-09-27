package br.com.jadson.snooper.github.client;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GitHubRateLimitInterceptorTest {

    private static final long NOW = 1_000_000L;
    private static final String USER_URL = "https://api.github.com/users/octocat";
    private static final String SEARCH_URL = "https://api.github.com/search/issues";

    private final List<Duration> sleeps = new ArrayList<>();

    private GitHubRateLimitInterceptor interceptor;
    private MockRestServiceServer server;
    private RestClient client;

    // the clock moves forward only when the interceptor sleeps, like a real one
    private Instant now = Instant.ofEpochSecond(NOW);

    @BeforeEach
    void setUp() {
        Clock clock = new Clock() {
            @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return now; }
        };
        interceptor = new GitHubRateLimitInterceptor(10, 3, duration -> {
            sleeps.add(duration);
            now = now.plus(duration);
        }, clock);

        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = builder.requestInterceptor(interceptor).build();
    }

    @Test
    void retriesAfterRetryAfterSeconds() {
        server.expect(requestTo(USER_URL)).andRespond(withStatus(HttpStatus.FORBIDDEN).headers(headers("Retry-After", "2")));
        server.expect(requestTo(USER_URL)).andRespond(ok());

        Assertions.assertEquals("{}", get(USER_URL));
        Assertions.assertEquals(List.of(Duration.ofSeconds(2)), sleeps);
        server.verify();
    }

    @Test
    void primaryLimitWaitsUntilTheReset() {
        server.expect(requestTo(USER_URL)).andRespond(withStatus(HttpStatus.FORBIDDEN)
                .headers(headers("X-RateLimit-Remaining", "0", "X-RateLimit-Reset", String.valueOf(NOW + 30))));
        server.expect(requestTo(USER_URL)).andRespond(ok());

        get(USER_URL);

        Assertions.assertEquals(List.of(Duration.ofSeconds(31)), sleeps);
        server.verify();
    }

    @Test
    void secondaryLimitWithoutHeadersBacksOff() {
        server.expect(requestTo(USER_URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(requestTo(USER_URL)).andRespond(ok());

        get(USER_URL);

        Assertions.assertEquals(List.of(Duration.ofSeconds(60)), sleeps);
        server.verify();
    }

    @Test
    void givesUpAfterMaxRetries() {
        for (int i = 0; i < 4; i++)
            server.expect(requestTo(USER_URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        Assertions.assertThrows(HttpClientErrorException.TooManyRequests.class, () -> get(USER_URL));
        Assertions.assertEquals(List.of(Duration.ofSeconds(60), Duration.ofSeconds(120), Duration.ofSeconds(240)), sleeps);
        server.verify();
    }

    @Test
    void permissionErrorIsNotRetried() {
        server.expect(requestTo(USER_URL)).andRespond(withStatus(HttpStatus.FORBIDDEN));

        Assertions.assertThrows(HttpClientErrorException.Forbidden.class, () -> get(USER_URL));
        Assertions.assertTrue(sleeps.isEmpty());
        server.verify();
    }

    @Test
    void waitsWhenTheQuotaIsAlmostExhaustedAndQuotasArePerResource() {
        // core quota drops below the threshold (10)
        server.expect(requestTo(USER_URL)).andRespond(ok()
                .headers(headers("X-RateLimit-Remaining", "5", "X-RateLimit-Reset", String.valueOf(NOW + 100))));
        // search has its own quota: no wait
        server.expect(requestTo(SEARCH_URL)).andRespond(ok());
        // next core request waits for the reset
        server.expect(requestTo(USER_URL)).andRespond(ok());

        get(USER_URL);
        get(SEARCH_URL);
        Assertions.assertTrue(sleeps.isEmpty());

        get(USER_URL);
        Assertions.assertEquals(List.of(Duration.ofSeconds(101)), sleeps);
        server.verify();
    }

    @Test
    void outOfOrderResponsesKeepTheLowestRemaining() {
        GitHubRateLimitInterceptor.Quota quota = new GitHubRateLimitInterceptor.Quota();

        quota.update(headers("X-RateLimit-Remaining", "8", "X-RateLimit-Reset", String.valueOf(NOW + 50)));
        quota.update(headers("X-RateLimit-Remaining", "40", "X-RateLimit-Reset", String.valueOf(NOW + 50)));

        Assertions.assertEquals(Duration.ofSeconds(51), quota.waitBeforeRequest(10, NOW));

        // a new window after the reset
        quota.update(headers("X-RateLimit-Remaining", "4999", "X-RateLimit-Reset", String.valueOf(NOW + 3600)));
        Assertions.assertEquals(Duration.ZERO, quota.waitBeforeRequest(10, NOW));
    }

    @Test
    void unauthorizedCallsTheCallbackAndStillFails() {
        AtomicInteger calls = new AtomicInteger();
        interceptor.setOnUnauthorized(calls::incrementAndGet);
        server.expect(requestTo(USER_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        Assertions.assertThrows(HttpClientErrorException.Unauthorized.class, () -> get(USER_URL));
        Assertions.assertEquals(1, calls.get());
        Assertions.assertTrue(sleeps.isEmpty());
    }

    private String get(String url) {
        return client.get().uri(url).retrieve().body(String.class);
    }

    private static org.springframework.test.web.client.response.DefaultResponseCreator ok() {
        return withSuccess("{}", MediaType.APPLICATION_JSON);
    }

    private static HttpHeaders headers(String... namesAndValues) {
        HttpHeaders headers = new HttpHeaders();
        for (int i = 0; i < namesAndValues.length; i += 2)
            headers.set(namesAndValues[i], namesAndValues[i + 1]);
        return headers;
    }
}
