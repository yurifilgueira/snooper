# Snooper

Snooper is a project to miner data from APIs of repositories like GitHub.

Until the moment, these APIs are supported by Snooper
  - **GitHub API**: https://docs.github.com/pt/rest
  - **GitHub Actions API**: https://docs.github.com/en/rest/actions
  - **Sonar Cloud API**: https://sonarcloud.io/web_api
  - **CodeCov API**: https://docs.codecov.com/reference/authorization
  - **Coveralls API**: https://docs.coveralls.io/api-introduction
  - **TravisCI API**: https://docs.travis-ci.com/api/


<img src="https://github.com/jadsonjs/snooper/blob/master/snooper-and-blabber.png" width="800">
@copyright hanna barbera

#### Versions: 

 - 1.0 - Github Search
 - 1.8 - CodeCov Support
 - 2.0 - Github Actions and Coveralls Support
 - 3.0 - Concurrent GitHub mining with virtual threads and `@HttpExchange` clients (requires Java 21)

#### Authors:

    Jadson Santos - jadsonjs@gmail.com
    
    
### Dependencies
    
    Java 21
    Gradle 9.1 (wrapper included, runs on JDK 17+ and compiles with a JDK 21 toolchain)
    Spring Web 6.2 (RestClient and @HttpExchange)
    Junit 5.11
    
### How do I get set up?

#### From the source code:

   Clone the project -> Import it as a gradle project on your IDE.

#### From the binary:

   Snooper has a binary distribution on **libs/snooper-X.Y.jar** directory.
   
   Include it on your classpath.    
    

### Concurrent mining (3.0)

Since version 3.0 the GitHub executors have `fetch*` methods. They do the same queries as the old methods,
but fetch pages and items in parallel on virtual threads, so mining a large repository is limited by the GitHub rate limits
instead of by the time of each request.

 - **Pages in parallel**: the first page is fetched, the `Link` header tells how many pages exist, and the other pages are fetched at the same time.
 - **Batch methods**: `fetchCommitFiles`, `fetchPullRequestDiffs`, `fetchPullRequestComments`, `fetchIssueComments`,
   `fetchRepoInfos` and `fetchUsers` receive a list and fetch one item per request in parallel. They return a `Map` in the order of the input.
 - **Commits with stats (GraphQL)**: `fetchCommitsWithStats` splits the period in date windows fetched in parallel.
 - **Concurrency limit**: at most 10 requests of the same token run at the same time, no matter how many executors or repositories are being mined.
 - **Rate limit**: when the quota of the token is almost over, requests wait for the reset time of GitHub.
   Secondary limits (403/429) are retried after `Retry-After`. Core, search and GraphQL quotas are tracked separately.
 - **One client per token**: executors with the same token share the HTTP connections, the rate limit and the concurrency limit.
   A token that receives 401 (revoked or expired) is removed from the cache.

```
    // change the concurrency limit of a token (default 10)
    GitHubClientFactory.forToken(githubToken).fetcher().setMaxConcurrency(20);

    // remove the clients of a token (after changing it, for example)
    GitHubClientFactory.evict(githubToken);
    GitHubClientFactory.clear();

    // mine several repositories at the same time with the same token
    try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
        for (String repo : List.of("jadsonjs/snooper", "spring-projects/spring-framework")) {
            pool.submit(() -> {
                CommitQueryExecutor executor = new CommitQueryExecutor();
                executor.setGithubToken(githubToken);
                return executor.fetchCommits(repo);
            });
        }
    }
```

A token has 5,000 REST requests per hour. Fetching the files of each commit costs one request per commit,
so the files of 5,000 commits use the whole hourly quota (the requests wait for the reset instead of failing).
To read only the commit history of a big repository, cloning it with `CloneGitHubExecutor` and reading it locally is faster and uses no quota.

#### Migrating from 2.x

The old GitHub methods still work but are `@Deprecated(since = "3.0")`. Each one points to its replacement:

| 2.x (deprecated)                                  | 3.0                                                   |
|---------------------------------------------------|-------------------------------------------------------|
| `CommitQueryExecutor.getCommits`                  | `fetchCommits`                                        |
| `CommitQueryExecutor.getCommitsWithStats`         | `fetchCommitsWithStats`                               |
| `CommitQueryExecutor.getCommitFiles`              | `fetchCommitFiles` (one commit or a list of commits)  |
| `CommitQueryExecutor.getHistoryOfCommitsWithPullRequestsQuery` | `fetchHistoryOfCommitsWithPullRequests` (default branch instead of `master`) |
| `PullRequestQueryExecutor.pullRequests`           | `fetchPullRequests`                                   |
| `PullRequestDiffQueryExecutor.pullRequestsDiff`   | `fetchPullRequestDiff` / `fetchPullRequestDiffs`      |
| `IssueQueryExecutor.issues`                       | `fetchIssues`                                         |
| `IssueCommentsQueryExecutor.getIssueCommentsInfo` | `fetchIssueComments`                                  |
| `PullRequestCommentsQueryExecutor.getPullCommentsInfo` | `fetchPullRequestComments`                       |
| `LabelQueryExecutor.labels`                       | `fetchLabels`                                         |
| `ReleaseQueryExecutor.releases`                   | `fetchReleases`                                       |
| `RepoQueryExecutor.getRepoInfo` / `getAllFiles`   | `fetchRepoInfo` / `fetchRepoInfos` / `fetchAllFiles`  |
| `UserQueryExecutor.user`                          | `fetchUser` / `fetchUsers`                            |

The other methods follow the same pattern (`getQtdPullRequests` -> `fetchQtdPullRequests`, `issuesCreatedInPeriod` -> `fetchIssuesCreatedInPeriod`, ...).

Other differences of the new methods:
 - GraphQL errors throw `IllegalStateException` with the message of GitHub (the old methods failed with `NullPointerException`).
 - The comments methods use the parameters of `setQueryParameters` (the old ones ignored them).
 - `fetchPullRequestDiff` reads `/pulls/{number}`, the endpoint that returns the diff statistics.
 - GitHub Actions, GitLab, Sonar Cloud, CodeCov, Coveralls and Travis CI executors were not changed yet.


### How to use

Examples of how to use:

```

#############################################
#####              GitHub               #####
#############################################


# download the repository from github to local machine
    
    DownloadGitHubExecutor executor = new DownloadGitHubExecutor();
    String localRepo = executor.download("jadsonjs/snooper", "/tmp");


# Clone the repository from github to local machine
    
    CloneGitHubExecutor executor = new CloneGitHubExecutor(githubToken);
    String localRepo = executor.clone("jadsonjs/snooper", "/tmp");
    

# Get all Commits of a repository

     CommitQueryExecutor executor = new CommitQueryExecutor();
     executor.setGithubToken(githubToken);
     executor.setPageSize(100);
     List<GitHubCommitInfo> commits = executor.fetchCommits("jadsonjs/snooper");


# Get the files changed by each commit (one request per commit, in parallel)

     Map<String, List<GitHubFileChanged>> filesBySha = executor.fetchCommitFiles("jadsonjs/snooper", commits);


# Get the commits with additions, deletions and changed files between dates (GraphQL)

     List<GitHubCommitStatsInfo> stats = executor.fetchCommitsWithStats("jadsonjs/snooper",
             LocalDateTime.of(2023, 1, 1, 0, 0), LocalDateTime.of(2023, 12, 31, 0, 0));


# Get all Pull Requests of a repository

    PullRequestQueryExecutor executor = new PullRequestQueryExecutor(githubToken);
    executor.setPageSize(100);
    executor.setQueryParameters(new String[]{"state=all"});
    
    List<GitHubPullRequestInfo> list =  executor.fetchPullRequests("jadsonjs/snooper");


# Get the review comments of many Pull Requests (in parallel)

    PullRequestCommentsQueryExecutor comments = new PullRequestCommentsQueryExecutor(githubToken);
    Map<Long, List<GithubCommentsInfo>> commentsByPr = comments.fetchPullRequestComments("jadsonjs/snooper", List.of(1L, 2L, 3L));



# Get all Issues of a repository

    IssueQueryExecutor executor = new IssueQueryExecutor(githubToken);
    executor.setPageSize(100);
    executor.setQueryParameters(new String[]{"state=all"});
    
    List<GitHubIssueInfo> list =  executor.fetchIssues("jadsonjs/snooper");
    
    

# Get all Releases of a repository

    ReleaseQueryExecutor executor = new ReleaseQueryExecutor(githubToken);
    executor.setPageSize(100);
    List<GitHubReleaseInfo> list =  executor.fetchReleases("jadsonjs/snooper");



# Get a pull request diff info (additions, deletions, changed files)

    PullRequestDiffQueryExecutor executor = new PullRequestDiffQueryExecutor(githubToken);
    GitHubPullRequestDiffInfo info =  executor.fetchPullRequestDiff("jadsonjs/snooper", 5356L);


# Get the profile of many users (the commit authors, for example) in parallel

    UserQueryExecutor executor = new UserQueryExecutor(githubToken);
    Map<String, GitHubUserInfo> users = executor.fetchUsers(List.of("jadsonjs", "octocat"));



# Search github project of language Java, with 100 stars or more, 1MB or more sort by stars, order by desc

   GitHubSearchExecutor executor = new GitHubSearchExecutor();
   executor.setGithubToken(githubToken);

   // java projects with more the 100 stars and 1MB
   List<GitHubRepoInfo> listOfProjects = search.searchRepositories("Java", 100, 1000, "stars", "desc");   


#############################################
#####          GitHub Actions            #####
#############################################

# list all workflows os a project

   GHActionWorkflowsExecutor executor = new GHActionWorkflowsExecutor();
   executor.setPageSize(100);
   executor.setGithubToken(githubToken);

   List<WorkflowInfo> list =  executor.getWorkflows("jadsonjs/snooper");


# get all runs of a repository between dates

   GHActionRunsExecutor executor = new GHActionRunsExecutor();
   LocalDateTime startCIDate = LocalDateTime.of(2022, 7, 1, 0, 0, 0);
   LocalDateTime endCIDate = LocalDateTime.of(2022, 7, 30, 23, 59, 59);
   // created=2022-07-01..2022-07-30
   executor.setQueryParameters(new String[]{ "created=" + new DateUtils().toIso8601(startCIDate)+".."+new DateUtils().toIso8601(endCIDate) });
   executor.setPageSize(10);

List<RunsInfo> list =  executor.runs("jadsonjs/snooper");

# get all runs of a specific workflow

   GHActionRunsExecutor executor = new GHActionRunsExecutor();
   executor.setPageSize(100);
   executor.setGithubToken(githubToken);

   List<RunsInfo> list =  executor.runs("jadsonjs/snooper", 27792816);


# get last run of a specific of a repository


   GHActionRunsExecutor executor = new GHActionRunsExecutor();
   executor.setPageSize(100);
   executor.setGithubToken(githubToken);

   RunsInfo lastRunInfo =  executor.lastRun("jadsonjs/snooper");


# get first run of a specific of a repository


   GHActionRunsExecutor executor = new GHActionRunsExecutor();
   executor.setPageSize(100);
   executor.setGithubToken(githubToken);

   RunsInfo firstRunInfo =  executor.firstRun("jadsonjs/snooper");


#############################################
#####            COVERALLS              #####
#############################################

# the all coverage of a GitHub project

   String token = "sfOEwmr232sdf203r0033"
   CoverallsBuildsQueryExecutor executor = new CoverallsBuildsQueryExecutor(token);
   
   List<CoverallsBuildInfo> builds =  executor.getBuildsInfo("microsoft/msphpsql", AbstractCoverallsQueryExecutor.CODE_ALL_SERVICE.GITHUB);
  
   for(CoverallsBuildInfo info : builds){
     // this field "covered_percent" has coverage information
     System.out.println(info.covered_percent)
  }
 
#############################################
#####            CODECOV               #####
#############################################  
  
# get coverage information of Github project

  List<CodeCovCommit> commits = new CodeCovCommitsQueryExecutor()
        .getCommits("microsoft/msphpsql", CodeCovCommitsQueryExecutor.CODE_COV_BASE.GITHUB,
                LocalDateTime.of(2021, 11, 26, 0, 0, 0),
                LocalDateTime.of(2022, 02, 22, 23, 59, 50));
                
  for(CodeCovCommit commit : commits){
     // this field "c" has coverage information
     System.out.println(commit.parent_totals.c)
  }              


#############################################
#####           Sonar                  #####
#############################################


# Get java project order by ncloc (number of lines of code)
   
    SonarCloudProjectsQueryExecutor query = new SonarCloudProjectsQueryExecutor();
    List<SonarProjectInfo> projects = query.getSonarProjects("java", "ncloc");
    
# get projects of an organization    

   SonarCloudProjectsQueryExecutor query = new SonarCloudProjectsQueryExecutor();

   List<SonarOrganizationProjectInfo> projects = query.getProjectsOfOrganization("microsoft");
   
   for (SonarOrganizationProjectInfo pi : projects){
      System.out.println("key: "+pi.key);
      System.out.println("name: "+pi.name);
      System.out.println("project: "+pi.project);
      System.out.println("organization: "+pi.organization);
      System.out.println("qualifier: "+pi.qualifier);
      System.out.println("language: "+pi.language);
   }
   
# get specific measure of a project

   SonarCloudProjectsQueryExecutor query = new SonarCloudProjectsQueryExecutor();
   ProjectMeasuresRoot data = query.getProjectMeasure("pipe-line-demo", "coverage");

   System.out.println(data.component.measures.get(0).metric);
   
  
# get a list of specif measure of a project between dates
 
   SonarCloudMetricHistoryQueryExecutor query = new SonarCloudMetricHistoryQueryExecutor();
   LocalDateTime from = LocalDateTime.of(2021,01,05,00,00,00);
   LocalDateTime to = LocalDateTime.of(2021,01,06,23,59,59);
   List<SonarHistoryEntry> historyEntries = query.getProjectMetricHistory("simgrid_simgrid", "coverage", from, to);          

#############################################
#####            TRAVIS CI              #####
#############################################

# Get All builds from Travis-CI of a project

    TravisCIQueryExecutor executor = new TravisCIQueryExecutor();
    List<TravisBuildsInfo> builds = executor.getBuilds("jadsonjs/snooper");





```

### How to run tests

 Run ```gradlew test``` command.
 
  
### Contribution guidelines

Be free to implement new queries or correct bugs and submit pull requests. Since, you write a correlated Unit Test that prove that your implementation is correct.

 
