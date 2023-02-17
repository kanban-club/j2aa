package club.kanban.j2aa;

import club.kanban.j2aaconverter.ExportableIssue;
import club.kanban.jirarestclient.Board;
import club.kanban.jirarestclient.BoardIssuesSet;
import net.rcarz.jiraclient.BasicCredentials;
import net.rcarz.jiraclient.JiraClient;
import net.rcarz.jiraclient.JiraException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class J2aaService {
    public static void main(String[] args) {
        SpringApplication.run(J2aaService.class, args);
    }

    @RequestMapping(value = "/issues", method = RequestMethod.POST)
    public BoardIssuesSet getBoardIssuesSet(
            @RequestParam(defaultValue = "0") int startAt,
            @RequestParam(defaultValue = "0") int maxResults,
            @RequestParam(defaultValue = "22232") int boardId,
            @RequestParam(defaultValue = "createdDate >= -12w") String jqlSubFilter,
            @RequestParam(defaultValue = "http://jiraserver") String jiraUrl,
            String username,
            String password
    ) throws JiraException {
        JiraClient jiraClient = new JiraClient(jiraUrl, new BasicCredentials(username, password));
        Board board = Board.get(jiraClient.getRestClient(), boardId);
        return board.getBoardIssuesSet(jqlSubFilter, startAt, maxResults, ExportableIssue.getHttpFields());
    }
}
