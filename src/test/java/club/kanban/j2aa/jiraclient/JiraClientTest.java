package club.kanban.j2aa.jiraclient;

import club.kanban.j2aa.jiraclient.dto.Board;
import club.kanban.j2aa.jiraclient.dto.BoardIssuesPage;
import club.kanban.j2aa.jiraclient.dto.auth.UserCredentials;
import club.kanban.j2aa.jiraclient.dto.boardconfig.BoardConfig;
import club.kanban.j2aa.jiraclient.dto.issue.Issue;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.net.*;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class JiraClientTest {
    private static final String USERNAME = "username";
    private static final String PASSWORD = "password";
    private static final String SESSION_ID = UUID.randomUUID().toString();
    private static final long BOARD_ID = 1;

    private static MockWebServer server;
    private JiraClient jiraClient;
    private URL jiraUrl;

    private static String BOARD_JSON_STUB;
    private static String BOARD_CONFIGURATION_JSON_STUB;
    private static String BOARD_ISSUES_JSON_STUB;

    static {
        try {
            BOARD_JSON_STUB = new String(JiraClient.class.getClassLoader().getResourceAsStream("stubs/board.json").readAllBytes());
            BOARD_CONFIGURATION_JSON_STUB = new String(JiraClient.class.getClassLoader().getResourceAsStream("stubs/boardconfig.json").readAllBytes());
            BOARD_ISSUES_JSON_STUB = new String(JiraClient.class.getClassLoader().getResourceAsStream("stubs/issueset.json").readAllBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @BeforeAll
    static void beforeAll() throws IOException {
        server = new MockWebServer();

        final Dispatcher dispatcher = new Dispatcher() {

            @Override
            public MockResponse dispatch(RecordedRequest request) {
                try {
                    URI uri = new URI(request.getPath());
                    switch (Objects.requireNonNull(uri.getPath())) {
                        case "/rest/agile/1.0/board/" + BOARD_ID:
                            return new MockResponse().setResponseCode(200).setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON).setBody(BOARD_JSON_STUB);
                        case "/rest/agile/1.0/board/" + BOARD_ID + "/configuration":
                            return new MockResponse().setResponseCode(200).setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON).setBody(BOARD_CONFIGURATION_JSON_STUB);
                        case "/rest/agile/1.0/board/" + BOARD_ID + "/issue":
                            return new MockResponse().setResponseCode(200).setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON).setBody(BOARD_ISSUES_JSON_STUB);
                        case "/rest/auth/1/session":
                            if (Objects.equals(request.getMethod(), "POST")) {
                                try {
                                    UserCredentials userCredentials = new ObjectMapper().readValue(request.getBody().inputStream(), UserCredentials.class);
                                    if (!userCredentials.getUsername().equalsIgnoreCase(USERNAME) || !userCredentials.getPassword().equals(PASSWORD))
                                        throw new Exception("");
                                } catch (Exception e) {
                                    return new MockResponse().setResponseCode(401);
                                }
                                return new MockResponse()
                                        .setResponseCode(200)
                                        .setHeader(HttpHeaders.SET_COOKIE, new HttpCookie("JSESSIONID", SESSION_ID))
                                        .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON).setBody(String.format("{ \"session\": {\"name\": \"JSESSIONID\", \"value\": \"%s\"}}", SESSION_ID));
                            } else if (request.getMethod().equals("DELETE"))
                                return new MockResponse().setResponseCode(204);
                    }
                } catch (URISyntaxException e) {
                    e.printStackTrace();
                }
                return new MockResponse().setResponseCode(403);
            }
        };
        server.setDispatcher(dispatcher);
        server.start();
    }

    @AfterAll
    static void afterAll() throws IOException {
        server.shutdown();
    }

    @BeforeEach
    void setUp() throws MalformedURLException {
        jiraUrl = new URL(String.format("http://localhost:%d", server.getPort()));
        jiraClient = JiraClient.builder(jiraUrl, USERNAME, PASSWORD).build();
    }

    @Test
    void connectTo() {
        var client = JiraClient.builder(jiraUrl, USERNAME, PASSWORD).build();
        assertNotNull(client);
        assertEquals(SESSION_ID, client.getSessionId());
        assertEquals(jiraUrl, client.getServerUrl());

        //Проверяем неверный адрес сервера
        Throwable exception = assertThrows(JiraException.class,
                () -> JiraClient.builder(new URL("https://unknown-server.ru"), USERNAME, PASSWORD).build());
        assertEquals(UnknownHostException.class, exception.getCause().getClass());

        //Проверяем неверное имя пользователя или пароль
        exception = assertThrows(JiraException.class,
                () -> JiraClient.builder(jiraUrl, "", PASSWORD).build());
        assertEquals(WebClientResponseException.Unauthorized.class, exception.getCause().getClass());

        exception = assertThrows(JiraException.class,
                () -> JiraClient.builder(jiraUrl, USERNAME, "").build());
        assertEquals(WebClientResponseException.Unauthorized.class, exception.getCause().getClass());
    }

    @Test
    void close() {
//        System.out.println("Установленная сессия (SessionID): " + jiraClient.getSessionId());
//        Board board = jiraClient.getBoard(BOARD_ID).get();
//        System.out.println("Название доски: " + board.getName());
//        System.out.println("Делаем logout");
        assertDoesNotThrow(() -> jiraClient.close());

//        System.out.println("Првоеряем доступность ресурсов после logout");
//        Throwable e = assertThrows(WebClientResponseException.Unauthorized.class, () -> jiraClient.getBoard(BOARD_ID));
//        System.out.println(e.getMessage());
    }

    @Test
    void getBoard() {
        Board board = jiraClient.getBoard(BOARD_ID).get();
        assertEquals(BOARD_ID, board.getId());
    }

    @Test
    void getBoardConfig() {
        BoardConfig boardConfig = jiraClient.getBoardConfig(BOARD_ID).get();
        assertEquals(BOARD_ID, boardConfig.getId());
    }

    @Test
    void pagingTest() {
        Optional<Board> optionalBoard = jiraClient.getBoard(BOARD_ID);
        optionalBoard.ifPresentOrElse(
                board -> {
                    BoardIssuesPage page = jiraClient.getBoardIssuesPage(
                            board, "created > -1w", null, 0, BoardIssuesPage.DEFAULT_MAX_RESULTS).block();
                    assert page != null;
                    List<Issue> issues = page.getIssues();
                    if (issues != null) {
                        issues.forEach(System.out::println);
                        System.out.printf("Total: %d\n", issues.size());
                    } else
                        System.out.println("Список = null");
                }
                , () -> System.out.println("Доска не найдена"));
    }

    @Test
    @Disabled
    void fluxTest() {
        try {
            Optional<Board> optionalBoard = jiraClient.getBoard(BOARD_ID);
            optionalBoard.ifPresentOrElse(b -> {
//                        List<Issue> issues = jiraRepository.getBoardIssues(authToken, b, "created > -12w", null).block();
//                        System.out.println(issues);

                Flux<Issue> response = jiraClient.getBoardIssuesFlux(b, "created > -2w", null);

                AtomicInteger count = new AtomicInteger(0);
                AtomicBoolean interrupted = new AtomicBoolean(false);
                response.subscribe(
                        issue -> {
                            count.getAndIncrement();
                            System.out.println(issue);
                        },
                        error -> {
                            System.out.println(error.getMessage());
                            interrupted.set(true);
                        },
                        () -> interrupted.set(true)
                );

                while (!interrupted.get()) ;
            }, () -> System.out.println("Board не найден"));

        } catch (JiraException e) {
            System.out.println(e.getMessage());
        }
    }
}