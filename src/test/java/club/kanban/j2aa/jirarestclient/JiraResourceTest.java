package club.kanban.j2aa.jirarestclient;

import net.rcarz.jiraclient.JiraException;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

//@ExtendWith(MockitoExtension.class)
class JiraResourceTest {

//    @Mock
//    private RestClient restClient;

    private JiraResource jiraResource;

    @BeforeEach
    void setUp() throws JiraException {

    }

    @Disabled
    @Test
    void get() {
    }

    @Test
    void getResource() throws JiraException {
        JSONObject jsonJiraResource =
                JSONObject.fromObject("{\"id\":\"00000\",\"self\":\"https://jira.example.com/rest/agile/1.0/board/00000\",\"name\":\"Sample jira resource\"}");

        JiraResource jiraResource = JiraResource.getResource(
                JiraResource.class,
                jsonJiraResource,
                null
        );
        assertEquals(Long.parseLong(jsonJiraResource.get("id").toString()), jiraResource.getId());
        assertEquals(jsonJiraResource.get("self"), jiraResource.getSelf());
        assertEquals(jsonJiraResource.get("name"), jiraResource.getName());

        // TODO Wrong JSON
//        JSONObject wrongJson = JSONObject.fromObject("{}");
//        assertThrows(JSONException.class, () -> jiraResource.getResource(
//                JiraResource.class,
//                wrongJson,
//                null
//        ));
    }

    @Disabled
    @Test
    void getResourceArray() {
    }

    @DisplayName("Парсинг json объекта в JiraResource")
    @Test
    void deserialize() throws JiraException {
        JSONObject jsonObject = Utils.getTestIssue();
        jiraResource = new JiraResource(null, jsonObject);

        // Happy flow
        jiraResource.deserialize(jsonObject);
        assertEquals(Long.parseLong(jsonObject.get("id").toString()), jiraResource.getId());
        assertEquals(jsonObject.get("name"), jiraResource.getName());
        assertEquals(jsonObject.get("self"), jiraResource.getSelf());
        assertEquals(jsonObject.get("id"), jiraResource.getAttribute("id"));

        // Wrong JSON string
        assertThrows(JSONException.class,
                () -> jiraResource.deserialize(JSONObject.fromObject("")));

        // TODO Skipped basic attributes
//        assertThrows(JiraException.class,
//                () -> jiraResource.deserialize(JSONObject.fromObject("{\"id\":1, \"name\":\"test\"}")));
//        assertThrows(JiraException.class,
//                () -> jiraResource.deserialize(JSONObject.fromObject("{\"id\":1, \"self\":\"https://jira.example.com\"}")));
//        assertThrows(JiraException.class,
//                () -> jiraResource.deserialize(JSONObject.fromObject("{\"name\":\"test\", \"self\":\"https://jira.example.com\"}")));
    }
}