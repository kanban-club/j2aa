package club.kanban.j2aa.jiraclient.dto;

import club.kanban.j2aa.jiraclient.dto.issue.Issue;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

import java.util.Collections;
import java.util.List;

@Getter
@ToString
@EqualsAndHashCode
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BoardIssuesPage {
    public static final int DEFAULT_MAX_RESULTS = 50;
    int startAt;
    int maxResults;
    int total;
    List<Issue> issues;

    public int nextPageStartAt() {
//        return startAt + issues.size();
        return startAt + maxResults;
    }

    public boolean hasNextPage() {
        return nextPageStartAt() < getTotal();
    }

    public List<Issue> getIssues() {
        return issues != null ? Collections.unmodifiableList(issues) : Collections.emptyList();
    }
}
