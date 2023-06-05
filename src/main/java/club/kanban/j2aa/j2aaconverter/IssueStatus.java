package club.kanban.j2aa.j2aaconverter;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

public class IssueStatus {
    @Getter
    private final long statusId;
    @Getter
    private final Date dateIn;
    @Getter
    private final String name;
    @Setter
    private Date dateOut;

    public IssueStatus(Date dateIn, long statusId, String name) {
        this.dateIn = dateIn;
        this.statusId = statusId;
        this.name = name;
        this.dateOut = null;
    }

    public long getCycleTimeInMillis() {
        return dateOut != null ? (dateOut.getTime() - dateIn.getTime()) : 0;
    }
}
