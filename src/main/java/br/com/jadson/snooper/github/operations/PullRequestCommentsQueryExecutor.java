package br.com.jadson.snooper.github.operations;

import br.com.jadson.snooper.github.data.comments.GithubCommentsInfo;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * This returns the Pull request review comments
 *
 * Pull request review comments are comments made on a portion of the unified diff during a pull request review. These are different from commit comments and issue comments in a pull request.
 */
public class PullRequestCommentsQueryExecutor extends AbstractGitHubQueryExecutor{


    public PullRequestCommentsQueryExecutor(){ }

    public PullRequestCommentsQueryExecutor(String githubToken){
        super(githubToken);
    }


    /**
     * Return all PR review comments of a repository
     *
     * curl \
     *   -H "Accept: application/vnd.github+json" \
     *   -H "Authorization: Bearer <YOUR-TOKEN>" \
     *   https://api.github.com/repos/OWNER/REPO/pulls/comments
     *
     * @param repoFullName
     * @return
     * @deprecated use {@link #fetchPullRequestsComments(String)}, which fetches the pages in parallel
     */
    @Deprecated(since = "3.0")
    public List<GithubCommentsInfo> getPullCommentsInfo(String repoFullName) {

        int page = 1;

        List<GithubCommentsInfo> allComments = new ArrayList<>();

        ResponseEntity<GithubCommentsInfo[]> result;

        do {

            /**
             * GET ***REVIEWS*** COMMENTS
             *
             * https://stackoverflow.com/questions/16198351/get-list-of-comments-from-github-pull-request
             *
             * curl \
             *   -H "Accept: application/vnd.github+json" \
             *   -H "Authorization: Bearer <YOUR-TOKEN>" \
             *   https://api.github.com/repos/OWNER/REPO/pulls/PULL_NUMBER/comments
             */
            String query = GIT_HUB_API_URL +"/repos/"+repoFullName+"/pulls/comments"+"?page="+page+"&per_page="+pageSize;

            System.out.println("Getting Review Comments of PR: "+query);

            RestTemplate restTemplate = new RestTemplate();

            HttpEntity entity = new HttpEntity(getDefaultHeaders());

            result = restTemplate.exchange( query, HttpMethod.GET, entity, GithubCommentsInfo[].class);

            allComments.addAll(  Arrays.asList(result.getBody()) );

            page++;


        }while ( result != null && result.getBody().length > 0 && ! testEnvironment);


        return allComments;
    }

    /**
     * Return all PR review comments of a PR
     *
     * @param repoFullName
     * @return
     * @deprecated use {@link #fetchPullRequestComments(String, long)}, or {@link #fetchPullRequestComments(String, Collection)} for many PRs in parallel
     */
    @Deprecated(since = "3.0")
    public List<GithubCommentsInfo> getPullCommentsInfo(String repoFullName, long prNumber) {

        int page = 1;

        List<GithubCommentsInfo> allComments = new ArrayList<>();

        ResponseEntity<GithubCommentsInfo[]> result;

        do {

            /**
             * GET ***REVIEWS*** COMMENTS
             *
             * https://stackoverflow.com/questions/16198351/get-list-of-comments-from-github-pull-request
             *
             * curl \
             *   -H "Accept: application/vnd.github+json" \
             *   -H "Authorization: Bearer <YOUR-TOKEN>" \
             *   https://api.github.com/repos/OWNER/REPO/pulls/PULL_NUMBER/comments
             */
            String query = GIT_HUB_API_URL +"/repos/"+repoFullName+"/pulls/"+prNumber+"/comments"+"?page="+page+"&per_page="+pageSize;

            System.out.println("Getting Review Comments of PR: "+query);

            RestTemplate restTemplate = new RestTemplate();

            HttpEntity entity = new HttpEntity(getDefaultHeaders());

            result = restTemplate.exchange( query, HttpMethod.GET, entity, GithubCommentsInfo[].class);

            allComments.addAll(  Arrays.asList(result.getBody()) );

            page++;


        }while ( result != null && result.getBody().length > 0 && ! testEnvironment);


        return allComments;

    }

    // @HttpExchange methods: pages and PRs are fetched in parallel on virtual threads

    // Return all PR review comments of a repository
    public List<GithubCommentsInfo> fetchPullRequestsComments(String repoFullName) {
        String owner = owner(repoFullName), name = name(repoFullName);
        PageParams params = pageParams();

        System.out.println("Fetching review comments of " + repoFullName);
        return fetchAllPages(page -> clients().rest().listReviewComments(owner, name, params.forPage(page)));
    }

    // Return all review comments of a PR
    public List<GithubCommentsInfo> fetchPullRequestComments(String repoFullName, long prNumber) {
        String owner = owner(repoFullName), name = name(repoFullName);
        PageParams params = pageParams();

        return fetchAllPages(page -> clients().rest().listReviewCommentsOfPullRequest(owner, name, prNumber, params.forPage(page)));
    }

    /**
        Return the review comments of many PRs. The PRs and their pages are fetched in parallel.

        @return PR number -> review comments, in the order of the numbers
     */
    public Map<Long, List<GithubCommentsInfo>> fetchPullRequestComments(String repoFullName, Collection<Long> prNumbers) {
        System.out.println("Fetching review comments of " + prNumbers.size() + " pull requests of " + repoFullName);
        return fetchEach(new LinkedHashSet<>(prNumbers), number -> fetchPullRequestComments(repoFullName, number));
    }

}
