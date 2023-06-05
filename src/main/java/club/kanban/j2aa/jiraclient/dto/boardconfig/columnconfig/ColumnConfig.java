package club.kanban.j2aa.jiraclient.dto.boardconfig.columnconfig;

import club.kanban.j2aa.jiraclient.dto.boardconfig.columnconfig.column.Column;
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
public class ColumnConfig {
    String constraintType;
    List<Column> columns;

    public List<Column> getColumns() {
        return columns != null ? Collections.unmodifiableList(columns) : Collections.emptyList();
    }
}
