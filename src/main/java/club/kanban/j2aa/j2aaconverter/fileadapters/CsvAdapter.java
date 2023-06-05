package club.kanban.j2aa.j2aaconverter.fileadapters;

import club.kanban.j2aa.j2aaconverter.ConvertedIssue;
import club.kanban.j2aa.jiraclient.dto.boardconfig.columnconfig.column.Column;
import org.springframework.stereotype.Repository;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class CsvAdapter extends AbstractAdapter {
    private CsvAdapter() {
    }

    @Override
    public String getDefaultExtension() {
        return "csv";
    }

    @Override
    public String getDescription() {
        return "CSV files";
    }

    @Override
    public String getHeaders(ConvertedIssue convertedIssue) {
        List<Column> columns = convertedIssue.getConverter().getBoardConfig().getColumnConfig().getColumns();
        List<String> headers = new ArrayList<>(3 + columns.size() + convertedIssue.getAttributes().size());

        headers.addAll(Arrays.asList("ID", "Link", "Name"));
        for (Column column : columns) {
            headers.add(column.getName());
        }
        headers.addAll(convertedIssue.getAttributes().keySet());
        headers.addAll(Arrays.asList("Blocked Days", "Blocked"));

        return headers.stream()
                .map(Utils::escapeString)
                .collect(Collectors.joining(","));
    }

    @Override
    public String getValues(ConvertedIssue convertedIssue) {
        List<Column> columns = convertedIssue.getConverter().getBoardConfig().getColumnConfig().getColumns();
        List<String> values = new ArrayList<>(3 + columns.size() + convertedIssue.getAttributes().size());

        values.addAll(Arrays.asList(convertedIssue.getKey(),
                convertedIssue.getLink(),
                Utils.escapeString(convertedIssue.getName())));

        DateFormat df = new SimpleDateFormat(Utils.DEFAULT_DATETIME_FORMAT);
        for (int i = 0; i < columns.size(); i++) {
            Date date = convertedIssue.getColumnTransitionsLog()[i];
            values.add(date != null ? df.format(date) : "");
        }

        convertedIssue.getAttributes().forEach((k, v) -> {
            if (v instanceof String) {
                values.add(Utils.escapeString((String) v));
            } else if (v instanceof List<?> && ((List<?>) v).size() > 0) {
                values.add("[" + ((List<?>) v).stream()
                        .map(o -> Utils.escapeString(o.toString()))
                        .collect(Collectors.joining("|"))
                        + "]");
            } else {
                values.add("");
            }
        });

        values.add(String.valueOf(convertedIssue.getBlockedDays()));
        values.add(convertedIssue.isBlocked() ? "yes" : "no");

        return "\n" + String.join(",", values);
    }
}
