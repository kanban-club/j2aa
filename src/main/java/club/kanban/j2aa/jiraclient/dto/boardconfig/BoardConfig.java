package club.kanban.j2aa.jiraclient.dto.boardconfig;

import club.kanban.j2aa.jiraclient.dto.JiraResource;
import club.kanban.j2aa.jiraclient.dto.boardconfig.columnconfig.ColumnConfig;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@ToString
@EqualsAndHashCode
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BoardConfig implements JiraResource {
    long id;
    String self;
    String name;
    String type;
    ColumnConfig columnConfig;

    @Override
    public boolean isNotEmpty() {
        return self != null;
    }
}
