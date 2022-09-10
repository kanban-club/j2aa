package club.kanban.jirarestclient;

import lombok.Getter;
import net.rcarz.jiraclient.JiraException;
import net.rcarz.jiraclient.RestClient;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class _Issue extends Issue {

    @Getter
    List<_Change> statusChanges;
    @Getter
    List<_Change> flaggedChanges;

    /**
     * Creates a new Agile Issue resource.
     *
     * @param restclient REST client instance
     * @param json       JSON payload
     */
    public _Issue(RestClient restclient, JSONObject json) throws JiraException {
        super(restclient, json);
    }

    @Override
    protected void deserialize(JSONObject json) throws JiraException {
        super.deserialize(json);

        statusChanges = new ArrayList<>(10);
        flaggedChanges = new ArrayList<>(10);

        JSONObject changelog = (JSONObject) json.get("changelog");
        if (changelog != null) {
            JSONArray histories = JSONArray.fromObject(changelog.get("histories"));
            if (histories != null) {
                for (int ih = histories.size() - 1; ih >= 0; ih--) {
                    JSONObject history = histories.getJSONObject(ih);
                    JSONArray items = JSONArray.fromObject(history.get("items"));
                    if (items != null) {
                        for (int i = 0; i < items.size(); i++) {
                            JSONObject item = items.getJSONObject(i);

                            if (item.getString("field").equals("status"))
                                statusChanges.add(_Change.get(history, item, 0));
                            else if ((item.getString("field")).equals("Flagged"))
                                flaggedChanges.add(_Change.get(history, item, _Change.SKIP_FROM | _Change.SKIP_TO));
                        }
                    }
                }
            }
        }
    }

    public static _Issue get(RestClient restClient, String key) throws JiraException {
        return _AgileResource.get(restClient, _Issue.class, RESOURCE_URI + "issue/" + key, new HashMap<String, String>() {{
            put("expand", "changelog");
        }});
    }
}
