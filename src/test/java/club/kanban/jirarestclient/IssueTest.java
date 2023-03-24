package club.kanban.jirarestclient;

import club.kanban.j2aa.j2aaconverter.ExportableIssue;
import club.kanban.j2aa.jirarestclient.Board;
import club.kanban.j2aa.jirarestclient.BoardConfig;
import club.kanban.j2aa.jirarestclient.BoardIssuesSet;
import club.kanban.j2aa.jirarestclient.Issue;
import net.rcarz.jiraclient.JiraException;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IssueTest {
    Issue issue;
    Board board;
    BoardConfig boardConfig;
    BoardIssuesSet boardIssuesSet;

    @BeforeEach
    void setUp() throws JiraException, IOException {
        // прочитать конфигурацию доски
        boardConfig = new BoardConfig(null, Utils.getJSONObjectFromResource("testBoardConfig.json"));

        // прочитать доску
        board = Board.newInstance(null, Utils.getJSONObjectFromResource("testBoard.json"));

        // прочитать Issue
        issue = new Issue(null, Utils.getJSONObjectFromResource("testIssue.json"));

        // конвертировать Issue в BoardIssue
//        ExportableIssue exportableIssue = ExportableIssue.newInstance(null, issue);

        // загрузка нескольких Issues в BoardIssuesSet
        JSONObject jsonIssuesSet = (JSONObject) JSONSerializer.toJSON(Utils.getJSONObjectFromResource("testIssuesSet.json"));
        JSONArray jsonArray = jsonIssuesSet.getJSONArray("issues");
        List<Issue> issues = new ArrayList<>(jsonArray.size());
        for (int i = 0; i < jsonArray.size(); i++) {
            JSONObject jsonIssue = jsonArray.getJSONObject(i);
            issues.add(new Issue(null, jsonIssue));
        }
        boardIssuesSet = new BoardIssuesSet(issues, jsonIssuesSet.getInt("maxResults"), jsonIssuesSet.getInt("total"));
    }

    @Test
    void getStatusChanges() {
        assertEquals("FILTA-43", issue.getKey());
    }
}