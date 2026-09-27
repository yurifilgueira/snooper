package br.com.jadson.snooper.github.client;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.util.OptionalInt;

class GitHubLinkHeaderTest {

    @Test
    void readsTheLastPage() {
        HttpHeaders headers = link("<https://api.github.com/repositories/1300192/issues?page=2&per_page=100>; rel=\"next\", "
                + "<https://api.github.com/repositories/1300192/issues?page=34&per_page=100>; rel=\"last\"");

        Assertions.assertEquals(OptionalInt.of(34), GitHubLinkHeader.lastPage(headers));
        Assertions.assertTrue(GitHubLinkHeader.hasNext(headers));
    }

    @Test
    void doesNotConfusePerPageWithPage() {
        HttpHeaders headers = link("<https://api.github.com/repos/o/r/commits?per_page=100&page=7>; rel=\"last\"");

        Assertions.assertEquals(OptionalInt.of(7), GitHubLinkHeader.lastPage(headers));
    }

    @Test
    void onlyNext() {
        HttpHeaders headers = link("<https://api.github.com/repos/o/r/commits?page=2>; rel=\"next\"");

        Assertions.assertTrue(GitHubLinkHeader.lastPage(headers).isEmpty());
        Assertions.assertTrue(GitHubLinkHeader.hasNext(headers));
    }

    @Test
    void lastPageOfTheListHasNoNext() {
        HttpHeaders headers = link("<https://api.github.com/repos/o/r/commits?page=1>; rel=\"first\", "
                + "<https://api.github.com/repos/o/r/commits?page=33>; rel=\"prev\"");

        Assertions.assertTrue(GitHubLinkHeader.lastPage(headers).isEmpty());
        Assertions.assertFalse(GitHubLinkHeader.hasNext(headers));
    }

    @Test
    void singlePageHasNoLinkHeader() {
        Assertions.assertTrue(GitHubLinkHeader.lastPage(new HttpHeaders()).isEmpty());
        Assertions.assertFalse(GitHubLinkHeader.hasNext(new HttpHeaders()));
        Assertions.assertTrue(GitHubLinkHeader.lastPage(null).isEmpty());
    }

    private static HttpHeaders link(String value) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.LINK, value);
        return headers;
    }
}
