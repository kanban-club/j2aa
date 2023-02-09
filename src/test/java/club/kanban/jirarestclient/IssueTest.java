package club.kanban.jirarestclient;

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
        board = new Board(null, Utils.getJSONObjectFromResource("testBoard.json"));
        board.setBoardConfig(boardConfig);

        // прочитать Issue
        issue = new Issue(null, Utils.getJSONObjectFromResource("testIssue.json"));

        // конвертировать Issue в BoardIssue
        BoardIssue boardIssue = BoardIssue.createFromIssue(issue, boardConfig);

        // загрузка нескольких Issues в BoardIssuesSet
        JSONObject jsonIssuesSet = (JSONObject) JSONSerializer.toJSON(Utils.getJSONObjectFromResource("testIssuesSet.json"));
        JSONArray jsonArray = jsonIssuesSet.getJSONArray("issues");
        List<BoardIssue> boardIssues = new ArrayList<>(jsonArray.size());
        for (int i = 0; i < jsonArray.size(); i++) {
            JSONObject jsonIssue = jsonArray.getJSONObject(i);
            boardIssues.add(BoardIssue.createFromIssue(new Issue(null, jsonIssue), boardConfig));
        }
        boardIssuesSet = new BoardIssuesSet(boardIssues, jsonIssuesSet.getInt("maxResults"), jsonIssuesSet.getInt("total"));
    }

    @Test
    void getStatusChanges() {
        assertEquals("FILTA-43", issue.getKey());
    }
}