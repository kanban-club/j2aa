package club.kanban.j2aa.jiraclient.dto.issue.changelog.history;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

@Getter
@ToString
@EqualsAndHashCode
@FieldDefaults(level = AccessLevel.PRIVATE)
public class HistoryItem {
    String field;
    String fieldtype;
    String from;
    String fromString;
    String to;
    String toString;
}
