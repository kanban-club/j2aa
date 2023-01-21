package club.kanban.jirarestclient;

import lombok.Getter;
import net.rcarz.jiraclient.agile.Field_v0_6;
import net.sf.json.JSONObject;
import org.jetbrains.annotations.Nullable;

import java.util.Date;

public class Change {
    @Getter private Long to;
    @Getter private Long from;
    @Getter private Date date;
    @Getter private String fromString;
    @Getter private String toString;

    static final long SKIP_FROM = 0x2;
    static final long SKIP_TO = 0x4;
    static final long SKIP_FROM_STRING = 0x8;
    static final long SKIP_TO_STRING = 0x10;

    public Change(Date date, @Nullable Long from, @Nullable String fromString, @Nullable Long to, @Nullable String toString) {
        this.date = date;
        this.to = to;
        this.from = from;
        this.fromString = fromString;
        this.toString = toString;
    }

    public static Change get(JSONObject history, JSONObject historyItem, long flags) {
        Date date = Field_v0_6.getDateTime(history.get("created"));
        Long from = (flags & SKIP_FROM) != 0 ? null : historyItem.getLong("from");
        String fromString = (flags & SKIP_FROM_STRING) != 0 ? null : historyItem.getString("fromString");
        Long to = (flags & SKIP_TO) != 0 ? null : historyItem.getLong("to");
        String toString = (flags & SKIP_TO_STRING) != 0 ? null : historyItem.getString("toString");
        return new Change(date, from, fromString, to, toString);
    }
}
