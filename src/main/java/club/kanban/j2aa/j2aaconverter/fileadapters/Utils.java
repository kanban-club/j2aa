package club.kanban.j2aa.j2aaconverter.fileadapters;

class Utils {
    public static String DEFAULT_DATETIME_FORMAT = "MM/dd/yyyy";
    public static String escapeString(String s) {
        return s.replace(",", "\\,")
                .replace("\"", "\\\"")
                .replace("\n", "")
                .replace("\r", "");
    }
}