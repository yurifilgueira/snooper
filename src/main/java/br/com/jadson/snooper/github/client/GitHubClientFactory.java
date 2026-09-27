package br.com.jadson.snooper.github.client;

import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
    Builds the GitHub @HttpExchange clients on top of RestClient.

    Clients are cached per token, so every executor using the same token shares one
    HTTP connection pool, one rate limit state and one concurrency limit (the quota belongs to the token).

    An entry leaves the cache when evict/clear is called or when GitHub answers 401 for that token.
 */
public final class GitHubClientFactory {

    public static final String GIT_HUB_API_URL = "https://api.github.com";

    // key: SHA-256 of the token, so the token itself is not kept as a map key
    private static final Map<String, GitHubClients> CACHE = new ConcurrentHashMap<>();

    private GitHubClientFactory() { }

    // Shared clients of a token (null or empty token means anonymous access)
    public static GitHubClients forToken(String token) {
        return forToken(token, () -> RestClient.builder().requestFactory(defaultRequestFactory()));
    }

    // Tests pass a builder bound to MockRestServiceServer
    static GitHubClients forToken(String token, Supplier<RestClient.Builder> builder) {
        return CACHE.computeIfAbsent(cacheKey(token), key -> {
            GitHubRateLimitInterceptor interceptor = new GitHubRateLimitInterceptor();
            GitHubClients clients = create(builder.get(), token, interceptor);

            // remove only this instance, never a newer one created by another thread
            interceptor.setOnUnauthorized(() -> CACHE.remove(key, clients));
            return clients;
        });
    }

    // Removes the clients of a token. Requests already running keep working, the next call creates new clients
    public static void evict(String token) {
        CACHE.remove(cacheKey(token));
    }

    public static void clear() {
        CACHE.clear();
    }

    static boolean isCached(String token) {
        return CACHE.containsKey(cacheKey(token));
    }

    /**
        Builds new clients from a RestClient.Builder. Use it to customize the HTTP layer
        (proxy, timeouts, MockRestServiceServer in tests). The result is not cached.
     */
    public static GitHubClients create(RestClient.Builder builder, String token, GitHubRateLimitInterceptor rateLimitInterceptor) {

        ConcurrentFetcher fetcher = new ConcurrentFetcher();
        rateLimitInterceptor.setConcurrencyLimiter(fetcher);

        builder.baseUrl(GIT_HUB_API_URL)
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .requestInterceptor(rateLimitInterceptor);

        if (token != null && !token.isBlank())
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token.trim());

        HttpServiceProxyFactory factory = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(builder.build()))
                .build();

        return new GitHubClients(factory.createClient(GitHubRestApi.class), factory.createClient(GitHubGraphQLApi.class), fetcher);
    }

    /**
        JDK HttpClient: blocking calls park virtual threads cheaply and HTTP/2 multiplexes
        the concurrent requests over few connections.
     */
    private static JdkClientHttpRequestFactory defaultRequestFactory() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMinutes(2));
        return requestFactory;
    }

    private static String cacheKey(String token) {
        String value = token == null ? "" : token.trim();
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
