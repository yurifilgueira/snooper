package br.com.jadson.snooper.github.operations;

import br.com.jadson.snooper.github.client.GitHubClientFactory;
import br.com.jadson.snooper.github.client.GitHubRateLimitInterceptor;
import br.com.jadson.snooper.github.data.LabelInfo;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.startsWith;

class FetchAllPagesTest {

    private static final String LABELS_URL = "https://api.github.com/repos/octo/hello/labels";

    private MockRestServiceServer server;
    private LabelsExecutor executor;

    // Uses the protected helpers the same way the fetch* methods will
    static class LabelsExecutor extends AbstractGitHubQueryExecutor {
        List<LabelInfo> labels(String repoFullName) {
            PageParams params = pageParams();
            return fetchAllPages(page -> clients().rest().listLabels(owner(repoFullName), name(repoFullName), params.forPage(page)));
        }
    }

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();

        executor = new LabelsExecutor();
        executor.setClients(GitHubClientFactory.create(builder, "token", new GitHubRateLimitInterceptor()));
        executor.setPageSize(2);
        executor.setQueryParameters(new String[]{"sort=name"});
    }

    @Test
    void fetchesTheOtherPagesInParallelAndKeepsTheOrder() {
        expectPage(1, "[{\"name\":\"a\"},{\"name\":\"b\"}]",
                "<" + LABELS_URL + "?page=2&per_page=2>; rel=\"next\", <" + LABELS_URL + "?page=3&per_page=2>; rel=\"last\"");
        expectPage(2, "[{\"name\":\"c\"},{\"name\":\"d\"}]", null);
        expectPage(3, "[{\"name\":\"e\"}]", null);

        List<LabelInfo> labels = executor.labels("octo/hello");

        Assertions.assertEquals(List.of("a", "b", "c", "d", "e"), labels.stream().map(l -> l.name).toList());
        server.verify();
    }

    @Test
    void followsNextWhenThereIsNoLast() {
        expectPage(1, "[{\"name\":\"a\"}]", "<" + LABELS_URL + "?page=2>; rel=\"next\"");
        expectPage(2, "[{\"name\":\"b\"}]", "<" + LABELS_URL + "?page=3>; rel=\"next\"");
        expectPage(3, "[{\"name\":\"c\"}]", null);

        List<LabelInfo> labels = executor.labels("octo/hello/");

        Assertions.assertEquals(List.of("a", "b", "c"), labels.stream().map(l -> l.name).toList());
        server.verify();
    }

    @Test
    void testEnvironmentFetchesOnlyTheFirstPage() {
        executor.setTestEnvironment(true);
        expectPage(1, "[{\"name\":\"a\"}]", "<" + LABELS_URL + "?page=9>; rel=\"last\"");

        Assertions.assertEquals(1, executor.labels("octo/hello").size());
        server.verify();
    }

    @Test
    void emptyRepository() {
        expectPage(1, "[]", null);

        Assertions.assertTrue(executor.labels("octo/hello").isEmpty());
        server.verify();
    }

    @Test
    void fetchEachKeepsTheOrderOfTheKeys() {
        Map<Integer, String> result = executor.fetchEach(List.of(3, 1, 2), n -> "value-" + n);

        Assertions.assertEquals(List.of(3, 1, 2), List.copyOf(result.keySet()));
        Assertions.assertEquals("value-1", result.get(1));
    }

    @Test
    void parsesTheQueryParameters() {
        MultiValueMap<String, String> params =
                AbstractGitHubQueryExecutor.parseQueryParameters("state=all&since=2021-03-01T22:26:45Z&&flag&");

        Assertions.assertEquals("all", params.getFirst("state"));
        Assertions.assertEquals("2021-03-01T22:26:45Z", params.getFirst("since"));
        Assertions.assertEquals("", params.getFirst("flag"));
        Assertions.assertEquals(3, params.size());
        Assertions.assertTrue(AbstractGitHubQueryExecutor.parseQueryParameters(null).isEmpty());
    }

    @Test
    void invalidRepoName() {
        Assertions.assertThrows(RuntimeException.class, () -> executor.owner("octo"));
        Assertions.assertThrows(RuntimeException.class, () -> executor.name("/hello"));
        Assertions.assertEquals("octo", executor.owner("octo/hello/"));
        Assertions.assertEquals("hello", executor.name("octo/hello/"));
    }

    private void expectPage(int page, String json, String link) {
        HttpHeaders headers = new HttpHeaders();
        if (link != null)
            headers.set(HttpHeaders.LINK, link);

        server.expect(requestTo(startsWith(LABELS_URL + "?")))
                .andExpect(queryParam("page", String.valueOf(page)))
                .andExpect(queryParam("per_page", "2"))
                .andExpect(queryParam("sort", "name"))
                .andExpect(header("Authorization", "Bearer token"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON).headers(headers));
    }
}
