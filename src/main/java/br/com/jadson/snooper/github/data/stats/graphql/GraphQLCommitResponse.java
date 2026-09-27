package br.com.jadson.snooper.github.data.stats.graphql;

import br.com.jadson.snooper.github.data.GraphQLError;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Root response returned by the GitHub GraphQL API.
 *
 * Yuri Filgueira - yurimedeiros141@gmail.com
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GraphQLCommitResponse {
    public Data data;
    public List<GraphQLError> errors;
}