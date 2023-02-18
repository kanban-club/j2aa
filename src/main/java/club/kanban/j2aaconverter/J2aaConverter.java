package club.kanban.j2aaconverter;

import club.kanban.j2aaconverter.adapters.Adapter;
import club.kanban.j2aaconverter.adapters.CsvAdapter;
import club.kanban.j2aaconverter.adapters.JsonAdapter;
import club.kanban.jirarestclient.Board;
import club.kanban.jirarestclient.BoardConfig;
import club.kanban.jirarestclient.BoardIssuesSet;
import club.kanban.jirarestclient.Issue;
import lombok.Getter;
import net.rcarz.jiraclient.JiraException;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class J2aaConverter {
    @Getter
    private List<ExportableIssue> exportableIssues;

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
            boardIssuesSet = board.getBoardIssuesSet(jqlSubFilter, startAt, 0, ExportableIssue.getHttpFields());

            if (boardIssuesSet.getIssues().size() > 0) {
                if (exportableIssues == null) exportableIssues = new ArrayList<>(boardIssuesSet.getTotal());

                // Map issue's changelog to board columns
                List<ExportableIssue> exportableIssuesSet = new ArrayList<>(boardIssuesSet.getIssues().size());
                for (Issue issue : boardIssuesSet.getIssues()) {
                    try {
                        ExportableIssue exportableIssue = ExportableIssue.fromIssue(issue, boardConfig);
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

            Adapter adapter;
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
