package net.rcarz.javaclient.agile;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;

import static net.rcarz.jiraclient.Field.DATETIME_FORMAT;

public class Field_v0_6 {
    public static Date getDateTime(Object d) {
        Date result = null;

        if (d instanceof String) {
            SimpleDateFormat df = new SimpleDateFormat(DATETIME_FORMAT);
            result = df.parse((String) d, new ParsePosition(0));
        }

        return result;
    }

    public static long getLong(Object i) {
        long result = 0;
        if (i instanceof Long) {
            result = ((Long) i).longValue();
        } else if (i instanceof Integer) {
            result = ((Integer) i).intValue();
        }
        return result;
    }
}
