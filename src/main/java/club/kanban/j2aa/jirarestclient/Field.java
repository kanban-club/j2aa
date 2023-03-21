package club.kanban.j2aa.jirarestclient;

import org.apache.commons.lang.math.NumberUtils;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;

import static net.rcarz.jiraclient.Field.DATETIME_FORMAT;

public class Field {
    public static long getLong(Object o) {
        if (o instanceof Integer || o instanceof Long) {
            return net.rcarz.jiraclient.Field.getInteger(o);
        } else if (o instanceof String &&
                NumberUtils.isDigits((String) o)) {
            return NumberUtils.toLong((String) o, 0L);
        } else {
            return 0L;
        }
    }

    public static Date getDateTime(Object d) {
        Date result = null;

        if (d instanceof String) {
            SimpleDateFormat df = new SimpleDateFormat(DATETIME_FORMAT);
            result = df.parse((String) d, new ParsePosition(0));
        }

        return result;
    }

    public static String getString(Object o) {
        return net.rcarz.jiraclient.Field.getString(o);
    }
}
