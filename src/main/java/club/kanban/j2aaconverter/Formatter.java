package club.kanban.j2aaconverter;

import java.util.List;
import java.util.stream.Collectors;

public class Formatter {
    public static String formatList(List<String> list, CharSequence delimiter, boolean quoteItems) {
        String s = "";

        if (list != null && list.size() > 0) {
            s = list.stream()
                    .map(obj -> quoteItems ? "\"" + formatString(obj) + "\"" : formatString(obj))
                    .collect(Collectors.joining(delimiter));
        }
        return s;
    }

    public static String formatString(String s) {
        return s.replace(",", "\\,").replace("\"", "\\\"");
    }

}
