package club.kanban.j2aa.j2aaconverter;

import club.kanban.j2aa.jiraclient.dto.issue.changelog.history.History;
import club.kanban.j2aa.jiraclient.dto.issue.changelog.history.HistoryItem;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.Date;

public class ChangeLogItem {
    @Getter
    private final Long to;
    @Getter
    private final Long from;
    @Getter
    private final Date date;
    @Getter
    private final String fromString;
    @Getter
    private final String toString;

    static final long SKIP_FROM = 0x2;
    static final long SKIP_TO = 0x4;
    static final long SKIP_FROM_STRING = 0x8;
    static final long SKIP_TO_STRING = 0x10;

    private ChangeLogItem(Date date,
                         @Nullable Long from, @Nullable String fromString,
                         @Nullable Long to, @Nullable String toString) {
        this.date = date;
        this.to = to;
        this.from = from;
        this.fromString = fromString;
        this.toString = toString;
    }

    public static ChangeLogItem of(History history, HistoryItem historyItem, long flags) {
        Date date = history.getCreated();
        Long from = (flags & SKIP_FROM) != 0 ? null : Long.parseLong(historyItem.getFrom());
        String fromString = (flags & SKIP_FROM_STRING) != 0 ? null : historyItem.getFromString();
        Long to = (flags & SKIP_TO) != 0 ? null : Long.parseLong(historyItem.getTo());
        String toString = (flags & SKIP_TO_STRING) != 0 ? null : historyItem.getToString();

        return new ChangeLogItem(date, from, fromString, to, toString);
    }

    /**
     * Для использования в юнит тестах BlockersCalendar. Отображает смену статуса Impediment
     * @param date дата установки или снятия флака Impediment
     * @param setImpediment true если открываем Impediment, false если снимаем
     * @return эксземпляр ChangeLogItem описывающий поднятие или снятия флага Impediment
     */
    public static ChangeLogItem of(Date date, boolean setImpediment) {
        return new ChangeLogItem(date,
                null,
                setImpediment ? null : "Impediment",
                null,
                setImpediment ? "Impediment" : null);
    }
}
