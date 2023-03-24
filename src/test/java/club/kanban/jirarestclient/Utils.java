package club.kanban.jirarestclient;

import club.kanban.j2aa.jirarestclient.*;
import net.rcarz.jiraclient.JiraException;
import net.rcarz.jiraclient.RestClient;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

public class Utils {

    public static <T extends JiraResource> T getTestJiraResource(Class<T> type, String file) throws IOException, JiraException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        JSONObject jsonObject = JSONObject.fromObject(Utils.getJSONObjectFromResource(file));
        Constructor<T> constructor = type.getDeclaredConstructor(RestClient.class, JSONObject.class);
        return constructor.newInstance(null, jsonObject);
    }

    public static BoardIssuesSet getTestBoardIssuesSet(String file) throws IOException, JiraException {
        // загрузка нескольких Issues в BoardIssuesSet
        JSONObject jsonIssuesSet = (JSONObject) JSONSerializer.toJSON(Utils.getJSONObjectFromResource(file));
        JSONArray jsonArray = jsonIssuesSet.getJSONArray("issues");
        List<Issue> issues = new ArrayList<>(jsonArray.size());
        for (int i = 0; i < jsonArray.size(); i++) {
            JSONObject jsonIssue = jsonArray.getJSONObject(i);
            issues.add(new Issue(null, jsonIssue));
        }
        return new BoardIssuesSet(issues, jsonIssuesSet.getInt("maxResults"), jsonIssuesSet.getInt("total"));
    }

    public static Board getTestBoard(String file) throws IOException, JiraException {
        JSONObject jsonObject = JSONObject.fromObject(Utils.getJSONObjectFromResource(file));
        return Board.newInstance(null, jsonObject);
    }

    public static BoardConfig getTestBoardConfig(String file) throws IOException, JiraException {
        JSONObject jsonObject = JSONObject.fromObject(Utils.getJSONObjectFromResource(file));
        return new BoardConfig(null, jsonObject);
    }

    public static JSONObject getJSONObjectFromResource(String file) throws IOException {
        JSONObject jsonObject = null;
        InputStream inputStream = ClassLoader.getSystemResourceAsStream(file);
        String s = IOUtils.toString(inputStream, "UTF-8");
        jsonObject = (JSONObject) JSONSerializer.toJSON(s);
        return jsonObject;
    }

    public static JSONObject getJSONObjectFromFile(String file) throws IOException {
        String input = FileUtils.readFileToString(new File(file), "UTF-8");
        JSONObject jsonObject = JSONObject.fromObject(input);
        return jsonObject;
    }

    public static JSONObject getTestIssue() {
        JSONObject jsonObject = (JSONObject) JSONSerializer.toJSON(
                "{\"expand\":\"renderedFields,names,schema,transitions,operations,editmeta,changelog\",\"id\":\"10742\",\"self\":\"https://brainbubble.atlassian.net/rest/api/latest/issue/10742\",\"key\":\"FILTA-43\",\"fields\":{\"progress\":{\"progress\":0,\"total\":0},\"summary\":\"Maintain Company Details\",\"timetracking\":{\"originalEstimate\":\"1w\",\"remainingEstimate\":\"2d\",\"timeSpent\":\"3d\",\"originalEstimateSeconds\":144000,\"remainingEstimateSeconds\":57600,\"timeSpentSeconds\":86400},\"issuetype\":{\"self\":\"https://brainbubble.atlassian.net/rest/api/2/issuetype/7\",\"id\":\"7\",\"description\":\"This is a test issue type.\",\"iconUrl\":\"https://brainbubble.atlassian.net/images/icons/issuetypes/story.png\",\"name\":\"Story\",\"subtask\":false},\"votes\":{\"self\":\"https://brainbubble.atlassian.net/rest/api/2/issue/FILTA-43/votes\",\"votes\":0,\"hasVoted\":false},\"resolution\":null,\"fixVersions\":[{\"self\":\"https://brainbubble.atlassian.net/rest/api/2/version/10200\",\"id\":\"10200\",\"description\":\"First Full Functional Build\",\"name\":\"1.0\",\"archived\":false,\"released\":false,\"releaseDate\":\"2022-12-01\"}],\"resolutiondate\":null,\"timespent\":86400,\"reporter\":{\"self\":\"https://brainbubble.atlassian.net/rest/api/2/user?username\\u003djoseph\",\"name\":\"joseph\",\"emailAddress\":\"joseph.b.mccarthy2012@googlemail.com\",\"avatarUrls\":{\"16x16\":\"https://secure.gravatar.com/avatar/a5a271f9eee8bbb3795f41f290274f8c?d\\u003dmm\\u0026s\\u003d16\",\"24x24\":\"https://secure.gravatar.com/avatar/a5a271f9eee8bbb3795f41f290274f8c?d\\u003dmm\\u0026s\\u003d24\",\"32x32\":\"https://secure.gravatar.com/avatar/a5a271f9eee8bbb3795f41f290274f8c?d\\u003dmm\\u0026s\\u003d32\",\"48x48\":\"https://secure.gravatar.com/avatar/a5a271f9eee8bbb3795f41f290274f8c?d\\u003dmm\\u0026s\\u003d48\"},\"displayName\":\"Joseph McCarthy\",\"active\":true},\"aggregatetimeoriginalestimate\":null,\"created\":\"2022-09-29T20:16:19.854+0100\",\"updated\":\"2022-10-09T22:24:55.961+0100\",\"description\":\"{panel:title\\u003dDescription|borderStyle\\u003ddashed|borderColor\\u003d#ccc|titleBGColor\\u003d#F7D6C1|bgColor\\u003d#FFFFCE}\\r\\nAs a company / admin\\r\\n\\r\\nI want to update the company details like contact details / name and so on\\r\\n\\r\\nSo that their details are up to date\\r\\n{panel}\\r\\n\\r\\n{panel:title\\u003dAcceptance Criteria|borderStyle\\u003ddashed|borderColor\\u003d#ccc|titleBGColor\\u003d#F7D6C1|bgColor\\u003d#FFFFCE}\\r\\nCan I change the company name?\\r\\nCan I change our emails, addresses and phone number etc?\\r\\nCan I change my invoicing details?\\r\\nCan I change our application service agreement?\\r\\n{panel}\",\"priority\":{\"self\":\"https://brainbubble.atlassian.net/rest/api/2/priority/3\",\"iconUrl\":\"https://brainbubble.atlassian.net/images/icons/priorities/major.png\",\"name\":\"Major\",\"id\":\"3\"},\"duedate\":null,\"customfield_10001\":null,\"customfield_10002\":null,\"customfield_10003\":null,\"issuelinks\":[],\"customfield_10004\":null,\"watches\":{\"self\":\"https://brainbubble.atlassian.net/rest/api/2/issue/FILTA-43/watchers\",\"watchCount\":0,\"isWatching\":false},\"worklog\":{\"startAt\":0,\"maxResults\":20,\"total\":0,\"worklogs\":[]},\"customfield_10000\":null,\"subtasks\":[],\"status\":{\"self\":\"https://brainbubble.atlassian.net/rest/api/2/status/10004\",\"description\":\"Issue is currently in progress.\",\"iconUrl\":\"https://brainbubble.atlassian.net/images/icons/statuses/open.png\",\"name\":\"To Do\",\"id\":\"10004\"},\"customfield_10007\":null,\"customfield_10006\":\"90\",\"labels\":[],\"customfield_10005\":null,\"workratio\":-1,\"assignee\":null,\"attachment\":[],\"customfield_10200\":null,\"aggregatetimeestimate\":null,\"project\":{\"self\":\"https://brainbubble.atlassian.net/rest/api/2/project/10501\",\"id\":\"10501\",\"key\":\"FILTA\",\"name\":\"Filta\",\"avatarUrls\":{\"16x16\":\"https://brainbubble.atlassian.net/secure/projectavatar?size\\u003dxsmall\\u0026pid\\u003d10501\\u0026avatarId\\u003d10307\",\"24x24\":\"https://brainbubble.atlassian.net/secure/projectavatar?size\\u003dsmall\\u0026pid\\u003d10501\\u0026avatarId\\u003d10307\",\"32x32\":\"https://brainbubble.atlassian.net/secure/projectavatar?size\\u003dmedium\\u0026pid\\u003d10501\\u0026avatarId\\u003d10307\",\"48x48\":\"https://brainbubble.atlassian.net/secure/projectavatar?pid\\u003d10501\\u0026avatarId\\u003d10307\"}},\"versions\":[],\"environment\":null,\"timeestimate\":144000,\"lastViewed\":\"2022-11-24T16:37:50.358+0000\",\"aggregateprogress\":{\"progress\":0,\"total\":0},\"components\":[{\"self\":\"https://brainbubble.atlassian.net/rest/api/2/component/10303\",\"id\":\"10303\",\"name\":\"Account Management\"},{\"self\":\"https://brainbubble.atlassian.net/rest/api/2/component/10301\",\"id\":\"10301\",\"name\":\"User Management\"}],\"comment\":{\"startAt\":0,\"maxResults\":1,\"total\":1,\"comments\":[{\"self\":\"https://brainbubble.atlassian.net/rest/api/2/issue/10742/comment/10500\",\"id\":\"10500\",\"author\":{\"self\":\"https://brainbubble.atlassian.net/rest/api/2/user?username\\u003djoseph\",\"name\":\"joseph\",\"emailAddress\":\"joseph.b.mccarthy2012@googlemail.com\",\"avatarUrls\":{\"16x16\":\"https://secure.gravatar.com/avatar/a5a271f9eee8bbb3795f41f290274f8c?d\\u003dmm\\u0026s\\u003d16\",\"24x24\":\"https://secure.gravatar.com/avatar/a5a271f9eee8bbb3795f41f290274f8c?d\\u003dmm\\u0026s\\u003d24\",\"32x32\":\"https://secure.gravatar.com/avatar/a5a271f9eee8bbb3795f41f290274f8c?d\\u003dmm\\u0026s\\u003d32\",\"48x48\":\"https://secure.gravatar.com/avatar/a5a271f9eee8bbb3795f41f290274f8c?d\\u003dmm\\u0026s\\u003d48\"},\"displayName\":\"Joseph McCarthy\",\"active\":true},\"body\":\"\\u0026#116;\\u0026#104;\\u0026#105;\\u0026#115;\\u0026#32;\\u0026#105;\\u0026#115;\\u0026#32;\\u0026#110;\\u0026#111;\\u0026#116;\\u0026#32;\\u0026#114;\\u0026#101;\\u0026#97;\\u0026#108;\\u0026#108;\\u0026#121;\\u0026#32;\\u0026#97;\\u0026#115;\\u0026#115;\\u0026#105;\\u0026#103;\\u0026#110;\\u0026#101;\\u0026#100;\\u0026#32;\\u0026#116;\\u0026#111;\\u0026#32;\\u0026#109;\\u0026#101;\\u0026#44;\\u0026#32;\\u0026#106;\\u0026#117;\\u0026#115;\\u0026#116;\\u0026#32;\\u0026#116;\\u0026#101;\\u0026#115;\\u0026#116;\\u0026#105;\\u0026#110;\\u0026#103;\\u0026#32;\\u0026#111;\\u0026#117;\\u0026#116;\\u0026#32;\\u0026#116;\\u0026#104;\\u0026#101;\\u0026#32;\\u0026#105;\\u0026#110;\\u0026#116;\\u0026#101;\\u0026#108;\\u0026#108;\\u0026#105;\\u0026#106;\\u0026#32;\\u0026#45;\\u0026#32;\\u0026#106;\\u0026#105;\\u0026#114;\\u0026#97;\\u0026#32;\\u0026#112;\\u0026#108;\\u0026#117;\\u0026#103;\\u0026#105;\\u0026#110;\\u0026#32;\\u0026#58;\\u0026#41;\",\"updateAuthor\":{\"self\":\"https://brainbubble.atlassian.net/rest/api/2/user?username\\u003djoseph\",\"name\":\"joseph\",\"emailAddress\":\"joseph.b.mccarthy2012@googlemail.com\",\"avatarUrls\":{\"16x16\":\"https://secure.gravatar.com/avatar/a5a271f9eee8bbb3795f41f290274f8c?d\\u003dmm\\u0026s\\u003d16\",\"24x24\":\"https://secure.gravatar.com/avatar/a5a271f9eee8bbb3795f41f290274f8c?d\\u003dmm\\u0026s\\u003d24\",\"32x32\":\"https://secure.gravatar.com/avatar/a5a271f9eee8bbb3795f41f290274f8c?d\\u003dmm\\u0026s\\u003d32\",\"48x48\":\"https://secure.gravatar.com/avatar/a5a271f9eee8bbb3795f41f290274f8c?d\\u003dmm\\u0026s\\u003d48\"},\"displayName\":\"Joseph McCarthy\",\"active\":true},\"created\":\"2022-10-09T22:14:54.979+0100\",\"updated\":\"2022-10-09T22:24:55.956+0100\"}]},\"timeoriginalestimate\":null,\"aggregatetimespent\":null},\"changelog\":{\"startAt\":0,\"maxResults\":30,\"total\":30,\"histories\":[{\"id\":\"12345679\",\"author\":{},\"created\":\"2022-12-13T21:23:04.659+0300\",\"items\":[{\"field\":\"Flagged\",\"fieldtype\":\"custom\",\"from\":\"\",\"fromString\":\"\",\"to\":\"[10000]\",\"toString\":\"Impediment\"}]},{\"id\":\"12345679\",\"author\":{},\"created\":\"2022-10-19T15:29:30.411+0300\",\"items\":[{\"field\":\"Flagged\",\"fieldtype\":\"custom\",\"from\":\"[10000]\",\"fromString\":\"Impediment\",\"to\":\"\",\"toString\":\"\"}]},{\"id\":\"12345678\",\"author\":{},\"created\":\"2022-10-19T15:29:30.411+0300\",\"items\":[{\"field\":\"status\",\"fieldtype\":\"jira\",\"from\":10001,\"fromString\":\"Backlog\",\"to\":\"10003\",\"toString\":\"To Do\"}]},{\"ic\":\"00000000\",\"author\":{},\"created\":\"2022-10-19T15:29:30.450+0300\",\"items\":[{\"field\":\"reporter\",\"fieldtype\":\"jira\",\"from\":\"Иванов Иван Иванович\",\"to\":\"petrov-pp\",\"toString\":\"Петров Петр Петрович\"}]}]}}"
        );
        return jsonObject;
    }

    public static JSONObject getTestBoard() {
        JSONObject jsonObject = (JSONObject) JSONSerializer.toJSON(
                "{\"id\":\"00000\",\"self\":\"https://jira.example.com/rest/agile/1.0/board/00000\",\"name\":\"Sample jira board\",\"type\":\"kanban\"}"
        );
        return jsonObject;
    }

    public static JSONObject getTestBoardConfig() {
        JSONObject jsonObject = (JSONObject) JSONSerializer.toJSON(
                "{\"id\":\"00000\",\"name\":\"Sample jira board\",\"type\":\"kanban\",\"self\":\"https://jira.example.com/rest/agile/1.0./board/00000/configuration\",\"filter\":{\"id\":\"000000\",\"self\":\"https://jira.example.com/rest/api/2/filter/000000\"},\"subQuery\":{\"query\":\"fixVersion in unreleasedVersions() OR fixVersion is EMPTY\"},\"columnConfig\":{\"columns\":[{\"name\":\"Backlog\",\"statuses\":[{\"id\":10001,\"self\":\"http://jira.example.com/rest/api/2/status/10001\"},{\"id\":10002,\"self\":\"http://jira.example.com/rest/api/2/status/10002\"}]},{\"name\":\"To Do\",\"statuses\":[{\"id\":10003,\"self\":\"http://jira.example.com/rest/api/2/status/10003\"},{\"id\":10004,\"self\":\"http://jira.example.com/rest/api/2/status/10004\"}]},{\"name\":\"In Progress\",\"statuses\":[{\"id\":10005,\"self\":\"http://jira.example.com/rest/api/2/status/10005\"},{\"id\":10006,\"self\":\"http://jira.example.com/rest/api/2/status/10006\"}]},{\"name\":\"Done\",\"statuses\":[{\"id\":10007,\"self\":\"http://jira.example.com/rest/api/2/status/10007\"},{\"id\":10008,\"self\":\"http://jira.example.com/rest/api/2/status/10008\"}]}],\"constraintType\":\"issueCount\"},\"ranking\":{\"rankingFieldId\":\"00000\"}}"
        );
        return jsonObject;
    }
}
