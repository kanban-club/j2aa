package club.kanban.j2aa.jiraclient.dto.issue.fields;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

@Getter
@ToString
@EqualsAndHashCode
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Epic {
    long id;
    String key;
    String self;
    String name;
}