package club.kanban.jirarestclient;

import net.rcarz.jiraclient.JiraException;
import net.rcarz.jiraclient.RestClient;
import net.sf.json.JSONObject;

public class BoardColumnStatus extends JiraResource {

    /**
     * Creates a new Agile resource.
     *
     * @param restclient REST client instance
     * @param json       JSON payload
     * @throws JiraException when the retrieval fails
     */
    public BoardColumnStatus(RestClient restclient, JSONObject json) throws JiraException {
        super(restclient, json);
    }

    @Override
    protected void deserialize(JSONObject json) throws JiraException {
        super.deserialize(json);
    }
}
