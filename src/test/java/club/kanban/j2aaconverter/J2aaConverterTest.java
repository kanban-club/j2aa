package club.kanban.j2aaconverter;

import club.kanban.jirarestclient.BoardConfig;
import club.kanban.jirarestclient.BoardIssuesSet;
import club.kanban.jirarestclient.Issue;
import club.kanban.jirarestclient.Utils;
import net.rcarz.jiraclient.JiraException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class J2aaConverterTest {

    J2aaConverter converter;

    @BeforeEach
    void setUp() throws IOException, JiraException, InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        converter = new J2aaConverter();
        BoardConfig boardConfig = Utils.getTestJiraResource(BoardConfig.class, "testBoardConfig.json");
        BoardIssuesSet boardIssuesSet = Utils.getTestBoardIssuesSet("testIssuesSet.json");

        List<ExportableIssue> exportableIssues = new ArrayList<>(boardIssuesSet.getIssues().size());
        for (Issue issue : boardIssuesSet.getIssues()) {
            ExportableIssue exportableIssue = ExportableIssue.fromIssue(issue, boardConfig);
            exportableIssues.add(exportableIssue);
        }
        converter.setExportableIssues(exportableIssues);
    }

    @Test
    void export2File() throws IOException {
        converter.export2File(new File("target/csv/output.csv"));
        converter.export2File(new File("target/csv/output.json"));
    }
}