package club.kanban.jirarestclient;

import net.rcarz.jiraclient.JiraException;
import net.rcarz.jiraclient.RestClient;
import net.sf.json.JSON;
import net.sf.json.JSONObject;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class _AgileResource extends AgileResource {
    /**
     * Creates a new Agile resource.
     *
     * @param restclient REST client instance
     * @param json       JSON payload
     * @throws JiraException when the retrieval fails
     */
    public _AgileResource(RestClient restclient, JSONObject json) throws JiraException {
        super(restclient, json);
    }

    /**
     * Retrieves all boards visible to the session user.
     *
     * @param restclient REST client instance
     * @return a list of boards
     * @throws JiraException when the retrieval fails
     */
    static <T extends AgileResource> T get(RestClient restclient, Class<T> type, String url, Map<String, String> params) throws JiraException {

        JSON result;
        try {
            result = restclient.get(url, params);
        } catch (Exception ex) {
            throw new JiraException("Failed to retrieve " + type.getSimpleName() + " : " + url, ex);
        }

        return getResource(
                type,
                result,
                restclient
        );
    }

    static <T extends AgileResource> List<T> list(
            RestClient restClient, Class<T> type, String url,
            String listName, Map<String, String> params, @Nullable _ProgressMonitor progressMonitor) throws JiraException {

        List<T> resArray = null;
        JSON result;

        try {
            List<T> tmpArray;
            int maxResults = 0;
            int total = 0;
            int startAt = 0;

            if (progressMonitor != null) progressMonitor.update(0,0);
            do {
                params.put("startAt", Integer.toString(startAt));
                result = restClient.get(url, params);
                tmpArray = getResourceArray(type, result, restClient, listName);
                if (resArray == null) {
                    maxResults = ((JSONObject) result).getInt("maxResults");
                    total = ((JSONObject) result).getInt("total");
                    resArray = new ArrayList<>(total);
                }
                resArray.addAll(tmpArray);
                startAt += maxResults;
                if (progressMonitor != null) progressMonitor.update(resArray.size(), total);
            } while (startAt < total);
        } catch (Exception ex) {
            throw new JiraException("Failed to retreive a list of " + type.getSimpleName() + " : " + url, ex);
        }
        return resArray;
    }

}
