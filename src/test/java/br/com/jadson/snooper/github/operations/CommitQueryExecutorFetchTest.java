package br.com.jadson.snooper.github.operations;

import br.com.jadson.snooper.github.client.GitHubClientFactory;
import br.com.jadson.snooper.github.client.GitHubRateLimitInterceptor;
import br.com.jadson.snooper.github.data.association.AssociationCommitPullRequestInfo;
import br.com.jadson.snooper.github.data.commit.GitHubCommitInfo;
import br.com.jadson.snooper.github.data.commit.GitHubFileChanged;
import br.com.jadson.snooper.github.data.stats.GitHubCommitStatsInfo;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CommitQueryExecutorFetchTest {

    private static final String API = "https://api.github.com";
    private static final String GRAPHQL_URL = API + "/graphql";

    private MockRestServiceServer server;
    private CommitQueryExecutor executor;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();

        executor = new CommitQueryExecutor();
        executor.setClients(GitHubClientFactory.create(builder, "token", new GitHubRateLimitInterceptor()));
    }

    @Test
    void fetchCommitsReadsAllPages() {
        String commitsUrl = API + "/repos/octo/hello/commits";
        HttpHeaders link = new HttpHeaders();
        link.set(HttpHeaders.LINK, "<" + commitsUrl + "?page=2>; rel=\"next\", <" + commitsUrl + "?page=2>; rel=\"last\"");

        server.expect(requestTo(startsWith(commitsUrl))).andExpect(queryParam("page", "1"))
                .andRespond(withSuccess("[{\"sha\":\"s1\"},{\"sha\":\"s2\"}]", MediaType.APPLICATION_JSON).headers(link));
        server.expect(requestTo(startsWith(commitsUrl))).andExpect(queryParam("page", "2"))
                .andRespond(withSuccess("[{\"sha\":\"s3\"}]", MediaType.APPLICATION_JSON));

        List<GitHubCommitInfo> commits = executor.fetchCommits("octo/hello");

        Assertions.assertEquals(List.of("s1", "s2", "s3"), commits.stream().map(c -> c.sha).toList());
        server.verify();
    }

    @Test
    void fetchCommitFilesOfManyCommitsInParallel() {
        for (String sha : List.of("a1", "b2", "c3")) {
            server.expect(requestTo(API + "/repos/octo/hello/commits/" + sha))
                    .andRespond(withSuccess("{\"sha\":\"" + sha + "\",\"files\":[{\"filename\":\"" + sha + ".java\"}]}",
                            MediaType.APPLICATION_JSON));
        }

        Map<String, List<GitHubFileChanged>> files = executor.fetchCommitFiles("octo/hello", commits("a1", "b2", "c3", "a1"));

        Assertions.assertEquals(List.of("a1", "b2", "c3"), List.copyOf(files.keySet()));
        Assertions.assertEquals("b2.java", files.get("b2").get(0).filename);
        server.verify();
    }

    @Test
    void fetchCommitsWithStatsSplitsThePeriodInWindows() {
        LocalDateTime since = LocalDateTime.of(2021, 1, 1, 0, 0);
        LocalDateTime until = LocalDateTime.of(2021, 1, 31, 0, 0);

        // whole period: 250 commits -> 3 pages -> 3 windows of 10 days and 8 hours
        expectStats("2021-01-01T00:00:00Z", "2021-02-01T00:00:00Z", history(250, true, "x"));
        expectStats("2021-01-21T16:00:00Z", "2021-02-01T00:00:00Z", history(2, false, "e", "d"));
        expectStats("2021-01-11T08:00:00Z", "2021-01-21T16:00:00Z", history(2, false, "d", "c"));
        expectStats("2021-01-01T00:00:00Z", "2021-01-11T08:00:00Z", history(2, false, "b", "a"));

        List<GitHubCommitStatsInfo> commits = executor.fetchCommitsWithStats("octo/hello", since, until);

        // newest first, "d" was on the border of two windows
        Assertions.assertEquals(List.of("e", "d", "c", "b", "a"), commits.stream().map(c -> c.sha).toList());
        Assertions.assertEquals(3, commits.get(0).gitHubCommitStats.additions);
        server.verify();
    }

    @Test
    void fetchCommitsWithStatsOfASinglePage() {
        expectStats("2021-01-01T00:00:00Z", "2021-01-02T00:00:00Z", history(2, false, "b", "a"));

        List<GitHubCommitStatsInfo> commits = executor.fetchCommitsWithStats("octo/hello",
                LocalDateTime.of(2021, 1, 1, 0, 0), LocalDateTime.of(2021, 1, 1, 0, 0));

        Assertions.assertEquals(List.of("b", "a"), commits.stream().map(c -> c.sha).toList());
        server.verify();
    }

    @Test
    void graphQLErrorsAreThrown() {
        server.expect(requestTo(GRAPHQL_URL))
                .andRespond(withSuccess("{\"data\":null,\"errors\":[{\"type\":\"NOT_FOUND\",\"message\":\"Could not resolve to a Repository\"}]}",
                        MediaType.APPLICATION_JSON));

        IllegalStateException error = Assertions.assertThrows(IllegalStateException.class,
                () -> executor.fetchCommitsWithStats("octo/missing", LocalDateTime.now(), LocalDateTime.now()));

        Assertions.assertTrue(error.getMessage().contains("NOT_FOUND: Could not resolve to a Repository"));
    }

    @Test
    void fetchFileStats() {
        server.expect(requestTo(GRAPHQL_URL))
                .andExpect(content().string(containsString("\"path\":\"src/Main.java\"")))
                .andRespond(withSuccess("{\"data\":{\"repository\":{\"defaultBranchRef\":{\"target\":{\"history\":{\"totalCount\":7}}}}}}",
                        MediaType.APPLICATION_JSON));

        Assertions.assertEquals(7, executor.fetchFileStats("octo/hello", "src/Main.java",
                LocalDateTime.of(2021, 1, 1, 0, 0), LocalDateTime.of(2021, 2, 1, 0, 0)).commits);
        server.verify();
    }

    @Test
    void fetchHistoryOfCommitsWithPullRequestsFollowsTheCursor() {
        server.expect(requestTo(GRAPHQL_URL)).andExpect(content().string(containsString("\"after\":null")))
                .andRespond(withSuccess(associationPage(true, "cursor-1", "u1", 6), MediaType.APPLICATION_JSON));
        server.expect(requestTo(GRAPHQL_URL)).andExpect(content().string(containsString("\"after\":\"cursor-1\"")))
                .andRespond(withSuccess(associationPage(false, null, "u2", 7), MediaType.APPLICATION_JSON));

        List<AssociationCommitPullRequestInfo> commits = executor.fetchHistoryOfCommitsWithPullRequests("octo/hello");

        Assertions.assertEquals(List.of("u1", "u2"), commits.stream().map(c -> c.commitUrl).toList());
        Assertions.assertEquals(7, commits.get(1).pullRequestNodeInfos.get(0).number);
        server.verify();
    }

    private void expectStats(String since, String until, String response) {
        server.expect(requestTo(GRAPHQL_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(allOf(
                        containsString("\"since\":\"" + since + "\""),
                        containsString("\"until\":\"" + until + "\""))))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
    }

    private static String history(int totalCount, boolean hasNextPage, String... shas) {
        List<String> nodes = new ArrayList<>();
        for (String sha : shas)
            nodes.add("{\"oid\":\"" + sha + "\",\"comments\":{\"totalCount\":0},\"additions\":3,\"deletions\":1,\"changedFiles\":1}");

        return "{\"data\":{\"repository\":{\"defaultBranchRef\":{\"target\":{\"history\":{"
                + "\"totalCount\":" + totalCount + ","
                + "\"pageInfo\":{\"hasNextPage\":" + hasNextPage + ",\"endCursor\":\"c\"},"
                + "\"nodes\":[" + String.join(",", nodes) + "]}}}}}}";
    }

    private static String associationPage(boolean hasNextPage, String cursor, String commitUrl, int prNumber) {
        return "{\"data\":{\"repository\":{\"object\":{\"history\":{"
                + "\"pageInfo\":{\"hasNextPage\":" + hasNextPage + ",\"endCursor\":" + (cursor == null ? "null" : "\"" + cursor + "\"") + "},"
                + "\"nodes\":[{\"commitUrl\":\"" + commitUrl + "\",\"associatedPullRequests\":{\"edges\":[{\"node\":{\"id\":\"PR_" + prNumber + "\",\"number\":" + prNumber + "}}]}}]"
                + "}}}}}";
    }

    private static List<GitHubCommitInfo> commits(String... shas) {
        List<GitHubCommitInfo> commits = new ArrayList<>();
        for (String sha : shas) {
            GitHubCommitInfo commit = new GitHubCommitInfo();
            commit.sha = sha;
            commits.add(commit);
        }
        return commits;
    }
}
