package club.kanban.j2aaconverter;

import java.util.List;
import java.util.stream.Collectors;

public class CSVFormatter {
    public static String formatList(List<String> list) {
        String s = "";

        if (list != null && list.size() > 0) {
            s = "[" + list.stream()
                    .map(CSVFormatter::formatString)
                    .collect(Collectors.joining("|")) + "]";
        }
        return s;
    }

    public static String formatString(String s) {
        return "\"" + s.replace("\"", "\\\"").replace(",", "\\,") + "\"";
    }
}
