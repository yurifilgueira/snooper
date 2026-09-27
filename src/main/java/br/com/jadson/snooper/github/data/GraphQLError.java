package br.com.jadson.snooper.github.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
    An error of a GitHub GraphQL response. GraphQL answers HTTP 200 with "errors" instead of an HTTP error.

    https://docs.github.com/en/graphql/overview/rate-limits-and-node-limits-for-the-graphql-api
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GraphQLError {

    public String type;
    public String message;
    public List<String> path;

    public static void throwIfAny(List<GraphQLError> errors) {
        if (errors == null || errors.isEmpty())
            return;

        StringBuilder messages = new StringBuilder();
        for (GraphQLError error : errors) {
            if (messages.length() > 0)
                messages.append("; ");
            messages.append(error.type != null ? error.type + ": " : "").append(error.message);
        }
        throw new IllegalStateException("GitHub GraphQL error: " + messages);
    }
}
