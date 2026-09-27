/*
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
 * br.com.jadson.snooper.github
 * GitHubClient
 * 22/09/20
 */
package br.com.jadson.snooper.github.operations;

import br.com.jadson.snooper.github.client.GitHubClientFactory;
import br.com.jadson.snooper.github.client.GitHubClients;
import br.com.jadson.snooper.github.client.GitHubLinkHeader;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.IntFunction;

/**
 * A client for github
 *
 * Jadson Santos - jadsonjs@gmail.com
 */
public abstract class AbstractGitHubQueryExecutor {

    public static final String GIT_HUB_API_URL = "https://api.github.com";

    protected String githubToken = "";

    /**
     * Default page size of pagination
     * maximum 100
     * https://docs.github.com/en/free-pro-team@latest/rest/guides/traversing-with-pagination
     */
    protected int pageSize = 100;

    /**
     * All to set fix parameters beyond of page and pageSize
     *
     * Executor e = new Executor();
     * e.setPageSize(10);
     * e.setQueryParameters(new String[]{"state=all"});
     *
     * this will generate a query:  ?state=all&page=X&per_page=10
     */
    protected String queryParameters;

    /** if is executing a test, or ir real query. */
    protected boolean testEnvironment = false;

    public AbstractGitHubQueryExecutor(){ }

    public AbstractGitHubQueryExecutor(String githubToken){
        if(githubToken == null || githubToken.trim().equals(""))
            throw new RuntimeException("Invalid GitHub Token: "+githubToken);

        this.githubToken = githubToken;
    }

    public void setPageSize(int pageSize) {
        if(pageSize < 0 || pageSize > 100)
            throw new RuntimeException("Invalid Page Size: "+pageSize+" see: https://docs.github.com/en/free-pro-team@latest/rest/guides/traversing-with-pagination");
        this.pageSize = pageSize;
    }

    public void setQueryParameters(String[] parametersArrays) {
        if(parametersArrays == null || parametersArrays.length == 0)
            throw new RuntimeException("Invalid Query Parameters: "+queryParameters);

        this.queryParameters = "";
        for (String p :parametersArrays){
            this.queryParameters += p+"&";
        }
    }

    protected final void validateRepoName(String repoFullName){
        if(repoFullName == null || repoFullName.trim().equals(""))
            throw new RuntimeException("Invalid GitHub repo full name: "+repoFullName);

        if(! repoFullName.contains("/"))
            throw new RuntimeException("Invalid GitHub repo full name: "+repoFullName+". The name should be owner/repo");
    }

    protected HttpHeaders getDefaultHeaders(){
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/json");
        if(githubToken != null && ! githubToken.isEmpty())
            headers.set("Authorization", "token "+githubToken+"");
        return headers;
    }

    public void setGithubToken(String githubToken) {
        this.githubToken = githubToken;
    }

    public void setTestEnvironment(boolean testEnvironment) {
        this.testEnvironment = testEnvironment;
    }

    public String getQueryParameters() { return queryParameters; }

    // Helpers of the @HttpExchange methods

    // Set only by tests; otherwise the shared clients of the current token are used
    private GitHubClients clients;

    void setClients(GitHubClients clients) {
        this.clients = clients;
    }

    // Looked up on every call, so setGithubToken takes effect on the next request
    protected GitHubClients clients() {
        return clients != null ? clients : GitHubClientFactory.forToken(githubToken);
    }

    // "owner/repo" or "owner/repo/" -> owner
    protected final String owner(String repoFullName) {
        return splitRepoName(repoFullName)[0];
    }

    // "owner/repo" or "owner/repo/" -> repo
    protected final String name(String repoFullName) {
        return splitRepoName(repoFullName)[1];
    }

    private String[] splitRepoName(String repoFullName) {
        validateRepoName(repoFullName);
        String[] parts = repoFullName.trim().split("/");
        if (parts.length < 2 || parts[0].isBlank() || parts[1].isBlank())
            throw new RuntimeException("Invalid GitHub repo full name: " + repoFullName + ". The name should be owner/repo");
        return parts;
    }

    /**
        Copy of queryParameters and pageSize taken at the start of a fetch, so changing
        the setters while a fetch is running does not mix parameters between pages.
     */
    protected PageParams pageParams() {
        return new PageParams(parseQueryParameters(queryParameters), pageSize);
    }

    protected static final class PageParams {

        private final MultiValueMap<String, String> params;
        private final int pageSize;

        private PageParams(MultiValueMap<String, String> params, int pageSize) {
            this.params = params;
            this.pageSize = pageSize;
        }

        // Only the user query parameters (for endpoints without pagination)
        public MultiValueMap<String, String> query() {
            return new LinkedMultiValueMap<>(params);
        }

        public MultiValueMap<String, String> forPage(int page) {
            MultiValueMap<String, String> pageParams = new LinkedMultiValueMap<>(params);
            pageParams.set("page", String.valueOf(page));
            pageParams.set("per_page", String.valueOf(pageSize));
            return pageParams;
        }
    }

    // "state=all&since=2021-03-01T22:26:45Z&" -> {state=[all], since=[2021-03-01T22:26:45Z]}
    static MultiValueMap<String, String> parseQueryParameters(String queryParameters) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        if (queryParameters == null)
            return params;

        for (String pair : queryParameters.split("&")) {
            if (pair.isBlank())
                continue;
            int equals = pair.indexOf('=');
            if (equals < 0)
                params.add(pair.trim(), "");
            else
                params.add(pair.substring(0, equals).trim(), pair.substring(equals + 1).trim());
        }
        return params;
    }

    /**
        Fetches all pages of a GitHub list endpoint.

        Page 1 is fetched first. If its Link header has rel="last", pages 2..N are fetched in parallel
        and merged in order. Without rel="last" the rel="next" links are followed one by one.
        With testEnvironment only page 1 is fetched.
     */
    protected <T> List<T> fetchAllPages(IntFunction<ResponseEntity<T[]>> pageCall) {

        ResponseEntity<T[]> first = pageCall.apply(1);
        List<T> all = new ArrayList<>(bodyOf(first));

        if (testEnvironment)
            return all;

        OptionalInt lastPage = GitHubLinkHeader.lastPage(first.getHeaders());

        if (lastPage.isPresent()) {
            List<Callable<List<T>>> pages = new ArrayList<>();
            for (int page = 2; page <= lastPage.getAsInt(); page++) {
                int p = page;
                pages.add(() -> bodyOf(pageCall.apply(p)));
            }
            for (List<T> page : clients().fetcher().fetchAll(pages))
                all.addAll(page);
            return all;
        }

        ResponseEntity<T[]> current = first;
        int page = 1;
        while (GitHubLinkHeader.hasNext(current.getHeaders())) {
            current = pageCall.apply(++page);
            all.addAll(bodyOf(current));
        }
        return all;
    }

    // Runs one request per key in parallel. The map keeps the order of the keys
    protected <K, V> Map<K, V> fetchEach(Collection<K> keys, Function<K, V> call) {
        List<K> orderedKeys = new ArrayList<>(keys);

        List<Callable<V>> tasks = new ArrayList<>();
        for (K key : orderedKeys)
            tasks.add(() -> call.apply(key));

        List<V> values = clients().fetcher().fetchAll(tasks);

        Map<K, V> result = new LinkedHashMap<>();
        for (int i = 0; i < orderedKeys.size(); i++)
            result.put(orderedKeys.get(i), values.get(i));
        return result;
    }

    private static <T> List<T> bodyOf(ResponseEntity<T[]> response) {
        T[] body = response == null ? null : response.getBody();
        return body == null ? new ArrayList<>() : Arrays.asList(body);
    }



}
