package club.kanban.jirarestclient;

import lombok.Getter;
import lombok.Setter;
import net.rcarz.jiraclient.JiraException;
import net.rcarz.jiraclient.RestClient;
import net.sf.json.JSON;
import net.sf.json.JSONObject;

import java.util.*;

public class Board extends net.rcarz.jiraclient.agile.Board {
    @Getter @Setter
    private BoardConfig boardConfig;

    /**
     * Creates a Board from a JSON payload.
     *
     * @param restclient REST client instance
     * @param json       JSON payload
     */
    public Board(RestClient restclient, JSONObject json) throws JiraException {
        super(restclient, json);
    }

    public static Board get(RestClient restClient, long id) throws JiraException {
        Board board = get(restClient, Board.class, RESOURCE_URI + "board/" + id);
        board.setBoardConfig(get(restClient, BoardConfig.class, RESOURCE_URI + "board/" + id + "/configuration"));
        return board;
    }

    public BoardIssuesSet getBoardIssuesSet(String jqlSubFilter,
                                            int startAt,
                                            int maxResults) throws JiraException {

        BoardIssuesSet boardIssuesSet;
        String url = RESOURCE_URI + "board/" + getId() + "/issue";

        Map<String, String> params = new HashMap<>();
        params.put("expand", "changelog");
        params.put("fields", String.join(",", BoardIssue.getHttpFields()));

        if (jqlSubFilter != null)
            params.put("jql", jqlSubFilter);

        if (maxResults > 0)
            params.put("maxResults", Integer.toString(maxResults));

        if (startAt > 0)
            params.put("startAt", Integer.toString(startAt));

        try {
            JSON result = getRestClient().get(url, params);
            List<Issue> issues = getResourceArray(Issue.class, result, getRestClient(), "issues");

            // Map issue's changelog to board columns
            List<BoardIssue> boardIssues = new ArrayList<>(issues.size());
            for (Issue issue : issues) {
                try {
                    BoardIssue boardIssue = BoardIssue.createFromIssue(issue, boardConfig);
                    boardIssues.add(boardIssue);
                } catch (JiraException e) {
                    System.out.printf("%s: %s\n", issue.getKey(), e.getMessage());
                }
            }

            boardIssuesSet = new BoardIssuesSet(boardIssues,
                    ((JSONObject) result).getInt("total"),
                    ((JSONObject) result).getInt("maxResults"));
        } catch (Exception ex) {
            throw new JiraException("Failed to retreive a list of issues : " + url, ex);
        }

        return boardIssuesSet;
    }
}
