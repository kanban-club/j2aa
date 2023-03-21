package club.kanban.j2aa.j2aaconverter.fileadapters;

import club.kanban.j2aa.j2aaconverter.ExportableIssue;

public interface FileAdapter {
    String getDefaultExtension();
    String getDescription();
    String getHeaders(ExportableIssue expIssue);
    String getValues(ExportableIssue expIssue);
    default String getPrefix() {
        return "";
    }
    default String getPostfix() {
        return "";
    }
}
