package club.kanban.j2aaconverter;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

public class Status {
    @Getter private long statusId;
    @Getter private Date dateIn;
    @Getter private String name;
    @Setter private Date dateOut;

    public Status(Date dateIn, long statusId, String name) {
        this.statusId = statusId;
        this.dateIn = dateIn;
        this.dateOut = null;
        this.name = name;
    }

    public long getCycleTimeInMillis() {
        return dateOut != null ? (dateOut.getTime() - dateIn.getTime()) : 0;
    }
}
