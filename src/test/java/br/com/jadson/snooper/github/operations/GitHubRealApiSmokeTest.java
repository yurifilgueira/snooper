package br.com.jadson.snooper.github.operations;

import br.com.jadson.snooper.github.data.LabelInfo;
import br.com.jadson.snooper.github.data.commit.GitHubCommitInfo;
import br.com.jadson.snooper.github.data.commit.GitHubFileChanged;
import br.com.jadson.snooper.github.data.issue.GitHubIssueInfo;
import br.com.jadson.snooper.github.data.pull.GitHubPullRequestInfo;
import br.com.jadson.snooper.github.data.repo.GitHubRepoInfo;
import br.com.jadson.snooper.github.data.stats.GitHubCommitStatsInfo;
import br.com.jadson.snooper.github.data.users.GitHubUserInfo;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
    Compares the old RestTemplate methods with the new fetch* methods against the real GitHub API.

    Runs only with SNOOPER_REAL_API=true. Without GITHUB_TOKEN it uses about 20 of the 60 anonymous requests per hour
    and skips the GraphQL test (GraphQL requires a token).

    PowerShell: $env:SNOOPER_REAL_API="true"; .\gradlew.bat test --rerun --tests "*GitHubRealApiSmokeTest"
    (--rerun: environment variables are not Gradle inputs, without it an old result can be reused)
 */
@EnabledIfEnvironmentVariable(named = "SNOOPER_REAL_API", matches = "true")
@SuppressWarnings("deprecation")
class GitHubRealApiSmokeTest {

    // small and stable public repository: 3 commits, 1 label
    private static final String REPO = "octocat/Hello-World";

    private final String token = System.getenv("GITHUB_TOKEN");

    @Test
    void commitsWithOneCommitPerPageUseTheRealLinkHeader() {
        CommitQueryExecutor executor = new CommitQueryExecutor();
        executor.setGithubToken(token);
        executor.setPageSize(1);

        // the old method stops on an empty page, the new one reads rel="last" and fetches the pages in parallel
        List<GitHubCommitInfo> oldCommits = executor.getCommits(REPO);
        List<GitHubCommitInfo> newCommits = executor.fetchCommits(REPO);

        Assertions.assertTrue(newCommits.size() > 1, "expected more than one page");
        Assertions.assertEquals(shas(oldCommits), shas(newCommits));
    }

    @Test
    void commitFilesOfAllCommits() {
        CommitQueryExecutor executor = new CommitQueryExecutor();
        executor.setGithubToken(token);
        List<GitHubCommitInfo> commits = executor.fetchCommits(REPO);

        Map<String, List<GitHubFileChanged>> newFiles = executor.fetchCommitFiles(REPO, commits);

        Assertions.assertEquals(shas(commits), List.copyOf(newFiles.keySet()));
        for (GitHubCommitInfo commit : commits) {
            List<GitHubFileChanged> oldFiles = executor.getCommitFiles(REPO, commit);
            Assertions.assertEquals(filenames(oldFiles), filenames(newFiles.get(commit.sha)), "files of " + commit.sha);
        }
    }

    @Test
    void firstPageOfPullRequestsAndIssues() {
        // Hello-World has thousands of PRs: only the first page, in the old and new methods
        PullRequestQueryExecutor pulls = new PullRequestQueryExecutor();
        pulls.setGithubToken(token);
        pulls.setTestEnvironment(true);
        pulls.setPageSize(5);

        List<GitHubPullRequestInfo> oldPulls = pulls.pullRequests(REPO);
        List<GitHubPullRequestInfo> newPulls = pulls.fetchPullRequests(REPO);
        Assertions.assertEquals(5, newPulls.size());
        Assertions.assertEquals(oldPulls.stream().map(p -> p.number).toList(), newPulls.stream().map(p -> p.number).toList());

        IssueQueryExecutor issues = new IssueQueryExecutor();
        issues.setGithubToken(token);
        issues.setTestEnvironment(true);
        issues.setPageSize(5);

        List<GitHubIssueInfo> oldIssues = issues.issues(REPO);
        List<GitHubIssueInfo> newIssues = issues.fetchIssues(REPO);
        Assertions.assertEquals(oldIssues.stream().map(i -> i.number).toList(), newIssues.stream().map(i -> i.number).toList());
    }

    @Test
    void repoLabelsAndUsers() {
        RepoQueryExecutor repos = new RepoQueryExecutor();
        repos.setGithubToken(token);
        GitHubRepoInfo oldRepo = repos.getRepoInfo(REPO);
        GitHubRepoInfo newRepo = repos.fetchRepoInfo(REPO);
        Assertions.assertEquals(oldRepo.full_name, newRepo.full_name);
        Assertions.assertEquals(REPO, newRepo.full_name);

        LabelQueryExecutor labels = new LabelQueryExecutor();
        labels.setGithubToken(token);
        List<LabelInfo> oldLabels = labels.labels(REPO);
        List<LabelInfo> newLabels = labels.fetchLabels(REPO);
        Assertions.assertEquals(oldLabels.stream().map(l -> l.name).toList(), newLabels.stream().map(l -> l.name).toList());

        UserQueryExecutor users = new UserQueryExecutor();
        users.setGithubToken(token);
        Map<String, GitHubUserInfo> fetched = users.fetchUsers(List.of("octocat"));
        Assertions.assertEquals(users.user("octocat").login, fetched.get("octocat").login);
    }

    @Test
    void commitsWithStatsNeedAToken() {
        Assumptions.assumeTrue(token != null && !token.isBlank(), "GraphQL requires GITHUB_TOKEN");

        CommitQueryExecutor executor = new CommitQueryExecutor();
        executor.setGithubToken(token);
        LocalDateTime since = LocalDateTime.of(2011, 1, 26, 0, 0);
        LocalDateTime until = LocalDateTime.of(2012, 3, 6, 0, 0);

        List<GitHubCommitStatsInfo> oldStats = executor.getCommitsWithStats(REPO, since, until);
        List<GitHubCommitStatsInfo> newStats = executor.fetchCommitsWithStats(REPO, since, until);

        Assertions.assertEquals(oldStats.stream().map(s -> s.sha).toList(), newStats.stream().map(s -> s.sha).toList());
    }

    private static List<String> shas(List<GitHubCommitInfo> commits) {
        return commits.stream().map(c -> c.sha).toList();
    }

    private static List<String> filenames(List<GitHubFileChanged> files) {
        return files.stream().map(f -> f.filename).toList();
    }
}
