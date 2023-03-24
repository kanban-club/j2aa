package club.kanban.j2aa.jirarestclient;

import lombok.Getter;
import net.rcarz.jiraclient.JiraException;
import net.rcarz.jiraclient.RestClient;
import net.sf.json.JSON;
import net.sf.json.JSONObject;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Board extends JiraResource {
    @Getter
    private BoardConfig boardConfig;

    /**
     * Creates a Board from a JSON payload.
     *
     * @param restClient REST client instance
     * @param json       JSON payload
     */
    protected Board(RestClient restClient, JSONObject json) throws JiraException {
        super(restClient, json);
    }

    private static long getBoardId(URL url) throws MalformedURLException {
        Objects.requireNonNull(url);

        String strBoardId = null;
        String query = url.getQuery();
        if (query != null && !query.trim().isEmpty()) {
            String[] tokens = query.split("&");
            for (String token : tokens) {
                int i = token.indexOf("=");
                if (i > 0 && token.substring(0, i).trim().equalsIgnoreCase("rapidView")) {
                    strBoardId = token.substring(i + 1).trim();
                    break;
                }
            }
        }

        long boardId;
        try {
            Objects.requireNonNull(strBoardId);
            boardId = Long.parseLong(Objects.requireNonNull(strBoardId));
        } catch (Exception e) {
            throw new MalformedURLException(String.format("Адрес (%s) не содержит ссылки на доску (параметр rapidView)", url));
        }

        return boardId;
    }

    public static Board newInstance(RestClient restClient, URL url) throws JiraException, MalformedURLException {
        long id = Board.getBoardId(url);
        Board board = get(restClient, Board.class, RESOURCE_URI + "board/" + id);
        board.boardConfig = get(restClient, BoardConfig.class, RESOURCE_URI + "board/" + id + "/configuration");
        return board;
    }

    // TODO сделан только для тестов, возможно restClient стоит убрать и сделать null
    public static Board newInstance(RestClient restClient, JSONObject json) throws JiraException {
        return new Board(restClient, json);
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
