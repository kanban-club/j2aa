package club.kanban.j2aa.jiraclient;

import club.kanban.j2aa.jiraclient.dto.Board;
import club.kanban.j2aa.jiraclient.dto.BoardIssuesPage;
import club.kanban.j2aa.jiraclient.dto.JiraResource;
import club.kanban.j2aa.jiraclient.dto.auth.AuthResponse;
import club.kanban.j2aa.jiraclient.dto.auth.UserCredentials;
import club.kanban.j2aa.jiraclient.dto.boardconfig.BoardConfig;
import club.kanban.j2aa.jiraclient.dto.issue.Issue;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.codec.DecodingException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.net.ssl.SSLHandshakeException;
import java.net.*;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class JiraClient implements AutoCloseable {
    public static final Logger logger = LoggerFactory.getLogger(JiraClient.class);

    public static final String RESOURCE_URI = "/rest/agile/1.0";
    private static final String ISSUE_URI_TEMPLATE = RESOURCE_URI + "/issue/%d";
    private static final String BOARD_URI_TEMPLATE = RESOURCE_URI + "/board/%d";
    private static final String BOARD_CONFIG_URI_TEMPLATE = RESOURCE_URI + "/board/%d/configuration";
    private static final String BOARD_ISSUES_URI_TEMPLATE = RESOURCE_URI + "/board/%d/issue";
    private static final String AUTH_RESOURCE_URI = "/rest/auth/1/session";
    private static final String JSESSIONID_COOKIE = "JSESSIONID";

    private final WebClient webClient;
    @Getter
    private final String jiraUrl;
    @Getter
    private final String sessionId;

    private static String getServerAddress(URI uri) {
        String serverAddress = null;
        if (uri.getPort() == -1)
            serverAddress = String.format("%s://%s", uri.getScheme(), uri.getHost());
        else
            serverAddress = String.format("%s://%s:%d", uri.getScheme(), uri.getHost(), uri.getPort());

        return serverAddress;
    }

    private static WebClient getDefaultWebClient() {
        final int size = 16 * 1024 * 1024;
        final ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(size)).build();
        return WebClient.builder().exchangeStrategies(strategies).build();
    }

    public static JiraClient connectTo(String jiraUrl, String username, String password) throws URISyntaxException {
        return connectTo(jiraUrl, username, password, getDefaultWebClient());
    }

    private static JiraClient connectTo(String jiraUrl, String username, String password, WebClient webClient) throws URISyntaxException {
        var _jiraUrl = getServerAddress(new URI(jiraUrl));

        try {
            AuthResponse authResponse = webClient.post()
                    .uri(_jiraUrl, uriBuilder -> uriBuilder.path(AUTH_RESOURCE_URI).build())
                    .accept(MediaType.APPLICATION_JSON)
                    .bodyValue(new UserCredentials(username, password))
                    .retrieve()
                    .bodyToMono(AuthResponse.class)
                    .block();
            assert authResponse != null;

            return new JiraClient(webClient, _jiraUrl, authResponse.getSessionId());
        } catch (WebClientRequestException e) {
            throw new JiraException(e.getCause());
        } catch (WebClientResponseException.Unauthorized e) {
            throw new JiraException(
                    String.format("Неизвестное имя пользователя или пароль (Пользователь '%s', Хост '%s').", username, _jiraUrl), e);
        } catch (IllegalArgumentException | NoSuchElementException e) {
            throw new JiraException("Неизвестный ответ от сервера при авторизации."); //TODO не протестировано
        }
    }

    @Override
    public void close() throws Exception {
        ResponseEntity<Void> logoutResponse = webClient.delete()
                .uri(jiraUrl, uriBuilder -> uriBuilder.path(AUTH_RESOURCE_URI).build())
                .accept(MediaType.APPLICATION_JSON)
                .cookie(JSESSIONID_COOKIE, sessionId)
                .retrieve()
                .toBodilessEntity().block();
        //TODO
//        System.out.println("Закрываем сессию: " + sessionId);
        assert logoutResponse != null;
        if (logoutResponse.getStatusCode() != HttpStatus.NO_CONTENT) {
            throw new JiraException("Не удалось закрыть пользовательскую сессию.");
        }
    }

    private <T extends JiraResource> Optional<T> get(Class<T> type, String uri) {
        // TODO Возможные исключения:
        // Прилетел какой-то (другой) JSON - вернется DTO с пустыми полями
        // Пустая страница - вернется null DTO
        // Страница с текстом не JSON - вернется DecodingException
        // или ошибка 400 BAD REQUEST

        T object = null;

        try {
            object = webClient.get()
                    .uri(jiraUrl, uriBuilder -> uriBuilder.path(uri).build())
                    .accept(MediaType.APPLICATION_JSON)
                    .cookie(JSESSIONID_COOKIE, sessionId)
                    .retrieve()
                    .onStatus(httpStatus -> httpStatus != HttpStatus.OK, httpStatus -> Mono.empty())
                    .bodyToMono(type).block();
        } catch (DecodingException ignored) {
        }

        return Optional.ofNullable(object != null && object.isNotEmpty() ? object : null);
    }

    // TODO Описать исключения, прилетающие из get*
    public Optional<Issue> getIssue(long id) {
        return get(Issue.class, String.format(ISSUE_URI_TEMPLATE, id));
    }

    public Optional<Board> getBoard(long id) {
        return get(Board.class,  String.format(BOARD_URI_TEMPLATE, id));
    }

    public Optional<BoardConfig> getBoardConfig(long id) {
        return get(BoardConfig.class, String.format(BOARD_CONFIG_URI_TEMPLATE, id));
    }

    public Mono<BoardIssuesPage> getBoardIssuesPage(Board board,
                                                    String jqlSubFilter,
                                                    List<String> jiraFields,
                                                    int startAt,
                                                    int maxResults) {
        Mono<BoardIssuesPage> object;

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("expand", "changelog");

        if (jiraFields != null)
            params.add("fields", String.join(",", jiraFields));

        if (jqlSubFilter != null)
            params.add("jql", jqlSubFilter);

        if (maxResults > 0)
            params.add("maxResults", Integer.toString(maxResults));

        if (startAt > 0)
            params.add("startAt", Integer.toString(startAt));

        try {
            object = webClient.get()
                    .uri(jiraUrl, uriBuilder -> uriBuilder
                            .path(String.format(BOARD_ISSUES_URI_TEMPLATE, board.getId()))
                            .queryParams(params)
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .cookie(JSESSIONID_COOKIE, sessionId)
                    .retrieve()
                    .onStatus(httpStatus -> httpStatus != HttpStatus.OK, httpStatus -> Mono.empty())
                    .bodyToMono(BoardIssuesPage.class);
        } catch (DecodingException ignored) {
            object = Mono.empty();
        }

        return object;
    }

    // TODO experimental
    public Mono<List<Issue>> getBoardIssuesMono(Board board,
                                            String jqlSubFilter,
                                            List<String> jiraFields) {

        return getBoardIssuesPage(board, jqlSubFilter, jiraFields, 0, BoardIssuesPage.DEFAULT_MAX_RESULTS)
                .expand(page -> {
                    if (page.hasNextPage())
                        return getBoardIssuesPage(board, jqlSubFilter, jiraFields, page.nextPageStartAt(), BoardIssuesPage.DEFAULT_MAX_RESULTS);
                    else
                        return Mono.empty();
                })
                .flatMap(page -> Flux.fromIterable(page.getIssues())).collectList();
    }

    public Flux<Issue> getBoardIssuesFlux(Board board,
                                          String jqlSubFilter,
                                          List<String> jiraFields) {

        return getBoardIssuesPage(board, jqlSubFilter, jiraFields, 0, BoardIssuesPage.DEFAULT_MAX_RESULTS)
                .expand(page -> {
                    if (page.hasNextPage())
                        return getBoardIssuesPage(board, jqlSubFilter, jiraFields, page.nextPageStartAt(), BoardIssuesPage.DEFAULT_MAX_RESULTS);
                    else
                        return Mono.empty();
                })
                .flatMap(page -> Flux.fromIterable(page.getIssues()));
    }
}
