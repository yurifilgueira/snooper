package br.com.jadson.snooper.gitlab.data.stats;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Churn (number of commits) information of a file in a GitLab repository.
 *
 * Luisa Ferreira - luisaferreirass08@gmail.com
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitLabFileStats {
    public String path;
    public int commits;
}
