/*
 * Federal University of Rio Grande do Norte
 * Department of Informatics and Applied Mathematics
 * Collaborative & Automated Software Engineering (CASE) Research Group
 *
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
 * br.com.jadson.snooper.github.operations
 * GitHubCommitExecutor
 * 26/01/21
 */
package br.com.jadson.snooper.github.operations;

import br.com.jadson.snooper.github.client.GitHubGraphQLApi;
import br.com.jadson.snooper.github.data.GraphQLError;
import br.com.jadson.snooper.github.data.association.AssociationCommitPullRequestInfo;
import br.com.jadson.snooper.github.data.association.PullRequestNodeInfo;
import br.com.jadson.snooper.github.data.association.graphql.AssociatedPullRequestsEdge;
import br.com.jadson.snooper.github.data.association.graphql.CommitNode;
import br.com.jadson.snooper.github.data.association.graphql.ResultGraphQLRepository;
import br.com.jadson.snooper.github.data.commit.GitHubFileChanged;
import br.com.jadson.snooper.github.data.commit.GitHubCommitInfo;
import br.com.jadson.snooper.github.data.stats.GitHubFileStats;
import br.com.jadson.snooper.github.data.stats.GitHubCommitStatsInfo;
import br.com.jadson.snooper.github.data.stats.graphql.CommitStatsNode;
import br.com.jadson.snooper.github.data.stats.graphql.GraphQLCommitResponse;
import br.com.jadson.snooper.github.data.stats.graphql.History;
import br.com.jadson.snooper.github.data.stats.mapper.GitHubCommitStatsMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Callable;

/**
 * Executes queries of commits
 *
 * Jadson Santos - jadsonjs@gmail.com
 */
public class CommitQueryExecutor extends AbstractGitHubQueryExecutor {


    /**
     * Return all commits of a project
     *
     * Get the commits between dates with the param: since=2021-03-01T22:26:45Z&until=2021-04-08T22:26:45Z&page=1&per_page=50
     *
     * @param repoFullName
     * @return
     * @deprecated use {@link #fetchCommits(String)}, which fetches the pages in parallel
     */
    @Deprecated(since = "3.0")
    public List<GitHubCommitInfo> getCommits(String repoFullName) {

        validateRepoName(repoFullName);

        int page = 1;

        // IMPORTANTE state=all for bring all PR
        String parameters = "";

        List<GitHubCommitInfo> allPull = new ArrayList<>();

        ResponseEntity<GitHubCommitInfo[]> result;

        do {

            if(queryParameters != null && ! queryParameters.isEmpty())
                parameters = "?"+queryParameters+"page="+page+"&per_page="+pageSize;
            else
                parameters = "?page="+page+"&per_page="+pageSize;

            String query = GIT_HUB_API_URL +"/repos/"+repoFullName+"/commits"+parameters;

            System.out.println("Getting Next Commit: "+query);

            RestTemplate restTemplate = new RestTemplate();

            HttpEntity entity = new HttpEntity(getDefaultHeaders());

            result = restTemplate.exchange( query, HttpMethod.GET, entity, GitHubCommitInfo[].class);

            allPull.addAll(  Arrays.asList(result.getBody()) );

            page++;


        }while ( result != null && result.getBody().length > 0   && !testEnvironment);

        return allPull;
    }



    /**
     * Return all commits of a reference (can be a branch or tag)
     *
     * More information at: https://docs.github.com/en/rest/commits/commits?apiVersion=2022-11-28#get-a-commit
     *
     * Exemple:
     * https://api.github.com/repos/traPtitech/traQ/commits/master?page=1&per_page=1
     *
     * @param repoFullName
     * @param ref a branch or a tag name
     * @return all commits form a branch or tag
     * @deprecated use {@link #fetchCommitOfReference(String, String)}
     */
    @Deprecated(since = "3.0")
    public GitHubCommitInfo getCommitsOfReference(String repoFullName, String ref) {

        validateRepoName(repoFullName);

        int page = 1;

        String parameters = "";

        GitHubCommitInfo gitHubCommitInfo = null;

        ResponseEntity<GitHubCommitInfo> result;

       // do {

            if(queryParameters != null && ! queryParameters.isEmpty())
                parameters = "?"+queryParameters+"page="+page+"&per_page="+pageSize;
            else
                parameters = "?page="+page+"&per_page="+pageSize;

            String query = GIT_HUB_API_URL +"/repos/"+repoFullName+"/commits"+"/"+ref+parameters;

            System.out.println("Getting Next Commit: "+query);

            RestTemplate restTemplate = new RestTemplate();

            HttpEntity entity = new HttpEntity(getDefaultHeaders());

            result = restTemplate.exchange( query, HttpMethod.GET, entity, GitHubCommitInfo.class);

            gitHubCommitInfo = result.getBody();

            page++;


      //  }while ( result != null && result.getBody() != null   && !testEnvironment);

        return gitHubCommitInfo;
    }


    /**
     * Return all commits associated with the PR
     *
     * @param repoFullName
     * @return
     * @deprecated use {@link #fetchCommitsOfPullRequest(String, long)}, which fetches the pages in parallel
     */
    @Deprecated(since = "3.0")
    public List<GitHubCommitInfo> commitsOfPullRequest(String repoFullName, long prNumber) {

        validateRepoName(repoFullName);

        int page = 1;

        // IMPORTANTE state=all for bring all PR
        String parameters = "";

        List<GitHubCommitInfo> allPull = new ArrayList<>();

        ResponseEntity<GitHubCommitInfo[]> result;

        do {

            if(queryParameters != null && ! queryParameters.isEmpty())
                parameters = "?"+queryParameters+"page="+page+"&per_page="+pageSize;
            else
                parameters = "?page="+page+"&per_page="+pageSize;

            String query = GIT_HUB_API_URL +"/repos/"+repoFullName+"/pulls/"+prNumber+"/commits"+parameters;

            System.out.println("Getting Next Commit of PULL REQUEST: "+query);

            RestTemplate restTemplate = new RestTemplate();

            HttpEntity entity = new HttpEntity(getDefaultHeaders());

            result = restTemplate.exchange( query, HttpMethod.GET, entity, GitHubCommitInfo[].class);

            allPull.addAll(  Arrays.asList(result.getBody()) );

            page++;


        }while ( result != null && result.getBody().length > 0   && !testEnvironment);

        return allPull;
    }








    /**
     * Return the history of commits of a project with the associated PULL REQUEST to this commits
     *
     * This method use the API V4 of github with GraphQL
     * @param projectFullName
     * @return
     * @deprecated use {@link #fetchHistoryOfCommitsWithPullRequests(String)}
     */
    @Deprecated(since = "3.0")
    public List<AssociationCommitPullRequestInfo> getHistoryOfCommitsWithPullRequestsQuery(String projectFullName) {

        final int pageSize = 100;

        String afterPosition = "";

        List<AssociationCommitPullRequestInfo> results = new ArrayList<>();


        System.out.println(" executing AssociatedPullRequestsQuery for " + projectFullName + "  ");

        boolean hasNextPage = true;

        String names[] = projectFullName.split("/");

        String[] symbols = new String[]{"|", "/", "-", "|", "/", "-", "\\"};

        int index = 0;
        int symbolsIndex = 0;

        while (hasNextPage && index <= 100) {   // for all commits in project

            System.out.println(" executing next commit page  "+symbols[symbolsIndex]);
            symbolsIndex =  ( symbolsIndex == 6 ? 0 : symbolsIndex+1);

            /**
             * return the commit history for a project with associated pull requests to it.
             *
             * {
             *     "query":  " query {  repository(owner:\"octocat\", name:\"Hello-World\") { object(expression: \"master\") { ... on Commit { history(first:10) { pageInfo {  hasNextPage,  endCursor  },  nodes {  commitUrl  associatedPullRequests(first:20){ edges {  node {  id  number  } } } } }  } } }  } "
             * }
             */
            String query = "   " +
                    " repository(owner: " +"\\\""+ names[0] +"\\\""+ ", name: " +"\\\""+ names[1] +"\\\""+ ") { " +
                    "   object(expression: \\\"master\\\") { " +
                    "     ... on Commit { " +
                    "         history(first:" + pageSize +" "+afterPosition+") { " +
                    "             pageInfo {  " +
                    "                 hasNextPage,  " +
                    "                 endCursor  " +
                    "             },  " +
                    "             nodes {  " +
                    "                 commitUrl  " +   /// commmit hash
                    "                 associatedPullRequests(first:20){  " +
                    "                     edges {  " +
                    "                         node {  " +
                    "                             id  " +
                    "                             number  " +   // the number of PR of the commit
                    "                         } " +
                    "                     } " +
                    "                 } " +
                    "             } " +
                    "         }  " +
                    "     }  " +
                    "   } " +
                    " } " ;


            /**
             * Convert the result of query to a object simplification and return it
             */
            ResultGraphQLRepository queryResult = executeQueryAssociatedPR(query);

            if( queryResult != null && queryResult.data.repository.object != null ) {
                if(queryResult.data.repository.object.history != null) {
                    for (CommitNode commitNode : queryResult.data.repository.object.history.nodes) {
                        AssociationCommitPullRequestInfo association = new AssociationCommitPullRequestInfo();
                        association.commitUrl = commitNode.commitUrl;
                        for (AssociatedPullRequestsEdge a1 : commitNode.associatedPullRequests.edges) {
                            association.addPullRequestInfo(new PullRequestNodeInfo(a1.node.id, a1.node.number));
                        }
                        results.add(association);
                    }
                }
            }

            if ( queryResult == null || queryResult.data.repository.object == null || ! queryResult.data.repository.object.history.pageInfo.hasNextPage) {
                System.out.println("!!! End query has no more pages !!!");
                hasNextPage = false;
            }else {
                afterPosition = ", after:\\\"" + queryResult.data.repository.object.history.pageInfo.endCursor + "\\\" ";
                hasNextPage = true;
            }

            if(index == 100)
                System.out.println("!!! End query limit 100 requests achieved !!!");

            index++;

        } // while

        return results;
    }

    /**
     * Return the history of commits of a GitHub project with their change statistics.
     *
     * This method uses the API V4 of GitHub with GraphQL.
     *
     * @param projectFullName
     * @param sinceDate
     * @param untilDate
     * @return
     * @deprecated use {@link #fetchCommitsWithStats(String, LocalDateTime, LocalDateTime)}, which fetches date windows in parallel
     */
    @Deprecated(since = "3.0")
    public List<GitHubCommitStatsInfo> getCommitsWithStats(String projectFullName, LocalDateTime sinceDate, LocalDateTime untilDate) {
        validateRepoName(projectFullName);

        List<GitHubCommitStatsInfo> allCommits = new ArrayList<>();
        String[] repoParts = projectFullName.split("/");
        String owner = repoParts[0];
        String repo = repoParts[1];

        String since = sinceDate.toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
        String until = untilDate.plusDays(1).toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);

        String cursor = null;
        boolean hasNextPage = true;

        System.out.println("Executing getCommitsWithStats for " + projectFullName + " from " + since + " to " + until);

        while (hasNextPage) {
            String afterClause = (cursor == null) ? "" : ", after: \\\"" + cursor + "\\\"";

            String query = String.format(""+
                    "repository(owner: \\\"%s\\\", name: \\\"%s\\\") {"+
                    "  defaultBranchRef {"+
                    "    target {"+
                    "      ... on Commit {"+
                    "        history(since: \\\"%s\\\", until: \\\"%s\\\", first: 100%s) {"+
                    "          pageInfo {"+
                    "            endCursor"+
                    "            hasNextPage"+
                    "          }"+
                    "          nodes {"+
                    "            oid "+
                    "            id "+
                    "            url "+
                    "            comments { totalCount } "+
                    "            author { user { url login } name email date avatarUrl } "+
                    "            committer { user { url login } name email date } "+
                    "            additions "+
                    "            deletions "+
                    "            changedFiles: changedFilesIfAvailable "+
                    "            message "+
                    "          }"+
                    "        }"+
                    "      }"+
                    "    }"+
                    "  }"+
                    "}", owner, repo, since, until, afterClause);

            GraphQLCommitResponse queryResult = executeCommitStatsQuery(query);

            if (queryResult != null && queryResult.data != null && queryResult.data.repository != null &&
                    queryResult.data.repository.defaultBranchRef != null && queryResult.data.repository.defaultBranchRef.target != null &&
                    queryResult.data.repository.defaultBranchRef.target.history != null) {

                for (CommitStatsNode node : queryResult.data.repository.defaultBranchRef.target.history.nodes) {
                    GitHubCommitStatsInfo statsInfo = GitHubCommitStatsMapper.mapToCommitStatsInfo(node);
                    allCommits.add(statsInfo);
                }

                hasNextPage = queryResult.data.repository.defaultBranchRef.target.history.pageInfo.hasNextPage;
                cursor = queryResult.data.repository.defaultBranchRef.target.history.pageInfo.endCursor;
                System.out.println("Page fetched. Commits so far: " + allCommits.size() + ". Has next page: " + hasNextPage);

            } else {
                System.err.println("!!! No more data or error in GraphQL response. Stopping pagination. !!!");
                hasNextPage = false;
            }
        }

        return allCommits;
    }

    /**
     * Return the list of files changed in a specific GitHub commit.
     *
     * This method uses the REST API of GitHub.
     *
     * @param repoFullName
     * @param commit
     * @return
     * @deprecated use {@link #fetchCommitFiles(String, GitHubCommitInfo)}, or {@link #fetchCommitFiles(String, List)} for many commits in parallel
     */
    @Deprecated(since = "3.0")
    public List<GitHubFileChanged> getCommitFiles(String repoFullName, GitHubCommitInfo commit){
        validateRepoName(repoFullName);
        // IMPORTANTE state=all for bring all PR
        String parameters = "";
        ResponseEntity<GitHubCommitInfo> result;

        String query = GIT_HUB_API_URL +"/repos/"+repoFullName+"/commits/"+commit.sha;

        System.out.println("Getting files for commit");

        RestTemplate restTemplate = new RestTemplate();

        HttpEntity entity = new HttpEntity(getDefaultHeaders());

        result = restTemplate.exchange( query, HttpMethod.GET, entity, GitHubCommitInfo.class);

        if (result.getBody() != null && result.getBody().files != null) {
            return result.getBody().files;
        }

        return new ArrayList<>();
    }

    /**
     * Return the change statistics of a specific file in a GitHub repository.
     *
     * This method uses the API V4 of GitHub with GraphQL.
     *
     * @param projectFullName
     * @param filePath
     * @param sinceDate
     * @param untilDate
     * @return
     * @deprecated use {@link #fetchFileStats(String, String, LocalDateTime, LocalDateTime)}
     */
    @Deprecated(since = "3.0")
    public GitHubFileStats getFileStats(String projectFullName, String filePath, LocalDateTime sinceDate, LocalDateTime untilDate){
        validateRepoName(projectFullName);

        String[] repoParts = projectFullName.split("/");
        String owner = repoParts[0];
        String repo = repoParts[1];

        String since = sinceDate.toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
        String until = untilDate.plusDays(1).toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);

        System.out.println("Executing getFileStats for " + filePath + " from " + since + " to " + until);

        String query = String.format(
                "repository(owner: \\\"%s\\\", name: \\\"%s\\\") { " +
                        "  defaultBranchRef { " +
                        "    target { " +
                        "      ... on Commit { " +
                        "        history(path: \\\"%s\\\", since: \\\"%s\\\", until: \\\"%s\\\") { " +
                        "          totalCount " +
                        "        } " +
                        "      } " +
                        "    } " +
                        "  } " +
                        "} ",
                owner, repo, filePath, since, until
        );

        GraphQLCommitResponse queryResult = executeCommitStatsQuery(query);

        GitHubFileStats gitHubFileStats = new GitHubFileStats();
        gitHubFileStats.commits = queryResult.data.repository.defaultBranchRef.target.history.totalCount;
        gitHubFileStats.path = filePath;

        return gitHubFileStats;

    }


    /**
     * Execute a GraphQL query to retrieve commit statistics from GitHub.
     *
     * This method uses the API V4 of GitHub with GraphQL.
     *
     * @param query
     * @return
     */
    private ResultGraphQLRepository executeQueryAssociatedPR(String query)  {

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer "+githubToken+"");
        headers.set("Accept", "application/json");

        String body =  " {    \"query\": \" query {  "+query+"   } \"   } " ;

        ObjectMapper objectMapper = new ObjectMapper();

        HttpEntity<String> request =  new HttpEntity<>(body, headers);

        String json = restTemplate.postForObject("https://api.github.com/graphql", request, String.class);

        ResultGraphQLRepository result = null;
        try {
            result = objectMapper.readValue(json, ResultGraphQLRepository.class);
        } catch (JsonProcessingException e) {
            System.err.println("--------------------JsonProcessingException----------------------------");
            e.printStackTrace();
            System.err.println("-----------------------------------------------------------------------");
        }
        return result;

    }

    private GraphQLCommitResponse executeCommitStatsQuery(String query) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + githubToken);
        headers.set("Accept", "application/json");

        String body = "{\"query\": \"query getCommitsByDateRange {" + query + "}\"}";

        HttpEntity<String> request = new HttpEntity<>(body, headers);

        String jsonResponse = restTemplate.postForObject("https://api.github.com/graphql", request, String.class);

        GraphQLCommitResponse result = null;
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            result = objectMapper.readValue(jsonResponse, GraphQLCommitResponse.class);
        } catch (JsonProcessingException e) {
            System.err.println("----------- JsonProcessingException while parsing commit stats -----------");
            System.err.println("Response JSON: " + jsonResponse);
            e.printStackTrace();
            System.err.println("--------------------------------------------------------------------------");
        }
        return result;
    }

    // @HttpExchange methods: pages and items are fetched in parallel on virtual threads

    private static final int GRAPHQL_PAGE_SIZE = 100;

    private static final String COMMIT_STATS_FIELDS =
            "oid id url comments { totalCount } " +
            "author { user { url login } name email date avatarUrl } " +
            "committer { user { url login } name email date } " +
            "additions deletions changedFiles: changedFilesIfAvailable message";

    private static final String COMMITS_WITH_STATS_QUERY =
            "query($owner: String!, $name: String!, $since: GitTimestamp!, $until: GitTimestamp!, $after: String) { " +
            "  repository(owner: $owner, name: $name) { defaultBranchRef { target { ... on Commit { " +
            "    history(since: $since, until: $until, first: " + GRAPHQL_PAGE_SIZE + ", after: $after) { " +
            "      totalCount pageInfo { endCursor hasNextPage } nodes { " + COMMIT_STATS_FIELDS + " } " +
            "    } " +
            "  } } } } " +
            "}";

    private static final String FILE_STATS_QUERY =
            "query($owner: String!, $name: String!, $path: String!, $since: GitTimestamp!, $until: GitTimestamp!) { " +
            "  repository(owner: $owner, name: $name) { defaultBranchRef { target { ... on Commit { " +
            "    history(path: $path, since: $since, until: $until) { totalCount } " +
            "  } } } } " +
            "}";

    private static final String COMMITS_WITH_PULL_REQUESTS_QUERY =
            "query($owner: String!, $name: String!, $expression: String!, $after: String) { " +
            "  repository(owner: $owner, name: $name) { object(expression: $expression) { ... on Commit { " +
            "    history(first: " + GRAPHQL_PAGE_SIZE + ", after: $after) { " +
            "      pageInfo { hasNextPage endCursor } " +
            "      nodes { commitUrl associatedPullRequests(first: 20) { edges { node { id number } } } } " +
            "    } " +
            "  } } } " +
            "}";

    /**
        Return all commits of a project. Pages after the first one are fetched in parallel.

        Get the commits between dates with setQueryParameters: since=2021-03-01T22:26:45Z, until=2021-04-08T22:26:45Z

        @param repoFullName owner/repo
        @return the commits, newest first
     */
    public List<GitHubCommitInfo> fetchCommits(String repoFullName) {
        String owner = owner(repoFullName), name = name(repoFullName);
        PageParams params = pageParams();

        System.out.println("Fetching commits of " + repoFullName);
        return fetchAllPages(page -> clients().rest().listCommits(owner, name, params.forPage(page)));
    }

    /**
        Return the commit of a reference (a sha, a branch or a tag), with its files.

        https://docs.github.com/en/rest/commits/commits?apiVersion=2022-11-28#get-a-commit
     */
    public GitHubCommitInfo fetchCommitOfReference(String repoFullName, String ref) {
        return clients().rest().getCommit(owner(repoFullName), name(repoFullName), ref, pageParams().forPage(1));
    }

    // Return all commits of a pull request. Pages after the first one are fetched in parallel
    public List<GitHubCommitInfo> fetchCommitsOfPullRequest(String repoFullName, long prNumber) {
        String owner = owner(repoFullName), name = name(repoFullName);
        PageParams params = pageParams();

        return fetchAllPages(page -> clients().rest().listCommitsOfPullRequest(owner, name, prNumber, params.forPage(page)));
    }

    // Return the files changed in a commit
    public List<GitHubFileChanged> fetchCommitFiles(String repoFullName, GitHubCommitInfo commit) {
        GitHubCommitInfo detail = clients().rest().getCommit(owner(repoFullName), name(repoFullName), commit.sha);
        return detail != null && detail.files != null ? detail.files : new ArrayList<>();
    }

    /**
        Return the files changed in each commit, fetching the commits in parallel.

        Each commit costs one request: 5,000 commits use the whole hourly quota of a token.
        The requests wait for the rate limit reset instead of failing.

        @return sha -> changed files, in the order of the commits
     */
    public Map<String, List<GitHubFileChanged>> fetchCommitFiles(String repoFullName, List<GitHubCommitInfo> commits) {
        String owner = owner(repoFullName), name = name(repoFullName);

        List<String> shas = new ArrayList<>();
        for (GitHubCommitInfo commit : commits)
            shas.add(commit.sha);

        System.out.println("Fetching files of " + shas.size() + " commits of " + repoFullName);
        return fetchEach(new LinkedHashSet<>(shas), sha -> {
            GitHubCommitInfo detail = clients().rest().getCommit(owner, name, sha);
            return detail != null && detail.files != null ? detail.files : new ArrayList<GitHubFileChanged>();
        });
    }

    /**
        Return the commits of the default branch with their change statistics (GraphQL).

        The period is split in windows fetched in parallel, each one walking its own cursor.
        Windows overlap on their borders and commits are de-duplicated by sha.

        @return the commits, newest first
     */
    public List<GitHubCommitStatsInfo> fetchCommitsWithStats(String projectFullName, LocalDateTime sinceDate, LocalDateTime untilDate) {
        String owner = owner(projectFullName), name = name(projectFullName);

        Instant since = sinceDate.toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant until = untilDate.plusDays(1).toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC);

        System.out.println("Fetching commits with stats of " + projectFullName + " from " + since + " to " + until);

        // the first page tells how many commits the period has
        History first = commitStatsPage(owner, name, since, until, null);
        if (first == null)
            return new ArrayList<>();

        if (!Boolean.TRUE.equals(first.pageInfo.hasNextPage) || testEnvironment)
            return toStatsInfo(first.nodes);

        int pages = (first.totalCount + GRAPHQL_PAGE_SIZE - 1) / GRAPHQL_PAGE_SIZE;
        int windows = Math.max(1, Math.min(pages, clients().fetcher().getMaxConcurrency()));

        // newest window first, so the result keeps the history order
        Duration windowSize = Duration.between(since, until).dividedBy(windows);
        List<Callable<List<CommitStatsNode>>> tasks = new ArrayList<>();
        for (int i = windows - 1; i >= 0; i--) {
            Instant windowSince = since.plus(windowSize.multipliedBy(i));
            Instant windowUntil = i == windows - 1 ? until : since.plus(windowSize.multipliedBy(i + 1));
            tasks.add(() -> allCommitStats(owner, name, windowSince, windowUntil));
        }

        Map<String, CommitStatsNode> bySha = new LinkedHashMap<>();
        for (List<CommitStatsNode> window : clients().fetcher().fetchAll(tasks))
            for (CommitStatsNode node : window)
                bySha.putIfAbsent(node.oid, node);

        return toStatsInfo(new ArrayList<>(bySha.values()));
    }

    // Return how many commits changed a file in the period (GraphQL)
    public GitHubFileStats fetchFileStats(String projectFullName, String filePath, LocalDateTime sinceDate, LocalDateTime untilDate) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("owner", owner(projectFullName));
        variables.put("name", name(projectFullName));
        variables.put("path", filePath);
        variables.put("since", sinceDate.toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC).toString());
        variables.put("until", untilDate.plusDays(1).toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC).toString());

        GraphQLCommitResponse response = clients().graphQL().queryCommitStats(new GitHubGraphQLApi.Request(FILE_STATS_QUERY, variables));
        GraphQLError.throwIfAny(response.errors);

        GitHubFileStats gitHubFileStats = new GitHubFileStats();
        gitHubFileStats.path = filePath;
        gitHubFileStats.commits = historyOf(response) != null ? historyOf(response).totalCount : 0;
        return gitHubFileStats;
    }

    /**
        Return the history of commits of the default branch with the pull requests associated to each commit (GraphQL).

        Unlike getHistoryOfCommitsWithPullRequestsQuery, it reads the default branch (not "master")
        and has no limit of 100 pages. The cursor is sequential, so pages are not fetched in parallel.
     */
    public List<AssociationCommitPullRequestInfo> fetchHistoryOfCommitsWithPullRequests(String projectFullName) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("owner", owner(projectFullName));
        variables.put("name", name(projectFullName));
        variables.put("expression", "HEAD");

        List<AssociationCommitPullRequestInfo> results = new ArrayList<>();
        String cursor = null;

        do {
            variables.put("after", cursor);
            ResultGraphQLRepository response = clients().graphQL()
                    .queryAssociatedPullRequests(new GitHubGraphQLApi.Request(COMMITS_WITH_PULL_REQUESTS_QUERY, variables));
            GraphQLError.throwIfAny(response.errors);

            if (response.data == null || response.data.repository == null || response.data.repository.object == null
                    || response.data.repository.object.history == null)
                break;

            br.com.jadson.snooper.github.data.association.graphql.History history = response.data.repository.object.history;
            for (CommitNode commitNode : history.nodes) {
                AssociationCommitPullRequestInfo association = new AssociationCommitPullRequestInfo();
                association.commitUrl = commitNode.commitUrl;
                for (AssociatedPullRequestsEdge edge : commitNode.associatedPullRequests.edges)
                    association.addPullRequestInfo(new PullRequestNodeInfo(edge.node.id, edge.node.number));
                results.add(association);
            }

            cursor = Boolean.TRUE.equals(history.pageInfo.hasNextPage) && !testEnvironment ? history.pageInfo.endCursor : null;
        } while (cursor != null);

        return results;
    }

    // Walks the cursor of one window until its last page
    private List<CommitStatsNode> allCommitStats(String owner, String name, Instant since, Instant until) {
        List<CommitStatsNode> nodes = new ArrayList<>();
        String cursor = null;
        do {
            History history = commitStatsPage(owner, name, since, until, cursor);
            if (history == null)
                break;
            nodes.addAll(history.nodes);
            cursor = Boolean.TRUE.equals(history.pageInfo.hasNextPage) ? history.pageInfo.endCursor : null;
        } while (cursor != null);
        return nodes;
    }

    private History commitStatsPage(String owner, String name, Instant since, Instant until, String cursor) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("owner", owner);
        variables.put("name", name);
        variables.put("since", since.toString());
        variables.put("until", until.toString());
        variables.put("after", cursor);

        GraphQLCommitResponse response = clients().graphQL().queryCommitStats(new GitHubGraphQLApi.Request(COMMITS_WITH_STATS_QUERY, variables));
        GraphQLError.throwIfAny(response.errors);
        return historyOf(response);
    }

    private static History historyOf(GraphQLCommitResponse response) {
        if (response == null || response.data == null || response.data.repository == null
                || response.data.repository.defaultBranchRef == null || response.data.repository.defaultBranchRef.target == null)
            return null;
        return response.data.repository.defaultBranchRef.target.history;
    }

    private static List<GitHubCommitStatsInfo> toStatsInfo(List<CommitStatsNode> nodes) {
        List<GitHubCommitStatsInfo> result = new ArrayList<>();
        for (CommitStatsNode node : nodes)
            result.add(GitHubCommitStatsMapper.mapToCommitStatsInfo(node));
        return result;
    }

}


