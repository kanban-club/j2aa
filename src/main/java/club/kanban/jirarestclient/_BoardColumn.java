package club.kanban.jirarestclient;

import lombok.Getter;
import lombok.Setter;
import net.rcarz.jiraclient.JiraException;
import net.rcarz.jiraclient.RestClient;
import net.sf.json.JSONObject;

import java.util.List;

public class _BoardColumn extends AgileResource {

    @Getter
    @Setter
    private long id;
    @Getter
    private List<_BoardColumnStatus> statuses;

    /**
     * Creates a new Agile resource.
     *
     * @param restclient REST client instance
     * @param json JSON payload
     * @throws JiraException when the retrieval fails
     */
    _BoardColumn(RestClient restclient, JSONObject json) throws JiraException {
        super(restclient, json);
    }

    @Override
    void deserialize(JSONObject json) throws JiraException {
        super.deserialize(json);
        statuses = getResourceArray(_BoardColumnStatus.class, json, getRestclient(), "statuses");
    }
}
