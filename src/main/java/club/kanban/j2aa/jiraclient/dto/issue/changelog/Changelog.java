package club.kanban.j2aa.jiraclient.dto.issue.changelog;

import club.kanban.j2aa.jiraclient.dto.issue.changelog.history.History;
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
public class Changelog {
    int startAt;
    int maxResults;
    int total;
    List<History> histories;

    public List<History> getHistories() {
        return histories != null ? Collections.unmodifiableList(histories) : Collections.emptyList();
    }
}
