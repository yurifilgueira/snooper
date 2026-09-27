package br.com.jadson.snooper.github.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class GitHubClientFactoryTest {

    @AfterEach
    void tearDown() {
        GitHubClientFactory.clear();
    }

    @Test
    void sameTokenSharesTheClients() {
        GitHubClients first = GitHubClientFactory.forToken("token-a");

        Assertions.assertSame(first, GitHubClientFactory.forToken(" token-a "));
        Assertions.assertNotSame(first, GitHubClientFactory.forToken("token-b"));
        Assertions.assertSame(GitHubClientFactory.forToken(null), GitHubClientFactory.forToken(""));
    }

    @Test
    void evictAndClear() {
        GitHubClients first = GitHubClientFactory.forToken("token-a");
        GitHubClientFactory.forToken("token-b");

        GitHubClientFactory.evict("token-a");
        Assertions.assertFalse(GitHubClientFactory.isCached("token-a"));
        Assertions.assertTrue(GitHubClientFactory.isCached("token-b"));
        Assertions.assertNotSame(first, GitHubClientFactory.forToken("token-a"));

        GitHubClientFactory.clear();
        Assertions.assertFalse(GitHubClientFactory.isCached("token-a"));
        Assertions.assertFalse(GitHubClientFactory.isCached("token-b"));
    }

    @Test
    void unauthorizedEvictsTheToken() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.github.com/repos/octo/hello"))
                .andExpect(header("Authorization", "Bearer revoked-token"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        GitHubClients clients = GitHubClientFactory.forToken("revoked-token", () -> builder);
        Assertions.assertTrue(GitHubClientFactory.isCached("revoked-token"));

        Assertions.assertThrows(HttpClientErrorException.Unauthorized.class, () -> clients.rest().getRepo("octo", "hello"));

        Assertions.assertFalse(GitHubClientFactory.isCached("revoked-token"));
        server.verify();
    }
}
