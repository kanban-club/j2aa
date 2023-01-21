package club.kanban.jirarestclient;

import lombok.Getter;
import lombok.Setter;
import net.rcarz.jiraclient.JiraException;
import net.rcarz.jiraclient.RestClient;
import net.sf.json.JSON;
import net.sf.json.JSONObject;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class Board extends net.rcarz.javaclient.agile.Board {
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

//    public BoardConfig getBoardConfig() throws JiraException {
//        return get(getRestclient(), BoardConfig.class, RESOURCE_URI + "board/" + getId() + "/configuration");
//    }

//    public List<Issue> getAllIssuesForBoard(
//            String jqlSubFilter, List<String> fields, Map<String, String> extraParams,
//            @Nullable ProgressMonitor progressMonitor) throws JiraException {
//        Map<String, String> params = new HashMap<>();
//
//        if (jqlSubFilter != null) params.put("jql", jqlSubFilter);
//        if (fields != null) params.put("fields", String.join(",", fields));
//        if (extraParams != null) params.putAll(extraParams);
//
//        String url = RESOURCE_URI + "board/" + getId() + "/issue";
//
//        return AgileResource.list(getRestclient(), Issue.class, url, "issues", params, progressMonitor);
//    }

    public BoardIssuesSet getBoardIssuesSet(String jqlSubFilter,
                                            int startAt,
                                            int maxResults,
                                            boolean useIssueSummary) throws JiraException {

        BoardIssuesSet boardIssuesSet;
        String url = RESOURCE_URI + "board/" + getId() + "/issue";

        Map<String, String> params = new HashMap<>();
        params.put("expand", "changelog");

        if (jqlSubFilter != null)
            params.put("jql", jqlSubFilter);

        if (!useIssueSummary)
            params.put("fields", String.join(",", Arrays.asList("epic", "components", "key", "issuetype", "labels", "status", "created", "priority")));
        else
           params.put("fields", String.join(",", Arrays.asList("epic", "components", "key", "issuetype", "labels", "status", "created", "priority", "summary")));

        if (maxResults > 0)
            params.put("maxResults", Integer.toString(maxResults));

        if (startAt > 0)
            params.put("startAt", Integer.toString(startAt));

        try {
            JSON result = getRestclient().get(url, params);
            List<Issue> issues = getResourceArray(Issue.class, result, getRestclient(), "issues");

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
