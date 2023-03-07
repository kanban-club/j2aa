package club.kanban.j2aa.jirarestclient;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import net.rcarz.jiraclient.JiraException;
import net.rcarz.jiraclient.RestClient;
import net.sf.json.JSONObject;

import java.util.List;

public class BoardColumn extends JiraResource {

    @Getter
    @Setter(value = AccessLevel.PROTECTED)
    protected long id;
    @Getter
    private List<BoardColumnStatus> statuses;

    /**
     * Creates a new Agile resource.
     *
     * @param restclient REST client instance
     * @param json JSON payload
     * @throws JiraException when the retrieval fails
     */
    public BoardColumn(RestClient restclient, JSONObject json) throws JiraException {
        super(restclient, json);
    }

    @Override
    protected void deserialize(JSONObject json) throws JiraException {
        super.deserialize(json);
        statuses = getResourceArray(BoardColumnStatus.class, json, getRestClient(), "statuses");
    }
}