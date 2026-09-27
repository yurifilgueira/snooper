package br.com.jadson.snooper.github.client;

/**
 * The GitHub HTTP interfaces of one token.
 */
public record GitHubClients(GitHubRestApi rest, GitHubGraphQLApi graphQL) { }
