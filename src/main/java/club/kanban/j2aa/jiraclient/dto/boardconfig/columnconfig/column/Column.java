package club.kanban.j2aa.jiraclient.dto.boardconfig.columnconfig.column;

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
public class Column {
    String name;
    List<Status> statuses;

    public List<Status> getStatuses() {
        return statuses != null ? Collections.unmodifiableList(statuses) : Collections.emptyList();
    }
}
