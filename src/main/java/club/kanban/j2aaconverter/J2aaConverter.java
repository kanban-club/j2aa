package club.kanban.j2aaconverter;

import club.kanban.jirarestclient.*;
import lombok.Getter;
import net.rcarz.jiraclient.JiraException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static club.kanban.j2aaconverter.CSVFormatter.formatString;

public class J2aaConverter {
    private BoardConfig boardConfig;
    @Getter
    private List<ExportableIssue> exportableIssues;

    /**
     * Get set of issues for the board
     * @param board - Board
     * @param jqlSubFilter - Sub Filter
     * @param progressMonitor - Callback procedure
     * @throws JiraException - Jira Exception
     */
    public void importFromJira(Board board, String jqlSubFilter, ProgressMonitor progressMonitor) throws JiraException {

        this.boardConfig = board.getBoardConfig();
        BoardIssuesSet boardIssuesSet;
        int startAt = 0;
        exportableIssues = null;
        do {
            boardIssuesSet = board.getBoardIssuesSet(jqlSubFilter, startAt, 0, ExportableIssue.getHttpFields());

            if (boardIssuesSet.getIssues().size() > 0) {
                if (exportableIssues == null)
                    exportableIssues = new ArrayList<>(boardIssuesSet.getTotal());

                // Map issue's changelog to board columns
                List<ExportableIssue> exportableIssuesSet= new ArrayList<>(boardIssuesSet.getIssues().size());
                for (Issue issue : boardIssuesSet.getIssues()) {
                    try {
                        ExportableIssue exportableIssue = ExportableIssue.createFromIssue(issue, boardConfig);
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
        if (outputFile.getParentFile() != null)
            Files.createDirectories(outputFile.getParentFile().toPath());

        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(outputFile.getAbsoluteFile()), StandardCharsets.UTF_8)) {

            for (int i = 0; i < exportableIssues.size(); i++) {
                ExportableIssue exportableIssue = exportableIssues.get(i);

                if (i == 0) {
                    // запись всего заголовка
                    String header = "ID,Link,Name";
                    for (BoardColumn boardColumn : boardConfig.getBoardColumns())
                        header = header.concat("," + formatString(boardColumn.getName()));
                    header += "," + String.join(",", exportableIssue.getAttributes().keySet());

                    writer.write(header);
                }

                DateFormat df = new SimpleDateFormat("MM/dd/yyyy");

                // запись строчек
                String row;

                row = exportableIssue.getKey() + "," + exportableIssue.getLink() + "," + exportableIssue.getName();

                for (BoardColumn boardColumn : boardConfig.getBoardColumns()) {
                    Date date = exportableIssue.getColumnTransitionsLog()[(int) boardColumn.getId()];
                    row = row.concat("," + (date != null ? df.format(date) : ""));
                }

                row += "," + String.join(",", new ArrayList<>(exportableIssue.getAttributes().values())
                );

                writer.append('\n');
                writer.append(row);
            }
            writer.flush();
        }
    }

}
