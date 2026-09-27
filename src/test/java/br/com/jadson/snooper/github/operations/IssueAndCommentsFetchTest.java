package br.com.jadson.snooper.github.operations;

import br.com.jadson.snooper.github.client.GitHubClientFactory;
import br.com.jadson.snooper.github.client.GitHubClients;
import br.com.jadson.snooper.github.client.GitHubRateLimitInterceptor;
import br.com.jadson.snooper.github.data.comments.GithubCommentsInfo;
import br.com.jadson.snooper.github.data.comments.GithubIssueCommentsInfo;
import br.com.jadson.snooper.github.data.issue.GitHubIssueInfo;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class IssueAndCommentsFetchTest {

    private static final String REPO_URL = "https://api.github.com/repos/octo/hello";

    private MockRestServiceServer server;
    private GitHubClients clients;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        clients = GitHubClientFactory.create(builder, "token", new GitHubRateLimitInterceptor());
    }

    @Test
    void fetchIssuesAndFilterByPeriod() {
        String issuesUrl = REPO_URL + "/issues";
        HttpHeaders link = new HttpHeaders();
        link.set(HttpHeaders.LINK, "<" + issuesUrl + "?page=2>; rel=\"last\"");

        server.expect(requestTo(startsWith(issuesUrl + "?"))).andExpect(queryParam("page", "1"))
                .andRespond(withSuccess("[{\"number\":1,\"created_at\":\"2021-01-10T10:00:00Z\",\"closed_at\":\"2021-02-10T10:00:00Z\"}]",
                        MediaType.APPLICATION_JSON).headers(link));
        server.expect(requestTo(startsWith(issuesUrl + "?"))).andExpect(queryParam("page", "2"))
                .andRespond(withSuccess("[{\"number\":2,\"created_at\":\"2021-02-15T10:00:00Z\"}]", MediaType.APPLICATION_JSON));

        List<GitHubIssueInfo> issues = issueExecutor().fetchIssuesCreatedInPeriod("octo/hello",
                LocalDateTime.of(2021, 2, 1, 0, 0), LocalDateTime.of(2021, 2, 28, 0, 0));

        Assertions.assertEquals(List.of(2L), issues.stream().map(i -> i.number).toList());
        server.verify();
    }

    @Test
    void fetchQtdIssuesUsesTheSearchApi() {
        server.expect(requestTo(startsWith("https://api.github.com/search/issues")))
                // "type:issue repo:octo/hello" percent-encoded
                .andExpect(queryParam("q", "type%3Aissue%20repo%3Aocto%2Fhello"))
                .andRespond(withSuccess("{\"total_count\":17}", MediaType.APPLICATION_JSON));

        Assertions.assertEquals(17, issueExecutor().fetchQtdIssues("octo/hello"));
        server.verify();
    }

    @Test
    void fetchIssueCommentsOfManyIssues() {
        for (long number : List.of(5L, 6L)) {
            server.expect(requestTo(startsWith(REPO_URL + "/issues/" + number + "/comments?")))
                    .andRespond(withSuccess("[{\"id\":" + number + ",\"body\":\"comment of " + number + "\"}]", MediaType.APPLICATION_JSON));
        }

        IssueCommentsQueryExecutor executor = new IssueCommentsQueryExecutor();
        executor.setClients(clients);

        Map<Long, List<GithubIssueCommentsInfo>> comments = executor.fetchIssueComments("octo/hello", List.of(6L, 5L));

        Assertions.assertEquals(List.of(6L, 5L), List.copyOf(comments.keySet()));
        Assertions.assertEquals("comment of 5", comments.get(5L).get(0).body);
        server.verify();
    }

    /**
        Each PR has 2 pages of comments, so every task of the batch starts another parallel fetch.
        With a limit of 1 request at a time this would deadlock if the permit were held by the whole task.
     */
    @Test
    void nestedParallelFetchesDoNotDeadlockWithConcurrencyOne() {
        clients.fetcher().setMaxConcurrency(1);

        for (long number = 1; number <= 3; number++) {
            String commentsUrl = REPO_URL + "/pulls/" + number + "/comments";
            HttpHeaders link = new HttpHeaders();
            link.set(HttpHeaders.LINK, "<" + commentsUrl + "?page=2>; rel=\"last\"");

            server.expect(requestTo(startsWith(commentsUrl + "?"))).andExpect(queryParam("page", "1"))
                    .andRespond(withSuccess("[{\"id\":" + (number * 10 + 1) + "}]", MediaType.APPLICATION_JSON).headers(link));
            server.expect(requestTo(startsWith(commentsUrl + "?"))).andExpect(queryParam("page", "2"))
                    .andRespond(withSuccess("[{\"id\":" + (number * 10 + 2) + "}]", MediaType.APPLICATION_JSON));
        }

        PullRequestCommentsQueryExecutor executor = new PullRequestCommentsQueryExecutor();
        executor.setClients(clients);

        Map<Long, List<GithubCommentsInfo>> comments = Assertions.assertTimeoutPreemptively(Duration.ofSeconds(10),
                () -> executor.fetchPullRequestComments("octo/hello", List.of(1L, 2L, 3L)));

        Assertions.assertEquals(List.of(21L, 22L), comments.get(2L).stream().map(c -> c.id).toList());
        server.verify();
    }

    private IssueQueryExecutor issueExecutor() {
        IssueQueryExecutor executor = new IssueQueryExecutor();
        executor.setClients(clients);
        return executor;
    }
}
