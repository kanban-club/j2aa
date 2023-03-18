package club.kanban.j2aa.j2aaconverter;

import club.kanban.j2aa.j2aaconverter.fileadapters.FileAdapter;
import club.kanban.j2aa.j2aaconverter.fileadapters.FileAdapterFactory;
import club.kanban.j2aa.jirarestclient.Board;
import club.kanban.j2aa.jirarestclient.BoardConfig;
import club.kanban.j2aa.jirarestclient.BoardIssuesSet;
import club.kanban.j2aa.jirarestclient.Issue;
import lombok.Getter;
import lombok.Setter;
import net.rcarz.jiraclient.JiraException;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

@Service
public class J2aaConverter {
    private static final Logger logger = LoggerFactory.getLogger(J2aaConverter.class);
    @Getter
    private final String[] httpFields;
    @Getter
    private final Boolean useMaxColumn;
    @Getter
    private BoardConfig boardConfig;

    private final static String DEFAULT_USE_MAX_COLUMN = "${converter.use-max-column:false}";
    private final static String DEFAULT_HTTP_FIELDS = "${converter.jira-fields:issuetype,labels,epic}";
    private final static String[] REQUIRED_HTTP_FIELDS = {"status", "created"};

    @Getter
    @Setter
    private List<ExportableIssue> exportableIssues;

    /**
     * @param useMaxColumn true - в качестве предельной колонки используется максимальный достигнутый issue статус
     *                     false - в качестве предельной колонки используется текущий статус issue
     * @param httpFields   поля для http запроса
     */
    public J2aaConverter(
            @Value(DEFAULT_HTTP_FIELDS) String[] httpFields,
            @Value(DEFAULT_USE_MAX_COLUMN) boolean useMaxColumn
    ) {
        this.useMaxColumn = useMaxColumn;
        this.httpFields = httpFields;
    }

    /**
     * Get set of issues for the board
     *
     * @param board        - Board
     * @param jqlSubFilter - Sub Filter
     * @throws JiraException - Jira Exception
     */
    public void importFromJira(Board board, String jqlSubFilter) throws JiraException, InterruptedException {
        boardConfig = board.getBoardConfig();
        exportableIssues = null;

        BoardIssuesSet boardIssuesSet;
        int startAt = 0;
        do {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }
            String[] actualHttpFields = new String[REQUIRED_HTTP_FIELDS.length + httpFields.length];
            System.arraycopy(REQUIRED_HTTP_FIELDS, 0, actualHttpFields, 0, REQUIRED_HTTP_FIELDS.length);
            System.arraycopy(httpFields, 0, actualHttpFields, REQUIRED_HTTP_FIELDS.length, httpFields.length);

            boardIssuesSet = board.getBoardIssuesSet(jqlSubFilter, startAt, 0, actualHttpFields);

//            if (boardIssuesSet.getIssues().size() > 0) { // TODO удалить закомментиованный код
            if (exportableIssues == null)
                exportableIssues = new ArrayList<>(boardIssuesSet.getTotal());

            // Map issue's changelog to board columns
            List<ExportableIssue> exportableIssuesSet = new ArrayList<>(boardIssuesSet.getIssues().size());
            for (Issue issue : boardIssuesSet.getIssues()) {
                try {
                    ExportableIssue exportableIssue = ExportableIssue.fromIssue(this, issue);
                    exportableIssuesSet.add(exportableIssue);
                } catch (Exception e) {
                    logger.info(String.format("Не удается конвертировать %s: %s", issue.getKey(), e.getMessage()));
                }
            }

            exportableIssues.addAll(exportableIssuesSet);
            startAt += boardIssuesSet.getMaxResults();
            logger.info(String.format("%d из %d issues получено", exportableIssues.size(), boardIssuesSet.getTotal()));
//            }
        } while (startAt < boardIssuesSet.getTotal()); // alternative (boardIssuesSet.getBoardIssues().size() > 0)
    }

    public void export2File(File outputFile) throws IOException {
        if (outputFile.getParentFile() != null) Files.createDirectories(outputFile.getParentFile().toPath());

        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(outputFile.getAbsoluteFile()), StandardCharsets.UTF_8)) {

            FileAdapter adapter = FileAdapterFactory.getAdapter(FilenameUtils.getExtension(outputFile.getName()));

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
