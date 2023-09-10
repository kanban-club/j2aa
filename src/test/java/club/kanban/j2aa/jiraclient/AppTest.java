package club.kanban.j2aa.jiraclient;

import club.kanban.j2aa.jiraclient.dto.Board;
import club.kanban.j2aa.jiraclient.dto.BoardIssuesPage;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.Arrays;
import java.util.List;

public class AppTest {
    @Test
    @Disabled
    void pagingTest() throws Exception {
        try (JiraClient jiraClient = JiraClient
                .builder(new URL("https://example.com"), "xxx", "xxx")
                .build()) {
            Board board = jiraClient.getBoard(35229).get();

            BoardIssuesPage page = null;
            int startAt = 0;
            do {
                if (page != null)
                    startAt = page.nextPageStartAt();
                page = jiraClient.getBoardIssuesPage(board, "created >= -12w", List.of("issuetype"), startAt, BoardIssuesPage.DEFAULT_MAX_RESULTS).block();
                System.out.println("Всего задач: " + page.getTotal());
                System.out.println("Всего на странице: " + page.getIssues().size());
            } while (page.hasNextPage());

        }

    }
}
