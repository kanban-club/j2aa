package club.kanban.jirarestclient;

import net.rcarz.jiraclient.JiraException;
import net.rcarz.jiraclient.RestClient;
import net.sf.json.JSONObject;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Board extends net.rcarz.javaclient.agile.Board {
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
        return get(restClient, Board.class, RESOURCE_URI + "board/" + id);
    }

    public BoardConfig getBoardConfig() throws JiraException {
        return get(getRestclient(), BoardConfig.class, RESOURCE_URI + "board/" + getId() + "/configuration");
    }

    public List<Issue> getAllIssuesForBoard(
            String jqlSubFilter, List<String> fields, Map<String, String> extraParams,
            @Nullable ProgressMonitor progressMonitor) throws JiraException {
        Map<String, String> params = new HashMap<>();

        if (jqlSubFilter != null) params.put("jql", jqlSubFilter);
        if (fields != null) params.put("fields", String.join(",", fields));
        if (extraParams != null) params.putAll(extraParams);

        String url = RESOURCE_URI + "board/" + getId() + "/issue";

        return AgileResource.list(getRestclient(), Issue.class, url, "issues", params, progressMonitor);
    }
}
