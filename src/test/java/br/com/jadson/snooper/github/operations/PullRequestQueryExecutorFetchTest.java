package br.com.jadson.snooper.github.operations;

import br.com.jadson.snooper.github.client.GitHubClientFactory;
import br.com.jadson.snooper.github.client.GitHubClients;
import br.com.jadson.snooper.github.client.GitHubRateLimitInterceptor;
import br.com.jadson.snooper.github.data.diff.GitHubPullRequestDiffInfo;
import br.com.jadson.snooper.github.data.pull.GitHubPullRequestInfo;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PullRequestQueryExecutorFetchTest {

    private static final String PULLS_URL = "https://api.github.com/repos/octo/hello/pulls";

    // created_at / closed_at of 3 PRs: #1 in January, #2 in February (closed in March), #3 open
    private static final String PAGE_1 = "["
            + "{\"id\":11,\"number\":1,\"created_at\":\"2021-01-10T10:00:00Z\",\"closed_at\":\"2021-01-20T10:00:00Z\"},"
            + "{\"id\":22,\"number\":2,\"created_at\":\"2021-02-10T10:00:00Z\",\"closed_at\":\"2021-03-05T10:00:00Z\"}]";
    private static final String PAGE_2 = "[{\"id\":33,\"number\":3,\"created_at\":\"2021-03-10T10:00:00Z\"}]";

    private MockRestServiceServer server;
    private GitHubClients clients;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        clients = GitHubClientFactory.create(builder, "token", new GitHubRateLimitInterceptor());
    }

    @Test
    void fetchPullRequestsWithQueryParameters() {
        expectPullRequestPages();

        List<GitHubPullRequestInfo> pulls = pullRequestExecutor().fetchPullRequests("octo", "hello");

        Assertions.assertEquals(List.of(1L, 2L, 3L), pulls.stream().map(pr -> pr.number).toList());
        server.verify();
    }

    @Test
    void fetchPullRequestsCreatedInPeriod() {
        expectPullRequestPages();

        List<GitHubPullRequestInfo> pulls = pullRequestExecutor().fetchPullRequestsCreatedInPeriod("octo/hello",
                LocalDateTime.of(2021, 2, 1, 0, 0), LocalDateTime.of(2021, 3, 31, 0, 0));

        Assertions.assertEquals(List.of(2L, 3L), pulls.stream().map(pr -> pr.number).toList());
    }

    @Test
    void fetchPullRequestsClosedInPeriodSkipsOpenPullRequests() {
        expectPullRequestPages();

        List<GitHubPullRequestInfo> pulls = pullRequestExecutor().fetchPullRequestsClosedInPeriod("octo/hello",
                LocalDateTime.of(2021, 3, 1, 0, 0), LocalDateTime.of(2021, 3, 31, 0, 0));

        Assertions.assertEquals(List.of(2L), pulls.stream().map(pr -> pr.number).toList());
    }

    @Test
    void fetchPullRequestsAssociatedWithCommitRemovesRepeatedPullRequests() {
        server.expect(requestTo(startsWith("https://api.github.com/repos/octo/hello/commits/abc123/pulls")))
                .andRespond(withSuccess("[{\"id\":11,\"number\":1},{\"id\":11,\"number\":1},{\"id\":22,\"number\":2}]",
                        MediaType.APPLICATION_JSON));

        List<GitHubPullRequestInfo> pulls = pullRequestExecutor().fetchPullRequestsAssociatedWithCommit("octo/hello", "abc123");

        Assertions.assertEquals(List.of(1L, 2L), pulls.stream().map(pr -> pr.number).toList());
        server.verify();
    }

    @Test
    void fetchQtdPullRequestsUsesTheSearchApi() {
        server.expect(requestTo(startsWith("https://api.github.com/search/issues")))
                // "type:pr repo:octo/hello" percent-encoded
                .andExpect(queryParam("q", "type%3Apr%20repo%3Aocto%2Fhello"))
                .andExpect(queryParam("per_page", "1"))
                .andRespond(withSuccess("{\"total_count\":42,\"incomplete_results\":false}", MediaType.APPLICATION_JSON));

        Assertions.assertEquals(42, pullRequestExecutor().fetchQtdPullRequests("octo/hello"));
        server.verify();
    }

    @Test
    void fetchPullRequestDiffsOfManyPullRequestsInParallel() {
        for (int number = 1; number <= 3; number++) {
            server.expect(requestTo(PULLS_URL + "/" + number))
                    .andRespond(withSuccess("{\"number\":" + number + ",\"additions\":" + (number * 10) + ",\"deletions\":1}",
                            MediaType.APPLICATION_JSON));
        }

        PullRequestDiffQueryExecutor executor = new PullRequestDiffQueryExecutor();
        executor.setClients(clients);

        Map<Long, GitHubPullRequestDiffInfo> diffs = executor.fetchPullRequestDiffs("octo/hello", List.of(3L, 1L, 2L, 1L));

        Assertions.assertEquals(List.of(3L, 1L, 2L), List.copyOf(diffs.keySet()));
        Assertions.assertEquals(20L, diffs.get(2L).additions);
        server.verify();
    }

    private PullRequestQueryExecutor pullRequestExecutor() {
        PullRequestQueryExecutor executor = new PullRequestQueryExecutor();
        executor.setClients(clients);
        executor.setPageSize(2);
        executor.setQueryParameters(new String[]{"state=all"});
        return executor;
    }

    private void expectPullRequestPages() {
        HttpHeaders link = new HttpHeaders();
        link.set(HttpHeaders.LINK, "<" + PULLS_URL + "?state=all&page=2&per_page=2>; rel=\"last\"");

        server.expect(requestTo(startsWith(PULLS_URL + "?")))
                .andExpect(queryParam("state", "all")).andExpect(queryParam("page", "1"))
                .andRespond(withSuccess(PAGE_1, MediaType.APPLICATION_JSON).headers(link));
        server.expect(requestTo(startsWith(PULLS_URL + "?")))
                .andExpect(queryParam("state", "all")).andExpect(queryParam("page", "2"))
                .andRespond(withSuccess(PAGE_2, MediaType.APPLICATION_JSON));
    }
}
