package br.com.jadson.snooper.github.operations;

import br.com.jadson.snooper.github.client.GitHubClientFactory;
import br.com.jadson.snooper.github.client.GitHubClients;
import br.com.jadson.snooper.github.client.GitHubRateLimitInterceptor;
import br.com.jadson.snooper.github.data.LabelInfo;
import br.com.jadson.snooper.github.data.release.GitHubReleaseInfo;
import br.com.jadson.snooper.github.data.repo.GitHubRepoInfo;
import br.com.jadson.snooper.github.data.repo.GitHubTreeInfo;
import br.com.jadson.snooper.github.data.users.GitHubUserInfo;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RepoLabelReleaseUserFetchTest {

    private static final String API = "https://api.github.com";

    private MockRestServiceServer server;
    private GitHubClients clients;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        clients = GitHubClientFactory.create(builder, "token", new GitHubRateLimitInterceptor());
    }

    @Test
    void fetchLabels() {
        server.expect(requestTo(startsWith(API + "/repos/octo/hello/labels?")))
                .andRespond(withSuccess("[{\"name\":\"bug\"},{\"name\":\"enhancement\"}]", MediaType.APPLICATION_JSON));

        LabelQueryExecutor executor = new LabelQueryExecutor();
        executor.setClients(clients);

        Assertions.assertEquals(List.of("bug", "enhancement"),
                executor.fetchLabels("octo/hello").stream().map(l -> l.name).toList());
        server.verify();
    }

    @Test
    void fetchReleasesReadsAllPages() {
        String releasesUrl = API + "/repos/octo/hello/releases";
        HttpHeaders link = new HttpHeaders();
        link.set(HttpHeaders.LINK, "<" + releasesUrl + "?page=2>; rel=\"last\"");

        server.expect(requestTo(startsWith(releasesUrl + "?"))).andExpect(queryParam("page", "1"))
                .andRespond(withSuccess("[{\"tag_name\":\"v2.0\"}]", MediaType.APPLICATION_JSON).headers(link));
        server.expect(requestTo(startsWith(releasesUrl + "?"))).andExpect(queryParam("page", "2"))
                .andRespond(withSuccess("[{\"tag_name\":\"v1.0\"}]", MediaType.APPLICATION_JSON));

        ReleaseQueryExecutor executor = new ReleaseQueryExecutor();
        executor.setClients(clients);

        List<GitHubReleaseInfo> releases = executor.fetchReleases("octo", "hello");

        Assertions.assertEquals(List.of("v2.0", "v1.0"), releases.stream().map(r -> r.tag_name).toList());
        server.verify();
    }

    @Test
    void fetchRepoInfosOfManyRepositories() {
        server.expect(requestTo(API + "/repos/octo/hello"))
                .andRespond(withSuccess("{\"full_name\":\"octo/hello\",\"stargazers_count\":10}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/repos/octo/world"))
                .andRespond(withSuccess("{\"full_name\":\"octo/world\",\"stargazers_count\":20}", MediaType.APPLICATION_JSON));

        RepoQueryExecutor executor = new RepoQueryExecutor();
        executor.setClients(clients);

        Map<String, GitHubRepoInfo> repos = executor.fetchRepoInfos(List.of("octo/world", "octo/hello"));

        Assertions.assertEquals(List.of("octo/world", "octo/hello"), List.copyOf(repos.keySet()));
        Assertions.assertEquals(10, repos.get("octo/hello").stargazers_count);
        server.verify();
    }

    @Test
    void fetchAllFilesIsRecursive() {
        server.expect(requestTo(startsWith(API + "/repos/octo/hello/git/trees/main?")))
                .andExpect(queryParam("recursive", "1"))
                .andRespond(withSuccess("{\"sha\":\"t1\",\"tree\":[{\"path\":\"src\"},{\"path\":\"src/Main.java\"}]}",
                        MediaType.APPLICATION_JSON));

        RepoQueryExecutor executor = new RepoQueryExecutor();
        executor.setClients(clients);

        GitHubTreeInfo tree = executor.fetchAllFiles("octo/hello", "main");

        Assertions.assertEquals(List.of("src", "src/Main.java"), tree.tree.stream().map(i -> i.path).toList());
        server.verify();
    }

    @Test
    void fetchUsersOfManyLogins() {
        for (String login : List.of("alice", "bob")) {
            server.expect(requestTo(API + "/users/" + login))
                    .andRespond(withSuccess("{\"login\":\"" + login + "\",\"name\":\"" + login.toUpperCase() + "\"}",
                            MediaType.APPLICATION_JSON));
        }

        UserQueryExecutor executor = new UserQueryExecutor();
        executor.setClients(clients);

        Map<String, GitHubUserInfo> users = executor.fetchUsers(List.of("bob", "alice", "bob"));

        Assertions.assertEquals(List.of("bob", "alice"), List.copyOf(users.keySet()));
        Assertions.assertEquals("ALICE", users.get("alice").name);
        server.verify();
    }
}
