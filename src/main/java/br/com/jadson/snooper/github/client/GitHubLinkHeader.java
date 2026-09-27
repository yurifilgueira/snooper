package br.com.jadson.snooper.github.client;

import org.springframework.http.HttpHeaders;

import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the GitHub pagination Link header.
 *
 * Link: <https://api.github.com/repositories/1300192/issues?page=2>; rel="next",
 *       <https://api.github.com/repositories/1300192/issues?page=34>; rel="last"
 *
 * https://docs.github.com/en/rest/using-the-rest-api/using-pagination-in-the-rest-api
 */
public final class GitHubLinkHeader {

    private static final Pattern LINK = Pattern.compile("<([^>]*)>\\s*;\\s*rel=\"([^\"]*)\"");
    private static final Pattern PAGE_PARAM = Pattern.compile("[?&]page=(\\d+)");

    private GitHubLinkHeader() { }

    /** Number of the last page, empty if the response has a single page or GitHub did not send rel="last" */
    public static OptionalInt lastPage(HttpHeaders headers) {
        String url = url(headers, "last");
        if (url == null)
            return OptionalInt.empty();

        Matcher page = PAGE_PARAM.matcher(url);
        return page.find() ? OptionalInt.of(Integer.parseInt(page.group(1))) : OptionalInt.empty();
    }

    public static boolean hasNext(HttpHeaders headers) {
        return url(headers, "next") != null;
    }

    private static String url(HttpHeaders headers, String rel) {
        if (headers == null)
            return null;

        String link = headers.getFirst(HttpHeaders.LINK);
        if (link == null)
            return null;

        Matcher m = LINK.matcher(link);
        while (m.find()) {
            if (rel.equals(m.group(2)))
                return m.group(1);
        }
        return null;
    }
}
