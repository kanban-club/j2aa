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
import java.util.*;

import static club.kanban.j2aaconverter.CSVFormatter.*;

public class J2aaConverter {
    final static boolean SHOW_ISSUE_LINK = true;
    final static boolean SHOW_ISSUE_NAME = false;

    private BoardConfig boardConfig;
    @Getter
    private List<BoardIssue> boardIssues;

    public void importFromJira(Board board, String jqlSubFilter, ProgressMonitor progressMonitor) throws JiraException {
        // Get all Issues for the board

        List<String> fields;
        if (!SHOW_ISSUE_NAME)
            fields = Arrays.asList("epic", "components", "key", "issuetype", "labels", "status", "created", "priority");
        else
            fields = Arrays.asList("epic", "components", "key", "issuetype", "labels", "status", "created", "priority", "summary");

        List<Issue> issues = board.getAllIssuesForBoard(jqlSubFilter, fields, new HashMap<>() {{
            put("expand", "changelog");
        }}, progressMonitor);

        // Map issue's changelog to board columns
        this.boardConfig = board.getBoardConfig();
        boardIssues = new ArrayList<>(issues.size());
        for (Issue issue : issues) {
            try {
                BoardIssue boardIssue = BoardIssue.createFromIssue(issue, boardConfig);
                boardIssues.add(boardIssue);
            } catch (JiraException e) {
                System.out.printf("%s: %s%n", issue.getKey(), e.getMessage());
            }
        }
    }

    public void export2File(File outputFile) throws IOException {
        if (outputFile.getParentFile() != null)
            Files.createDirectories(outputFile.getParentFile().toPath());

        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(outputFile.getAbsoluteFile()), StandardCharsets.UTF_8)) {
            // запись всего заголовка
            String header = "ID,Link,Name";
            for (BoardColumn boardColumn : boardConfig.getBoardColumns())
                header = header.concat("," + formatString(boardColumn.getName()));
            header += ",Project,Type,Blocked Days,Labels,Priority,EpicKey,EpicName,Components";

            writer.write(header);

            // запись строчек
            DateFormat df = new SimpleDateFormat("MM/dd/yyyy");
            for (BoardIssue boardIssue : boardIssues) {
                String row;

                row = boardIssue.getKey()
                        + "," + (SHOW_ISSUE_LINK ? boardIssue.getLink() : "")
                        + "," + (SHOW_ISSUE_NAME ? formatString(boardIssue.getName()) : "");

                for (BoardColumn boardColumn : boardConfig.getBoardColumns()) {
                    Date date = boardIssue.getColumnTransitionsLog()[(int) boardColumn.getId()];
                    row = row.concat("," + (date != null ? df.format(date) : ""));
                }

//                row += "," + boardIssue.getProjectKey() + "," + boardIssue.getIssueTypeName() + "," + boardIssue.getBlockedDays() + "," + boardIssue.getLabels();
                row += "," + String.join(",", Arrays.asList(
                        boardIssue.getProjectKey(),
                        boardIssue.getIssueTypeName(),
                        boardIssue.getBlockedDays().toString(),
                        formatList(boardIssue.getLabels()),
                        boardIssue.getPriority(),
                        boardIssue.getEpicKey(),
                        formatString(boardIssue.getEpicName()),
                        formatList(boardIssue.getComponents())
                ));

                writer.append('\n');
                writer.append(row);
            }
            writer.flush();
        }
    }

}
