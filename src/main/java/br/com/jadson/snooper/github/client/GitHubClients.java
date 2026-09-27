package br.com.jadson.snooper.github.client;

/**
    The GitHub HTTP interfaces of one token and the fetcher that limits its concurrent requests.
 */
public record GitHubClients(GitHubRestApi rest, GitHubGraphQLApi graphQL, ConcurrentFetcher fetcher) { }