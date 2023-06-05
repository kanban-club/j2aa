package club.kanban.j2aa.jiraclient.dto.issue;

import club.kanban.j2aa.jiraclient.dto.JiraResource;
import club.kanban.j2aa.jiraclient.dto.issue.changelog.Changelog;
import club.kanban.j2aa.jiraclient.dto.issue.fields.Fields;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@EqualsAndHashCode
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Issue implements JiraResource {
    long id;
    String self;
    String key;
    Changelog changelog;
    Fields fields;

    @Override
    public boolean isNotEmpty() {
        return self != null;
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("Issue{");
        sb.append("key='").append(key).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
