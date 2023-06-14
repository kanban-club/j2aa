package club.kanban.j2aa.jiraclient.dto.issue.fields;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

import java.util.Collections;
import java.util.Date;
import java.util.List;

@Getter
@ToString
@EqualsAndHashCode
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Fields {
    String summary;
    Date created;
    List<String> labels;
    List<Resource> fixVersions;
    List<Resource> components;
    @JsonProperty("issuetype")
    Resource issueType;
    Resource status;
    Resource priority;
    Resource project;
    Person assignee;
    Person reporter;
    Epic epic;

    public List<String> getLabels() {
        return labels != null ? Collections.unmodifiableList(labels) : Collections.emptyList();
    }

    public List<Resource> getFixVersions() {
        return fixVersions != null ? Collections.unmodifiableList(fixVersions) : Collections.emptyList();
    }
}
