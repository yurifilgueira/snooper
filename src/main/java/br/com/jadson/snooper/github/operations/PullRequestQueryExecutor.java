/*
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 *
 * snooper
 * br.com.jadson.snooper.github
 * GitHubPullRequest
 * 23/09/20
 */
package br.com.jadson.snooper.github.operations;

import br.com.jadson.snooper.github.data.pull.GitHubPullRequestInfo;
import br.com.jadson.snooper.github.data.pull.GitHubQTDPullRequestInfo;
import br.com.jadson.snooper.utils.DateUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Queries about Pull Request
 *
 * Jadson Santos - jadsonjs@gmail.com
 */
public class PullRequestQueryExecutor extends AbstractGitHubQueryExecutor {

    public PullRequestQueryExecutor(){ }

    public PullRequestQueryExecutor(String githubToken){
        super(githubToken);
    }

    /**
     * @deprecated use {@link #fetchPullRequests(String, String)}, which fetches the pages in parallel
     */
    @Deprecated(since = "3.0")
    public List<GitHubPullRequestInfo> pullRequests(String repoOwner, String repoName) {
        return pullRequests(repoOwner+"/"+repoName);
    }

    /**
     * Return all PR of a project
     *
     * @param repoFullName
     * @return
     * @deprecated use {@link #fetchPullRequests(String)}, which fetches the pages in parallel
     */
    @Deprecated(since = "3.0")
    public List<GitHubPullRequestInfo> pullRequests(String repoFullName) {

        validateRepoName(repoFullName);

        int page = 1;

        // IMPORTANTE state=all for bring all PR
        String parameters = "";

        List<GitHubPullRequestInfo> allPull = new ArrayList<>();

        ResponseEntity<GitHubPullRequestInfo[]> result;

        do {

            if(queryParameters != null && ! queryParameters.isEmpty())
                parameters = "?"+queryParameters+"page="+page+"&per_page="+pageSize;
            else
                parameters = "?page="+page+"&per_page="+pageSize;

            String query = GIT_HUB_API_URL +"/repos/"+repoFullName+"/pulls"+parameters;

            System.out.println("Getting Next Pull Info: "+query);

            RestTemplate restTemplate = new RestTemplate();

            HttpEntity entity = new HttpEntity(getDefaultHeaders());

            result = restTemplate.exchange( query, HttpMethod.GET, entity, GitHubPullRequestInfo[].class);

            allPull.addAll(  Arrays.asList(result.getBody()) );

            page++;


        }while ( result != null && result.getBody().length > 0   && !testEnvironment);

        return allPull;
    }


    /**
     * Return a specific PR of a project
     *
     * @param repoFullName
     * @return
     * @deprecated use {@link #fetchPullRequest(String, int)}
     */
    @Deprecated(since = "3.0")
    public GitHubPullRequestInfo pullRequest(String repoFullName, int pullNumber) {

        validateRepoName(repoFullName);

        ResponseEntity<GitHubPullRequestInfo> result;

        String query = GIT_HUB_API_URL +"/repos/"+repoFullName+"/pulls/"+pullNumber;

        System.out.println("Getting Pull Info: "+query);

        RestTemplate restTemplate = new RestTemplate();

        HttpEntity entity = new HttpEntity(getDefaultHeaders());

        result = restTemplate.exchange( query, HttpMethod.GET, entity, GitHubPullRequestInfo.class);

        return result.getBody();
    }
    
    /**
     * Return all PR between dates of a project
     *
     * @param repoFullName
     * @param start
     * @param end
     * @return
     * @deprecated use {@link #fetchPullRequestsCreatedInPeriod(String, LocalDateTime, LocalDateTime)}
     */
    @Deprecated(since = "3.0")
    public List<GitHubPullRequestInfo> pullRequestsCreatedInPeriod(String repoFullName, LocalDateTime start, LocalDateTime end) {
        
        List<GitHubPullRequestInfo> pullRequests = new ArrayList();
        
        List<GitHubPullRequestInfo> allPullRequests = this.pullRequests(repoFullName);

        DateUtils dateUtils = new DateUtils();

        for (GitHubPullRequestInfo pr : allPullRequests) {

            if (pr.created_at != null) {
                LocalDateTime createdDate = LocalDateTime.ofInstant(pr.created_at.toInstant(), ZoneId.systemDefault());
                if ( dateUtils.isBetweenDates(createdDate, start, end) ) {
                    pullRequests.add(pr);
                }
            }
        }

        return pullRequests;
    }


    /**
     * @deprecated use {@link #fetchPullRequestsClosedInPeriod(String, LocalDateTime, LocalDateTime)}
     */
    @Deprecated(since = "3.0")
    public List<GitHubPullRequestInfo> pullRequestsClosedInPeriod(String repoFullName, LocalDateTime start, LocalDateTime end) {

        List<GitHubPullRequestInfo> pullRequests = new ArrayList();

        List<GitHubPullRequestInfo> allPullRequests = this.pullRequests(repoFullName);

        DateUtils dateUtils = new DateUtils();

        for (GitHubPullRequestInfo pr : allPullRequests) {

            if (pr.closed_at != null) {
                LocalDateTime closedDate = LocalDateTime.ofInstant(pr.closed_at.toInstant(), ZoneId.systemDefault());
                if ( dateUtils.isBetweenDates(closedDate, start, end) ) {
                    pullRequests.add(pr);
                }
            }
        }

        return pullRequests;
    }


    /**
     * Lists the merged pull request that introduced the commit to the repository.
     * If the commit is not present in the default branch, will only return open pull requests associated with the commit.
     *
     * https://api.github.com/repos/traPtitech/traQ/commits/43b707020d19fe6bc8dce8b3fdb0e2e0f367b6c7/pulls
     *
     * @param repoFullName
     * @param sha
     * @return
     * @deprecated use {@link #fetchPullRequestsAssociatedWithCommit(String, String)}
     */
    @Deprecated(since = "3.0")
    public List<GitHubPullRequestInfo> listPullRequestsAssociatedwithCommit(String repoFullName, String sha) {

        validateRepoName(repoFullName);

        int page = 1;

        // IMPORTANTE state=all for bring all PR
        String parameters = "";

        List<GitHubPullRequestInfo> allPull = new ArrayList<>();

        ResponseEntity<GitHubPullRequestInfo[]> result;

        List<Long> idsPullsResult = new ArrayList<>();

        whileloop:
        do {

            if(queryParameters != null && ! queryParameters.isEmpty())
                parameters = "?"+queryParameters+"page="+page+"&per_page="+pageSize;
            else
                parameters = "?page="+page+"&per_page="+pageSize;

            String query = GIT_HUB_API_URL +"/repos/"+repoFullName+"/commits/"+sha+"/pulls"+parameters;

            System.out.println("Getting Next Pull Info: "+query);

            RestTemplate restTemplate = new RestTemplate();

            HttpEntity entity = new HttpEntity(getDefaultHeaders());

            result = restTemplate.exchange( query, HttpMethod.GET, entity, GitHubPullRequestInfo[].class);

            List<GitHubPullRequestInfo> tempList = Arrays.asList(result.getBody());

            for (GitHubPullRequestInfo info : tempList) {
                if( ! idsPullsResult.contains(info.id)) {
                    idsPullsResult.add(info.id);
                }else{
                    break whileloop;
                }
            }

            allPull.addAll(  tempList );

            page++;


        }while ( result != null && result.getBody().length > 0   && !testEnvironment);

        return allPull;
    }


    
//    /**
//     * Verify the minimum number of PR for a projects.
//     *
//     * @deprecated  @see {@link this#getQtdPullRequests(String)}
//     *
//     * @param repoFullName
//     * @param limit
//     * @return
//     */
//    @Deprecated
//    public boolean hasMinimumPullRequest(String repoFullName, final int limit) {
//
//        validateRepoName(repoFullName);
//
//        System.out.println("Counting Pull Requests for : "+repoFullName);
//
//        long totalPROfProject = 0l;
//
//        int page = 1;
//        int perPage = 50;
//
//        // IMPORTANTE state=all for bring all PR
//        // gitHubClient.setQueryParameters(new String[]{"state=all"});
//        String parameters = "";
//
//        ResponseEntity<GitHubPullRequestInfo[]> result;
//
//        do {
//
//            if(queryParameters != null && ! queryParameters.isEmpty())
//                parameters = "?"+queryParameters+"page="+page+"&per_page="+pageSize;
//            else
//                parameters = "?page="+page+"&per_page="+pageSize;
//
//            String query = GIT_HUB_API_URL +"/repos/"+repoFullName+"/pulls"+parameters;
//
//            System.out.println("Getting Next Pull Info: "+query);
//
//            RestTemplate restTemplate = new RestTemplate();
//
//            HttpEntity entity = new HttpEntity(getDefaultHeaders());
//
//            result = restTemplate.exchange( query, HttpMethod.GET, entity, GitHubPullRequestInfo[].class);
//
//            totalPROfProject += Arrays.asList(result.getBody()).size();
//
//            page++;
//
//        }while (result != null && result.getBody().length > 0 && totalPROfProject < limit );
//
//        System.out.println("project: "+repoFullName+" total of PR: "+totalPROfProject);
//
//        return totalPROfProject >= limit;
//    }

    /**
     * Return the qtd of PULL Request of a project
     *
     * https://stackoverflow.com/questions/13094712/github-api-get-number-of-pull-requests
     * https://docs.github.com/en/free-pro-team@latest/github/searching-for-information-on-github/searching-issues-and-pull-requests
     *
     * @param repoFullName
     * @return
     * @deprecated use {@link #fetchQtdPullRequests(String)}
     */
    @Deprecated(since = "3.0")
    public int getQtdPullRequests(String repoFullName) {

        validateRepoName(repoFullName);


        String query = GIT_HUB_API_URL +"/search/issues?q=type:pr+repo:"+repoFullName+"&sort=created&order=asc";

        // allow '+' character in URL
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(query);
        URI uri = builder.build(false).toUri();


        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = getDefaultHeaders();
        headers.set("Accept", "application/vnd.github.v3+json");
        HttpEntity entity = new HttpEntity(headers);

        ResponseEntity<GitHubQTDPullRequestInfo> result = restTemplate.exchange( uri, HttpMethod.GET, entity, GitHubQTDPullRequestInfo.class);

        return result.getBody().total_count;
    }

    // @HttpExchange methods: pages are fetched in parallel on virtual threads

    public List<GitHubPullRequestInfo> fetchPullRequests(String repoOwner, String repoName) {
        return fetchPullRequests(repoOwner + "/" + repoName);
    }

    /**
        Return all PRs of a project. Pages after the first one are fetched in parallel.

        Use setQueryParameters(new String[]{"state=all"}) to bring closed PRs too.
     */
    public List<GitHubPullRequestInfo> fetchPullRequests(String repoFullName) {
        String owner = owner(repoFullName), name = name(repoFullName);
        PageParams params = pageParams();

        System.out.println("Fetching pull requests of " + repoFullName);
        return fetchAllPages(page -> clients().rest().listPullRequests(owner, name, params.forPage(page)));
    }

    // Return a specific PR of a project
    public GitHubPullRequestInfo fetchPullRequest(String repoFullName, int pullNumber) {
        return clients().rest().getPullRequest(owner(repoFullName), name(repoFullName), pullNumber);
    }

    // Return the PRs created between the dates (inclusive)
    public List<GitHubPullRequestInfo> fetchPullRequestsCreatedInPeriod(String repoFullName, LocalDateTime start, LocalDateTime end) {
        return filterByDate(fetchPullRequests(repoFullName), pr -> pr.created_at, start, end);
    }

    // Return the PRs closed between the dates (inclusive)
    public List<GitHubPullRequestInfo> fetchPullRequestsClosedInPeriod(String repoFullName, LocalDateTime start, LocalDateTime end) {
        return filterByDate(fetchPullRequests(repoFullName), pr -> pr.closed_at, start, end);
    }

    /**
        Lists the merged pull request that introduced the commit to the repository.
        If the commit is not present in the default branch, will only return open pull requests associated with the commit.

        https://docs.github.com/en/rest/commits/commits?apiVersion=2022-11-28#list-pull-requests-associated-with-a-commit
     */
    public List<GitHubPullRequestInfo> fetchPullRequestsAssociatedWithCommit(String repoFullName, String sha) {
        String owner = owner(repoFullName), name = name(repoFullName);
        PageParams params = pageParams();

        List<GitHubPullRequestInfo> all = fetchAllPages(page -> clients().rest().listPullRequestsOfCommit(owner, name, sha, params.forPage(page)));

        // the old method stopped on a repeated PR, here repeated PRs are removed
        Map<Long, GitHubPullRequestInfo> byId = new LinkedHashMap<>();
        for (GitHubPullRequestInfo pr : all)
            byId.putIfAbsent(pr.id, pr);
        return new ArrayList<>(byId.values());
    }

    /**
        Return the qtd of PULL Request of a project, using the search API (it has its own rate limit, 30 requests per minute).

        https://docs.github.com/en/search-github/searching-on-github/searching-issues-and-pull-requests
     */
    public int fetchQtdPullRequests(String repoFullName) {
        return clients().rest().searchIssues("type:pr repo:" + owner(repoFullName) + "/" + name(repoFullName), 1).total_count;
    }

}
