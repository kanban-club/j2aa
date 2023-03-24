package club.kanban.j2aa.j2aaconverter.fileadapters;

import club.kanban.j2aa.j2aaconverter.ConvertedIssue;

public interface Exportable {
    String getDefaultExtension();
    String getDescription();
    String getHeaders(ConvertedIssue expIssue);
    String getValues(ConvertedIssue expIssue);
    String getPrefix();
    String getPostfix();
}
