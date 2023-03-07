package club.kanban.j2aa.jirarestclient;

import lombok.Getter;

import java.util.List;

/**
 * Represents REST response from jira with issues set
 */
public class BoardIssuesSet {
    @Getter
    private final int maxResults;
    @Getter
    private final int total;
    @Getter
    List<Issue> issues;

    public BoardIssuesSet(List<Issue> issues, int total, int maxResults) {
        this.issues = issues;
        this.total = total;
        this.maxResults = maxResults;
    }
}
