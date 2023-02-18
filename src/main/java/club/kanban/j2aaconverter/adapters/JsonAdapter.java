package club.kanban.j2aaconverter.adapters;

import club.kanban.j2aaconverter.ExportableIssue;
import club.kanban.jirarestclient.BoardColumn;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class JsonAdapter extends Adapter {

    @Override
    public String getHeaders(ExportableIssue expIssue) {
        List<String> headers = new ArrayList<>(3
                + expIssue.getBoardConfig().getBoardColumns().size()
                + expIssue.getAttributes().size());
        headers.addAll(Arrays.asList("ID", "Link", "Name"));
        for (BoardColumn boardColumn : expIssue.getBoardConfig().getBoardColumns())
            headers.add(boardColumn.getName());
        headers.addAll(expIssue.getAttributes().keySet());
        return "[" + headers.stream()
                .map(s -> "\"" + formatString(s) + "\"")
                .collect(Collectors.joining(","))
                + "]";
    }

    @Override
    public String getValues(ExportableIssue expIssue) {
        List<String> values = new ArrayList<>(3
                + expIssue.getBoardConfig().getBoardColumns().size()
                + expIssue.getAttributes().size());
        values.addAll(Arrays.asList(expIssue.getKey(), expIssue.getLink(), expIssue.getName()));

        DateFormat df = new SimpleDateFormat(DEFAULT_DATETIME_FORMAT);

        for (BoardColumn boardColumn : expIssue.getBoardConfig().getBoardColumns()) {
            Date date = expIssue.getColumnTransitionsLog()[(int) boardColumn.getId()];
            values.add(date != null ? df.format(date) : "");
        }

        expIssue.getAttributes().forEach((k, v) -> {
            if (v instanceof String) {
                values.add(formatString((String) v));
            } else if (v instanceof List<?> && ((List<?>) v).size() > 0) {
                values.add("[" + ((List<String>) v).stream()
                        .map(this::formatString)
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
