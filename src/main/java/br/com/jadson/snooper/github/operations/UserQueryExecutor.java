/*
 * Universidade Federal do Rio Grande do Norte
 * Instituto Metrópole Digital
 * Diretoria de Tecnologia da Informação
 *
 * snooper
 * UserQueryExecutor
 * @since 02/08/2021
 */
package br.com.jadson.snooper.github.operations;

import br.com.jadson.snooper.github.data.users.GitHubUserInfo;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;

/**
 * Information about Github Users
 *
 * @author Jadson Santos - jadson.santos@ufrn.br
 */
public class UserQueryExecutor extends AbstractGitHubQueryExecutor{

    public UserQueryExecutor(){ }

    public UserQueryExecutor(String githubToken){
        super(githubToken);
    }


    /**
     * Return information about user
     *
     * @param userLogin
     * @return
     * @deprecated use {@link #fetchUser(String)}, or {@link #fetchUsers(Collection)} for many users in parallel
     */
    @Deprecated(since = "3.0")
    public GitHubUserInfo user(String userLogin) {

        String parameters = "";

        ResponseEntity<GitHubUserInfo> result;

        if(queryParameters != null && ! queryParameters.isEmpty())
            parameters = "?"+queryParameters;

        String query = GIT_HUB_API_URL +"/users/"+userLogin+""+parameters;

        System.out.println("Getting User Info: "+query);

        RestTemplate restTemplate = new RestTemplate();

        HttpEntity entity = new HttpEntity(getDefaultHeaders());

        result = restTemplate.exchange( query, HttpMethod.GET, entity, GitHubUserInfo.class);

        return result.getBody();

    }

    // @HttpExchange methods

    // Return information about a user
    public GitHubUserInfo fetchUser(String userLogin) {
        return clients().rest().getUser(userLogin, pageParams().query());
    }

    /**
        Return information about many users (the authors of the commits of a repository, for example),
        fetched in parallel (one request per user).

        @return login -> information, in the order of the logins
     */
    public Map<String, GitHubUserInfo> fetchUsers(Collection<String> userLogins) {
        MultiValueMap<String, String> params = pageParams().query();

        System.out.println("Fetching " + userLogins.size() + " users");
        return fetchEach(new LinkedHashSet<>(userLogins), login -> clients().rest().getUser(login, params));
    }
}
