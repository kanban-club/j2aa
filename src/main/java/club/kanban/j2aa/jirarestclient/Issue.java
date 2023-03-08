package club.kanban.j2aa.jirarestclient;

import lombok.Getter;
import net.rcarz.jiraclient.Field;
import net.rcarz.jiraclient.JiraException;
import net.rcarz.jiraclient.RestClient;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

public class Issue extends JiraResource {
    @Getter private String key;
    @Getter private String summary;
    @Getter private Date created;
    @Getter private JiraResource priority;
    @Getter private JiraResource status;
    @Getter private JiraResource project;
    @Getter private JiraResource issueType;
    @Getter private JiraResource assignee;
    @Getter private JiraResource reporter;
    @Getter private Issue epic;
    @Getter private List<String> labels;
    @Getter private List<JiraResource> components;
    @Getter List<ChangeLogItem> statusChanges;
    @Getter List<ChangeLogItem> flaggedChanges;

    /**
     * Creates a new Agile Issue resource.
     *
     * @param restclient REST client instance
     * @param json       JSON payload
     */
    public Issue(RestClient restclient, JSONObject json) throws JiraException {
        super(restclient, json);
    }

    @Override
    protected void deserialize(JSONObject json) throws JiraException {
        super.deserialize(json);
        key = Field.getString(json.get("key"));

        // Extract from "fields" sub JSONObject
        if (json.containsKey("fields")) {
            JSONObject jsonFields = (JSONObject) json.get("fields");

            Iterator<String> keys = jsonFields.keys();
            while (keys.hasNext()) {
                String jsonKey = keys.next();

                try {
                    switch (jsonKey) {
                        case "summary":
                            summary = Field.getString(jsonFields.get(jsonKey));
                            break;
                        case "created":
                            created = getDateTime(jsonFields.get(jsonKey));
                            break;
                        case "priority":
                            priority = new JiraResource(getRestClient(), jsonFields.getJSONObject(jsonKey));
                            break;
                        case "status":
                            status = new JiraResource(getRestClient(), jsonFields.getJSONObject(jsonKey));
                            break;
                        case "project":
                            project = new JiraResource(getRestClient(), jsonFields.getJSONObject(jsonKey));
                            break;
                        case "issuetype":
                            issueType = new JiraResource(getRestClient(), jsonFields.getJSONObject(jsonKey));
                            break;
                        case "assignee":
                            assignee = new JiraResource(getRestClient(), jsonFields.getJSONObject(jsonKey));
                            break;
                        case "reporter":
                            reporter = new JiraResource(getRestClient(), jsonFields.getJSONObject(jsonKey));
                            break;
                        case "epic":
                            epic = new Issue(getRestClient(), jsonFields.getJSONObject(jsonKey));
                            break;
                        case "labels":
                            labels = new ArrayList<>(((JSONArray) jsonFields.get(jsonKey)).size());
                            for (Object label : (JSONArray) jsonFields.get(jsonKey)) {
                                labels.add((String) label);
                            }
                            break;
                        case "components":
                            Object objArray = jsonFields.get(jsonKey);
                            if (((JSONArray) objArray).size() > 0) {
                                components = new ArrayList<>(((JSONArray) objArray).size());
                                for (int i = 0; i < ((JSONArray) objArray).size(); i++) {
                                    JiraResource component = new JiraResource(getRestClient(), ((JSONArray) objArray).getJSONObject(i));
                                    components.add(component);
                                }
                            }
                            break;
                    }
                } catch (net.sf.json.JSONException ignored) {}
            }

            addAttributes(jsonFields);
        }

        // Extract Statuses & Blockers
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
                                statusChanges.add(ChangeLogItem.get(history, item, 0));
                            else if ((item.getString("field")).equals("Flagged"))
                                flaggedChanges.add(ChangeLogItem.get(history, item, ChangeLogItem.SKIP_FROM | ChangeLogItem.SKIP_TO));
                        }
                    }
                }
            }
        }
    }
}
