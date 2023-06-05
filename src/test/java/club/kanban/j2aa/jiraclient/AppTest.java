package club.kanban.j2aa.jiraclient;

import club.kanban.j2aa.jiraclient.dto.Board;
import club.kanban.j2aa.jiraclient.dto.BoardIssuesPage;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.util.Arrays;

public class AppTest {
    @Test
    @Disabled
    void pagingTest() throws Exception {
        try (JiraClient jiraClient = JiraClient.connectTo("https://example.com", "xxx", "xxx")) {
            Board board = jiraClient.getBoard(35229).get();

            BoardIssuesPage page = null;
            int startAt = 0;
            do {
                if (page != null)
                    startAt = page.nextPageStartAt();
                page = jiraClient.getBoardIssuesPage(board, "created >= -12w", Arrays.asList("issuetype"), startAt, BoardIssuesPage.DEFAULT_MAX_RESULTS).block();
                System.out.println("Всего задач: " + page.getTotal());
                System.out.println("Всего на странице: " + page.getIssues().size());
            } while (page.hasNextPage());

        } catch (Exception e) {
            throw e;
        }

    }
}
