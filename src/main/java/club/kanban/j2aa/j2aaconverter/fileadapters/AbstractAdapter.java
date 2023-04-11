package club.kanban.j2aa.j2aaconverter.fileadapters;

import club.kanban.j2aa.j2aaconverter.ConvertedIssue;

public abstract class AbstractAdapter implements Exportable {
    @Override
    public abstract String getDefaultExtension();

    @Override
    public abstract String getDescription();

    @Override
    public abstract String getHeaders(ConvertedIssue expIssue);

    @Override
    public abstract String getValues(ConvertedIssue expIssue);

    @Override
    public String getPrefix() {
        return "";
    }

    @Override
    public String getPostfix() {
        return "";
    }
}
