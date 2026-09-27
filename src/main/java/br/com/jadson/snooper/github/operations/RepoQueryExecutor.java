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
 * RepoQueryExecutor
 * 18/12/20
 */
package br.com.jadson.snooper.github.operations;

import br.com.jadson.snooper.github.data.repo.GitHubRepoInfo;
import br.com.jadson.snooper.github.data.repo.GitHubTreeInfo;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Return information about repository of github
 *
 * https://api.github.com/repos/webauthn4j/webauthn4j
 *
 * Jadson Santos - jadsonjs@gmail.com
 */
public class RepoQueryExecutor extends AbstractGitHubQueryExecutor{

    public RepoQueryExecutor(){ }

    public RepoQueryExecutor(String githubToken){
        super(githubToken);
    }

    /**
     * Return all information about Github repository
     *
     * @param repoOwner
     * @param repoName
     * @return
     * @deprecated use {@link #fetchRepoInfo(String, String)}
     */
    @Deprecated(since = "3.0")
    public GitHubRepoInfo getRepoInfo(String repoOwner, String repoName) {
        return getRepoInfo(repoOwner+"/"+repoName);
    }

    /**
     * Return all information about Github repository
     *
     * @param repoFullName
     * @return
     * @deprecated use {@link #fetchRepoInfo(String)}, or {@link #fetchRepoInfos(Collection)} for many repositories in parallel
     */
    @Deprecated(since = "3.0")
    public GitHubRepoInfo getRepoInfo(String repoFullName) {

        ResponseEntity<GitHubRepoInfo> result;

        String query = GIT_HUB_API_URL +"/repos/"+repoFullName;

        RestTemplate restTemplate = new RestTemplate();

        HttpEntity entity = new HttpEntity(getDefaultHeaders());

        result = restTemplate.exchange( query, HttpMethod.GET, entity, GitHubRepoInfo.class);

        return result.getBody();
    }

    /**
     * Return all files and directories from a GitHub repository tree.
     *
     * This method uses the REST API of GitHub.
     *
     * @param repoFullName
     * @param branch
     * @return
     * @deprecated use {@link #fetchAllFiles(String, String)}
     */
    @Deprecated(since = "3.0")
    public GitHubTreeInfo getAllFiles(String repoFullName, String branch){
        ResponseEntity<GitHubTreeInfo> result;
        String url = GIT_HUB_API_URL +
                "/repos/" + repoFullName + "/git/trees/" + branch + "?recursive=1";
        RestTemplate restTemplate = new RestTemplate();

        HttpEntity entity = new HttpEntity(getDefaultHeaders());

        result = restTemplate.exchange(url, HttpMethod.GET, entity, GitHubTreeInfo.class);
        return result.getBody();
    }

    // @HttpExchange methods

    public GitHubRepoInfo fetchRepoInfo(String repoOwner, String repoName) {
        return fetchRepoInfo(repoOwner + "/" + repoName);
    }

    // Return all information about a GitHub repository
    public GitHubRepoInfo fetchRepoInfo(String repoFullName) {
        return clients().rest().getRepo(owner(repoFullName), name(repoFullName));
    }

    /**
        Return the information of many repositories, fetched in parallel (one request per repository).

        @return repo full name -> information, in the order of the names
     */
    public Map<String, GitHubRepoInfo> fetchRepoInfos(Collection<String> repoFullNames) {
        return fetchEach(new LinkedHashSet<>(repoFullNames), this::fetchRepoInfo);
    }

    /**
        Return all files and directories of a branch (recursive tree).

        GitHub truncates trees with more than 100,000 entries or 7 MB, so very large repositories come incomplete.
     */
    public GitHubTreeInfo fetchAllFiles(String repoFullName, String branch) {
        return clients().rest().getTree(owner(repoFullName), name(repoFullName), branch, 1);
    }

}
