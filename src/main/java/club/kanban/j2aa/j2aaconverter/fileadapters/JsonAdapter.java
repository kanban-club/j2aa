package club.kanban.j2aa.j2aaconverter.fileadapters;

import club.kanban.j2aa.j2aaconverter.ExportableIssue;
import club.kanban.j2aa.jirarestclient.BoardColumn;
import org.springframework.stereotype.Repository;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class JsonAdapter extends FileAdapter {

    @Override
    public String getHeaders(ExportableIssue expIssue) {
        List<String> headers = new ArrayList<>(3
                + expIssue.getConverter().getBoard().getBoardConfig().getBoardColumns().size()
                + expIssue.getAttributes().size());
        headers.addAll(Arrays.asList("ID", "Link", "Name"));
        for (BoardColumn boardColumn : expIssue.getConverter().getBoard().getBoardConfig().getBoardColumns())
            headers.add(boardColumn.getName());
        headers.addAll(expIssue.getAttributes().keySet());
        return "[" + headers.stream()
                .map(s -> "\"" + formatString(s) + "\"")
                .collect(Collectors.joining(","))
                + "]";
    }

    @Override
    public String getDefaultExtension() {
        return "json";
    }

    @Override
    public String getDescription() {
        return "JSON файлы";
    }

    protected JsonAdapter() {
    }

    @Override
    public String getValues(ExportableIssue expIssue) {
        List<String> values = new ArrayList<>(3
                + expIssue.getConverter().getBoard().getBoardConfig().getBoardColumns().size()
                + expIssue.getAttributes().size());
        values.addAll(Arrays.asList(expIssue.getKey(), expIssue.getLink(), expIssue.getName()));

        DateFormat df = new SimpleDateFormat(DEFAULT_DATETIME_FORMAT);

        for (BoardColumn boardColumn : expIssue.getConverter().getBoard().getBoardConfig().getBoardColumns()) {
            Date date = expIssue.getColumnTransitionsLog()[(int) boardColumn.getId()];
            values.add(date != null ? df.format(date) : "");
        }

        expIssue.getAttributes().forEach((k, v) -> {
            if (v instanceof String) {
                values.add(formatString((String) v));
            } else if (v instanceof List<?> && ((List<?>) v).size() > 0) {
                values.add("[" + ((List<?>) v).stream()
                        .map(o -> this.formatString(o.toString()))
                        .collect(Collectors.joining("|"))
                        + "]");
            } else {
                values.add("");
            }
        });

        return "\n,[" + values.stream()
                .map(s -> "\"" + formatString(s) + "\"")
                .collect(Collectors.joining(","))
                + "]";
    }

    @Override
    public String getPrefix() {
        return "[";
    }

    @Override
    public String getPostfix() {
        return "]";
    }
}
