package club.kanban.j2aa.jirarestclient;

import lombok.Getter;
import net.rcarz.jiraclient.JiraException;
import net.rcarz.jiraclient.RestClient;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

public class Issue extends JiraResource {
    @Getter
    private String key;
    @Getter
    private String summary;
    @Getter
    private Date created;
    @Getter
    private JiraResource priority;
    @Getter
    private JiraResource status;
    @Getter
    private JiraResource project;
    @Getter
    private JiraResource issueType;
    @Getter
    private JiraResource assignee;
    @Getter
    private JiraResource reporter;
    @Getter
    private Issue epic;
    @Getter
    private List<String> labels;
    @Getter
    private List<JiraResource> components;
    @Getter
    private List<JiraResource> fixVersions;
    @Getter
    List<ChangeLogItem> statusChanges;
    @Getter
    List<ChangeLogItem> flaggedChanges;

    /**
     * Creates a new Agile Issue resource.
     *
     * @param restClient REST client instance
     * @param json       JSON payload
     */
    public Issue(RestClient restClient, JSONObject json) throws JiraException {
        super(restClient, json);
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
                            created = Field.getDateTime(jsonFields.get(jsonKey));
                            break;
                        case "priority":
                            priority = getResource(JiraResource.class, jsonFields.getJSONObject(jsonKey), getRestClient());
                            break;
                        case "status":
                            status = getResource(JiraResource.class, jsonFields.getJSONObject(jsonKey), getRestClient());
                            break;
                        case "project":
                            project = getResource(JiraResource.class, jsonFields.getJSONObject(jsonKey), getRestClient());
                            break;
                        case "issuetype":
                            issueType = getResource(JiraResource.class, jsonFields.getJSONObject(jsonKey), getRestClient());
                            break;
                        case "assignee":
                            assignee = getResource(JiraResource.class, jsonFields.getJSONObject(jsonKey), getRestClient());
                            break;
                        case "reporter":
                            reporter = getResource(JiraResource.class, jsonFields.getJSONObject(jsonKey), getRestClient());
                            break;
                        case "epic":
//                            epic = new Issue(getRestClient(), jsonFields.getJSONObject(jsonKey));
                            epic = getResource(Issue.class, jsonFields.getJSONObject(jsonKey), getRestClient());
                            break;
                        case "labels":
                            labels = getResourceArray(String.class, jsonFields, getRestClient(), jsonKey);
                            break;
                        case "components":
                            components = getResourceArray(JiraResource.class, jsonFields, getRestClient(), jsonKey);
                            break;
                        case "fixVersions":
                            fixVersions = getResourceArray(JiraResource.class, jsonFields, getRestClient(), jsonKey);
                            break;
                    }
                } catch (Exception ignored) {
                }
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
