package club.kanban.jirarestclient;

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
    List<BoardIssue> boardIssues;

    public BoardIssuesSet(List<BoardIssue> boardIssues, int total, int maxResults) {
        this.boardIssues = boardIssues;
        this.total = total;
        this.maxResults = maxResults;
    }
}
