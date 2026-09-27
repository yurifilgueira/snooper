package br.com.jadson.snooper.github.client;

import br.com.jadson.snooper.github.data.association.graphql.ResultGraphQLRepository;
import br.com.jadson.snooper.github.data.stats.graphql.GraphQLCommitResponse;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.Map;

/**
 * GitHub GraphQL API v4 as a Spring declarative HTTP interface.
 *
 * Queries are sent with variables instead of string concatenation, so no manual escaping is needed.
 *
 * https://docs.github.com/en/graphql
 */
@HttpExchange(url = "/graphql", accept = "application/json", contentType = "application/json")
public interface GitHubGraphQLApi {

    record Request(String query, Map<String, Object> variables) { }

    @PostExchange
    GraphQLCommitResponse queryCommitStats(@RequestBody Request request);

    @PostExchange
    ResultGraphQLRepository queryAssociatedPullRequests(@RequestBody Request request);
}
