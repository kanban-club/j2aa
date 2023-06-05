package club.kanban.j2aa.jiraclient.dto;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

@Getter
@ToString
@EqualsAndHashCode
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Board implements JiraResource {
    long id;
    String self;
    String name;
    String type;

    @Override
    public boolean isNotEmpty() {
        return self != null;
//        return id > 0 && self != null;
    }
}
