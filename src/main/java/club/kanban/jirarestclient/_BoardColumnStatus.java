package club.kanban.jirarestclient;

import net.rcarz.jiraclient.JiraException;
import net.rcarz.jiraclient.RestClient;
import net.sf.json.JSONObject;

public class _BoardColumnStatus extends AgileResource {

    /**
     * Creates a new Agile resource.
     *
     * @param restclient REST client instance
     * @param json       JSON payload
     * @throws JiraException when the retrieval fails
     */
    public _BoardColumnStatus(RestClient restclient, JSONObject json) throws JiraException {
        super(restclient, json);
    }

    @Override
    void deserialize(JSONObject json) throws JiraException {
        super.deserialize(json);
    }
}
