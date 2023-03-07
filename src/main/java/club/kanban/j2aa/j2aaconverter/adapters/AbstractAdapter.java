package club.kanban.j2aa.j2aaconverter.adapters;

import club.kanban.j2aa.j2aaconverter.ExportableIssue;

public abstract class AbstractAdapter {
    protected final String DEFAULT_DATETIME_FORMAT = "MM/dd/yyyy";
    protected String formatString(String s) {
        return s.replace(",", "\\,").replace("\"", "\\\"");
    }
    public abstract String getHeaders(ExportableIssue expIssue);
    public abstract String getValues(ExportableIssue expIssue);
    public String getPrefix() {
        return "";
    }
    public String getPostfix() {
        return "";
    }
}
