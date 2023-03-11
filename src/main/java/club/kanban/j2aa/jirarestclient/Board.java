package club.kanban.j2aa.jirarestclient;

import lombok.Getter;
import net.rcarz.jiraclient.JiraException;
import net.rcarz.jiraclient.RestClient;
import net.sf.json.JSON;
import net.sf.json.JSONObject;

import java.util.*;

public class Board extends JiraResource {
    @Getter
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
        board.boardConfig = get(restClient, BoardConfig.class, RESOURCE_URI + "board/" + id + "/configuration");
        return board;
    }

    public BoardIssuesSet getBoardIssuesSet(String jqlSubFilter,
                                            int startAt,
                                            int maxResults,
                                            String[] httpFields) throws JiraException {
        BoardIssuesSet boardIssuesSet;
        String url = RESOURCE_URI + "board/" + getId() + "/issue";

        Map<String, String> params = new HashMap<>();
        params.put("expand", "changelog");
        params.put("fields", String.join(",", httpFields));

        if (jqlSubFilter != null)
            params.put("jql", jqlSubFilter);

        if (maxResults > 0)
            params.put("maxResults", Integer.toString(maxResults));

        if (startAt > 0)
            params.put("startAt", Integer.toString(startAt));

        try {
            JSON result = getRestClient().get(url, params);
            List<Issue> issues = getResourceArray(Issue.class, result, getRestClient(), "issues");

            boardIssuesSet = new BoardIssuesSet(issues,
                    ((JSONObject) result).getInt("total"),
                    ((JSONObject) result).getInt("maxResults"));
        } catch (Exception ex) {
            throw new JiraException(String.format("Не удается получить issues из доски '%s'", url), ex);
        }

        return boardIssuesSet;
    }
}
