package club.kanban.j2aaconverter.adapters;

import club.kanban.j2aaconverter.ExportableIssue;

public class Adapter {
    public final String DEFAULT_DATETIME_FORMAT = "MM/dd/yyyy";

    public String getPrefix() {
        return "";
    }

    public String getPostfix() {
        return "";
    }

    public String getHeaders(ExportableIssue expIssue) {
        return "";
    }

    public String getValues(ExportableIssue expIssue) {
        return "";
    }

    protected String formatString(String s) {
        return s.replace(",", "\\,").replace("\"", "\\\"");
    }
}
