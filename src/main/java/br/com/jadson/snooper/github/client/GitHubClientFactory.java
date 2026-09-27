package br.com.jadson.snooper.github.client;

import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds the GitHub @HttpExchange clients on top of RestClient.
 *
 * Clients are cached per token, so every executor using the same token shares one
 * HTTP connection pool and one rate limit state (the quota belongs to the token).
 */
public final class GitHubClientFactory {

    public static final String GIT_HUB_API_URL = "https://api.github.com";

    private static final Map<String, GitHubClients> CACHE = new ConcurrentHashMap<>();

    private GitHubClientFactory() { }

    // Shared clients of a token (null or empty token means anonymous access)
    public static GitHubClients forToken(String token) {
        String key = token == null ? "" : token.trim();
        return CACHE.computeIfAbsent(key, t ->
                create(RestClient.builder().requestFactory(defaultRequestFactory()), t, new GitHubRateLimitInterceptor()));
    }

    /**
        Builds new clients from a RestClient.Builder. Use it to customize the HTTP layer
        (proxy, timeouts, MockRestServiceServer in tests).
     */
    public static GitHubClients create(RestClient.Builder builder, String token, GitHubRateLimitInterceptor rateLimitInterceptor) {

        builder.baseUrl(GIT_HUB_API_URL)
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .requestInterceptor(rateLimitInterceptor);

        if (token != null && !token.isBlank())
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token.trim());

        HttpServiceProxyFactory factory = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(builder.build()))
                .build();

        return new GitHubClients(factory.createClient(GitHubRestApi.class), factory.createClient(GitHubGraphQLApi.class));
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
}
