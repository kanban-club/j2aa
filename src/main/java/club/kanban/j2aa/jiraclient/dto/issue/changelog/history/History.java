package club.kanban.j2aa.jiraclient.dto.issue.changelog.history;

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
public class History {
    long id;
    Object author;
    Date created;
    @JsonProperty("items")
    List<HistoryItem> historyItems;

    public List<HistoryItem> getHistoryItems() {
        return historyItems != null ? Collections.unmodifiableList(historyItems) : Collections.emptyList();
    }
}
