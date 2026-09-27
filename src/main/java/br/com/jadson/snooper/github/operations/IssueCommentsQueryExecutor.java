package br.com.jadson.snooper.github.operations;

import br.com.jadson.snooper.github.data.comments.GithubIssueCommentsInfo;
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
 * This return comments for Issues and also PRs.
 *
 * You can use the REST API to create and manage comments on issues and pull requests.
 * *** Every pull request is an issue ***, but not every issue is a pull request. For this reason,
 * "shared" actions for both features, like managing assignees, labels, and milestones, are provided within the Issues endpoints.
 * @author Jadson Santos - jadsonjs@gmail.com
 */
public class IssueCommentsQueryExecutor extends AbstractGitHubQueryExecutor {

    public IssueCommentsQueryExecutor(){ }

    public IssueCommentsQueryExecutor(String githubToken){
        super(githubToken);
    }


    /**
     * Return all Issues/PR comments of a repository
     *
     * curl -L \
     *   -H "Accept: application/vnd.github+json" \
     *   -H "Authorization: Bearer <YOUR-TOKEN>"\
     *   -H "X-GitHub-Api-Version: 2022-11-28" \
     *   https://api.github.com/repos/OWNER/REPO/issues/comments
     *
     * @param repoFullName
     * @return
     * @deprecated use {@link #fetchIssuesComments(String)}, which fetches the pages in parallel
     */
    @Deprecated(since = "3.0")
    public List<GithubIssueCommentsInfo> getIssuesCommentsInfo(String repoFullName) {

        int page = 1;

        List<GithubIssueCommentsInfo> allComments = new ArrayList<>();

        ResponseEntity<GithubIssueCommentsInfo[]> result;

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
            String query = GIT_HUB_API_URL +"/repos/"+repoFullName+"/issues/comments"+"?page="+page+"&per_page="+pageSize;

            System.out.println("Getting Review Comments of PR: "+query);

            RestTemplate restTemplate = new RestTemplate();

            HttpEntity entity = new HttpEntity(getDefaultHeaders());

            result = restTemplate.exchange( query, HttpMethod.GET, entity, GithubIssueCommentsInfo[].class);

            allComments.addAll(  Arrays.asList(result.getBody()) );

            page++;


        }while ( result != null && result.getBody().length > 0 && ! testEnvironment);


        return allComments;
    }

    /**
     * Return all comments of an Issue or a PR
     *
     * https://docs.github.com/en/rest/issues/comments?apiVersion=2022-11-28#list-issue-comments
     *
     * curl -L \
     *   -H "Accept: application/vnd.github+json" \
     *   -H "Authorization: Bearer <YOUR-TOKEN>"\
     *   -H "X-GitHub-Api-Version: 2022-11-28" \
     *   https://api.github.com/repos/OWNER/REPO/issues/ISSUE_NUMBER/comments
     *
     * @param repoFullName
     * @return
     * @deprecated use {@link #fetchIssueComments(String, long)}, or {@link #fetchIssueComments(String, Collection)} for many issues in parallel
     */
    @Deprecated(since = "3.0")
    public List<GithubIssueCommentsInfo> getIssueCommentsInfo(String repoFullName, long issueNumber) {

        int page = 1;

        List<GithubIssueCommentsInfo> allComments = new ArrayList<>();

        ResponseEntity<GithubIssueCommentsInfo[]> result;

        do {

            String query = GIT_HUB_API_URL +"/repos/"+repoFullName+"/issues/"+issueNumber+"/comments"+"?page="+page+"&per_page="+pageSize;

            System.out.println("Getting Comments of Issue/PR: "+query);

            RestTemplate restTemplate = new RestTemplate();

            HttpEntity entity = new HttpEntity(getDefaultHeaders());

            result = restTemplate.exchange( query, HttpMethod.GET, entity, GithubIssueCommentsInfo[].class);

            allComments.addAll(  Arrays.asList(result.getBody()) );

            page++;


        }while ( result != null && result.getBody().length > 0 && ! testEnvironment);


        return allComments;

    }

    // @HttpExchange methods: pages and issues are fetched in parallel on virtual threads

    // Return all Issues/PR comments of a repository
    public List<GithubIssueCommentsInfo> fetchIssuesComments(String repoFullName) {
        String owner = owner(repoFullName), name = name(repoFullName);
        PageParams params = pageParams();

        System.out.println("Fetching issue comments of " + repoFullName);
        return fetchAllPages(page -> clients().rest().listIssueComments(owner, name, params.forPage(page)));
    }

    // Return all comments of an Issue or a PR
    public List<GithubIssueCommentsInfo> fetchIssueComments(String repoFullName, long issueNumber) {
        String owner = owner(repoFullName), name = name(repoFullName);
        PageParams params = pageParams();

        return fetchAllPages(page -> clients().rest().listCommentsOfIssue(owner, name, issueNumber, params.forPage(page)));
    }

    /**
        Return the comments of many Issues/PRs. The issues and their pages are fetched in parallel.

        @return issue number -> comments, in the order of the numbers
     */
    public Map<Long, List<GithubIssueCommentsInfo>> fetchIssueComments(String repoFullName, Collection<Long> issueNumbers) {
        System.out.println("Fetching comments of " + issueNumbers.size() + " issues of " + repoFullName);
        return fetchEach(new LinkedHashSet<>(issueNumbers), number -> fetchIssueComments(repoFullName, number));
    }

}
