package club.kanban.j2aaconverter;

import java.util.List;
import java.util.stream.Collectors;

public class CSVFormatter {
    public static String formatList(List<String> list, CharSequence delimiter, boolean addQuotes) {
        String s = "";

        if (list != null && list.size() > 0) {
            s = list.stream()
                    .map(obj -> addQuotes ? "\"" + formatString(obj) + "\"" : formatString(obj))
                    .collect(Collectors.joining(delimiter));
        }
        return s;
    }

    public static String formatString(String s) {
        return s.replace("\"", "\\\"").replace(",", "\\,");
    }

}
