package club.kanban.j2aa.j2aaconverter;

import club.kanban.j2aa.j2aaconverter.adapters.AbstractAdapter;
import club.kanban.j2aa.j2aaconverter.adapters.CsvAdapter;
import club.kanban.j2aa.j2aaconverter.adapters.JsonAdapter;
import club.kanban.j2aa.jirarestclient.Board;
import club.kanban.j2aa.jirarestclient.BoardConfig;
import club.kanban.j2aa.jirarestclient.BoardIssuesSet;
import club.kanban.j2aa.jirarestclient.Issue;
import lombok.Getter;
import lombok.Setter;
import net.rcarz.jiraclient.JiraException;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class J2aaConverter {
    private final boolean USE_MAX_COLUMN;
    private final String[] HTTP_FIELDS;

    private final static String DEFAULT_USE_MAX_COLUMN =  "${use-max-column:false}";
    private final static String DEFAULT_HTTP_FIELDS =  "${http-fields:epic,components,key,issuetype,labels,status,created,priority}";

    @Getter
    @Setter
    private List<ExportableIssue> exportableIssues;

    /**
     *
     * @param useMaxColumn true - в качестве предельной колонки используется максимальный достигнутый issue статус
     *                     false - в качестве предельной колонки используется текущий статус issue
     * @param httpFields  поля для http запроса
     */
    public J2aaConverter(
            @Value(DEFAULT_USE_MAX_COLUMN) boolean useMaxColumn,
            @Value(DEFAULT_HTTP_FIELDS) String[] httpFields) {
        USE_MAX_COLUMN = useMaxColumn;
        HTTP_FIELDS = httpFields;
    }

    /**
     * Get set of issues for the board
     *
     * @param board           - Board
     * @param jqlSubFilter    - Sub Filter
     * @param progressMonitor - Callback procedure
     * @throws JiraException - Jira Exception
     */
    public void importFromJira(Board board, String jqlSubFilter, ProgressMonitor progressMonitor) throws JiraException {

        BoardConfig boardConfig = board.getBoardConfig();
        BoardIssuesSet boardIssuesSet;
        int startAt = 0;
        exportableIssues = null;
        do {
            boardIssuesSet = board.getBoardIssuesSet(jqlSubFilter, startAt, 0, HTTP_FIELDS);

            if (boardIssuesSet.getIssues().size() > 0) {
                if (exportableIssues == null)
                    exportableIssues = new ArrayList<>(boardIssuesSet.getTotal());

                // Map issue's changelog to board columns
                List<ExportableIssue> exportableIssuesSet = new ArrayList<>(boardIssuesSet.getIssues().size());
                for (Issue issue : boardIssuesSet.getIssues()) {
                    try {
                        ExportableIssue exportableIssue = ExportableIssue.fromIssue(
                                issue, boardConfig,
                                USE_MAX_COLUMN,
                                Arrays.asList(HTTP_FIELDS).contains("summary"));
                        exportableIssuesSet.add(exportableIssue);
                    } catch (Exception e) {
                        progressMonitor.update(String.format("Не удается конвертировать %s: %s\n", issue.getKey(), e.getMessage()));
                    }
                }

                exportableIssues.addAll(exportableIssuesSet);
                startAt += boardIssuesSet.getMaxResults();
                progressMonitor.update(String.format("%d из %d issues получено\n", exportableIssues.size(), boardIssuesSet.getTotal()));
            }
        } while (startAt < boardIssuesSet.getTotal()); // alternative (boardIssuesSet.getBoardIssues().size() > 0)
    }

    public void export2File(File outputFile) throws IOException {
        if (outputFile.getParentFile() != null) Files.createDirectories(outputFile.getParentFile().toPath());

        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(outputFile.getAbsoluteFile()), StandardCharsets.UTF_8)) {

            AbstractAdapter adapter;
            if (FilenameUtils.getExtension(outputFile.getName()).equalsIgnoreCase("json"))
                adapter = new JsonAdapter();
            else
                adapter = new CsvAdapter();

            writer.write(adapter.getPrefix());
            for (int i = 0; i < exportableIssues.size(); i++) {
                ExportableIssue exportableIssue = exportableIssues.get(i);

                if (i == 0)
                    writer.write(adapter.getHeaders(exportableIssue));

                writer.write(adapter.getValues(exportableIssue));
            }
            writer.write(adapter.getPostfix());
            writer.flush();
        }
    }
}
