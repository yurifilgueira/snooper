package br.com.jadson.snooper.github.client;

import br.com.jadson.snooper.github.data.LabelInfo;
import br.com.jadson.snooper.github.data.comments.GithubCommentsInfo;
import br.com.jadson.snooper.github.data.comments.GithubIssueCommentsInfo;
import br.com.jadson.snooper.github.data.commit.GitHubCommitInfo;
import br.com.jadson.snooper.github.data.diff.GitHubPullRequestDiffInfo;
import br.com.jadson.snooper.github.data.issue.GitHubIssueInfo;
import br.com.jadson.snooper.github.data.pull.GitHubPullRequestInfo;
import br.com.jadson.snooper.github.data.pull.GitHubQTDPullRequestInfo;
import br.com.jadson.snooper.github.data.release.GitHubReleaseInfo;
import br.com.jadson.snooper.github.data.repo.GitHubRepoInfo;
import br.com.jadson.snooper.github.data.repo.GitHubTreeInfo;
import br.com.jadson.snooper.github.data.users.GitHubUserInfo;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/**
 * GitHub REST API v3 as a Spring declarative HTTP interface.
 *
 * List endpoints return ResponseEntity so the caller can read the Link (pagination)
 * and X-RateLimit-* headers. The params map carries page, per_page and the user query parameters.
 *
 * https://docs.github.com/en/rest
 */
@HttpExchange(accept = "application/vnd.github+json")
public interface GitHubRestApi {

    // Repository

    @GetExchange("/repos/{owner}/{repo}")
    GitHubRepoInfo getRepo(@PathVariable String owner, @PathVariable String repo);

    @GetExchange("/repos/{owner}/{repo}/git/trees/{branch}")
    GitHubTreeInfo getTree(@PathVariable String owner, @PathVariable String repo, @PathVariable String branch,
                           @RequestParam("recursive") int recursive);

    // Commits

    @GetExchange("/repos/{owner}/{repo}/commits")
    ResponseEntity<GitHubCommitInfo[]> listCommits(@PathVariable String owner, @PathVariable String repo,
                                                   @RequestParam MultiValueMap<String, String> params);

    // A single commit (with its files). Ref can be a sha, a branch or a tag

    @GetExchange("/repos/{owner}/{repo}/commits/{ref}")
    GitHubCommitInfo getCommit(@PathVariable String owner, @PathVariable String repo, @PathVariable String ref);

    @GetExchange("/repos/{owner}/{repo}/commits/{ref}")
    GitHubCommitInfo getCommit(@PathVariable String owner, @PathVariable String repo, @PathVariable String ref,
                               @RequestParam MultiValueMap<String, String> params);

    @GetExchange("/repos/{owner}/{repo}/commits/{sha}/pulls")
    ResponseEntity<GitHubPullRequestInfo[]> listPullRequestsOfCommit(@PathVariable String owner, @PathVariable String repo,
                                                                     @PathVariable String sha,
                                                                     @RequestParam MultiValueMap<String, String> params);

    // Pull Requests

    @GetExchange("/repos/{owner}/{repo}/pulls")
    ResponseEntity<GitHubPullRequestInfo[]> listPullRequests(@PathVariable String owner, @PathVariable String repo,
                                                             @RequestParam MultiValueMap<String, String> params);

    @GetExchange("/repos/{owner}/{repo}/pulls/{pullNumber}")
    GitHubPullRequestInfo getPullRequest(@PathVariable String owner, @PathVariable String repo, @PathVariable long pullNumber);

    // Same endpoint as getPullRequest, mapped to the diff statistics (additions, deletions, changed_files)
    @GetExchange("/repos/{owner}/{repo}/pulls/{pullNumber}")
    GitHubPullRequestDiffInfo getPullRequestDiff(@PathVariable String owner, @PathVariable String repo, @PathVariable long pullNumber);

    @GetExchange("/repos/{owner}/{repo}/pulls/{pullNumber}/commits")
    ResponseEntity<GitHubCommitInfo[]> listCommitsOfPullRequest(@PathVariable String owner, @PathVariable String repo,
                                                                @PathVariable long pullNumber,
                                                                @RequestParam MultiValueMap<String, String> params);

    // Review comments of all pull requests of the repository
    @GetExchange("/repos/{owner}/{repo}/pulls/comments")
    ResponseEntity<GithubCommentsInfo[]> listReviewComments(@PathVariable String owner, @PathVariable String repo,
                                                            @RequestParam MultiValueMap<String, String> params);

    @GetExchange("/repos/{owner}/{repo}/pulls/{pullNumber}/comments")
    ResponseEntity<GithubCommentsInfo[]> listReviewCommentsOfPullRequest(@PathVariable String owner, @PathVariable String repo,
                                                                         @PathVariable long pullNumber,
                                                                         @RequestParam MultiValueMap<String, String> params);

    // Issues

    @GetExchange("/repos/{owner}/{repo}/issues")
    ResponseEntity<GitHubIssueInfo[]> listIssues(@PathVariable String owner, @PathVariable String repo,
                                                 @RequestParam MultiValueMap<String, String> params);

    @GetExchange("/repos/{owner}/{repo}/issues/{issueNumber}")
    GitHubIssueInfo getIssue(@PathVariable String owner, @PathVariable String repo, @PathVariable long issueNumber);

    // Comments of all issues and pull requests of the repository
    @GetExchange("/repos/{owner}/{repo}/issues/comments")
    ResponseEntity<GithubIssueCommentsInfo[]> listIssueComments(@PathVariable String owner, @PathVariable String repo,
                                                                @RequestParam MultiValueMap<String, String> params);

    @GetExchange("/repos/{owner}/{repo}/issues/{issueNumber}/comments")
    ResponseEntity<GithubIssueCommentsInfo[]> listCommentsOfIssue(@PathVariable String owner, @PathVariable String repo,
                                                                  @PathVariable long issueNumber,
                                                                  @RequestParam MultiValueMap<String, String> params);

    // Labels / Releases / Users

    @GetExchange("/repos/{owner}/{repo}/labels")
    ResponseEntity<LabelInfo[]> listLabels(@PathVariable String owner, @PathVariable String repo,
                                           @RequestParam MultiValueMap<String, String> params);

    @GetExchange("/repos/{owner}/{repo}/releases")
    ResponseEntity<GitHubReleaseInfo[]> listReleases(@PathVariable String owner, @PathVariable String repo,
                                                     @RequestParam MultiValueMap<String, String> params);

    @GetExchange("/users/{login}")
    GitHubUserInfo getUser(@PathVariable String login, @RequestParam MultiValueMap<String, String> params);

    // Search

    // Only total_count is mapped; per_page=1 keeps the response small
    @GetExchange("/search/issues")
    GitHubQTDPullRequestInfo searchIssues(@RequestParam("q") String query, @RequestParam("per_page") int perPage);
}
