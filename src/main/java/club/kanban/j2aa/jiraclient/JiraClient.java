package club.kanban.j2aa.jiraclient;

import club.kanban.j2aa.jiraclient.dto.Board;
import club.kanban.j2aa.jiraclient.dto.BoardIssuesPage;
import club.kanban.j2aa.jiraclient.dto.JiraResource;
import club.kanban.j2aa.jiraclient.dto.auth.AuthResponse;
import club.kanban.j2aa.jiraclient.dto.auth.UserCredentials;
import club.kanban.j2aa.jiraclient.dto.boardconfig.BoardConfig;
import club.kanban.j2aa.jiraclient.dto.issue.Issue;
import lombok.Builder;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.codec.DecodingException;
import org.springframework.http.HttpHeaders;
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

import java.net.HttpCookie;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

/**
 * Клиент, использумые для подключения к jira через REST API и получения ее объектов таких как,
 * Issue, Board, BoardConfig
 * Используется через try-with-resources, чтобы обеспечить закрытие пользовательских сессий
 */
public class JiraClient implements AutoCloseable {
    public static final Logger logger = LoggerFactory.getLogger(JiraClient.class);//TODO

    public static final String RESOURCE_URI = "/rest/agile/1.0";
    private static final String ISSUE_URI_TEMPLATE = RESOURCE_URI + "/issue/%d";
    private static final String BOARD_URI_TEMPLATE = RESOURCE_URI + "/board/%d";
    private static final String BOARD_CONFIG_URI_TEMPLATE = RESOURCE_URI + "/board/%d/configuration";
    private static final String BOARD_ISSUES_URI_TEMPLATE = RESOURCE_URI + "/board/%d/issue";
    private static final String AUTH_RESOURCE_URI = "/rest/auth/1/session";
    private static final String JSESSIONID_COOKIE = "JSESSIONID";

    @Getter
    private final WebClient webClient;
    @Getter
    private final String urlPathPrefix;
    @Getter
    private final URL serverUrl;
    @Getter
    private final String sessionId;

    /**
     * Извлекает из заданного URL адрес сервера и при необходимости номер порта.
     * @param url первоначальный адрес для извлечения
     * @return адрес сервера
     */
    private static URL getServerUrl(URL url) {
        try {
            if (url.getPort() == -1)
                return new URL(String.format("%s://%s", url.getProtocol(), url.getHost()));
            else
                return new URL(String.format("%s://%s:%d", url.getProtocol(), url.getHost(), url.getPort()));
        } catch (MalformedURLException ignored) {
        }
        return null;
    }

    /**
     * Возвращает экземпляр WebClient "по-умолчанию" с буферизацией 16 Мб
     * @return экземпляр WebClient
     */
    private static WebClient getDefaultWebClient() {
        final int size = 16 * 1024 * 1024;
        final ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(size)).build();
        return WebClient.builder().exchangeStrategies(strategies).build();
    }

    /**
     * Извлекает JSESSIONID cookie из ответа сервера
     * @param responseEntity    ответ сервера
     * @return  значение JSESSIONID cookie
     * @throws  IllegalArgumentException – if cookies in the responseEntity violate the cookie
     * specification's syntax or the cookie name contains illegal characters.
     * @throws  NullPointerException – if cookies in the responseEntity string is null
     */
    private static Optional<HttpCookie> getSessionCookie(ResponseEntity responseEntity) {
        try {
            List<String> cookies = responseEntity.getHeaders().get(HttpHeaders.SET_COOKIE);
            for (String cookie : Objects.requireNonNull(cookies)) {
                List<HttpCookie> httpCookies = HttpCookie.parse(cookie);
                for (HttpCookie httpCookie : httpCookies) {
                    if (httpCookie.getName().equals(JSESSIONID_COOKIE)) {
                        return Optional.of(httpCookie);
                    }
                }
            }
        } catch (Exception e) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    /**
     * Создает новый экземпляр JiraClient с заданными параметрами
     * @param jiraUrl jiraUrl URL jira, содержащий адрес сервера для подключения
     * @param username имя пользвоателя
     * @param password пароль
     * @param webClient WebClient для установления web сессии
     */
    @Builder(setterPrefix = "with", builderMethodName = "internalBuilder")
    private JiraClient(URL jiraUrl, String username, String password,
                                        WebClient webClient, String urlPathPrefix) {
        try {
            var serverUrl = getServerUrl(jiraUrl);

            assert serverUrl != null;
//            AuthResponse authResponse = webClient.post()
//                    .uri(serverUrl.toString(), uriBuilder -> uriBuilder.path(AUTH_RESOURCE_URI).build())
//                    .accept(MediaType.APPLICATION_JSON)
//                    .bodyValue(new UserCredentials(username, password))
//                    .retrieve()
//                    .bodyToMono(AuthResponse.class)
//                    .block();
//            assert authResponse != null;
//
//            return new JiraClient(webClient, serverUrl, authResponse.getSessionId());
            this.serverUrl = serverUrl;
            this.webClient = webClient != null ? webClient : getDefaultWebClient();
            this.urlPathPrefix = urlPathPrefix != null ? urlPathPrefix : "";

            ResponseEntity<AuthResponse> responseEntity = this.webClient.post()
                    .uri(
                            serverUrl.toString(),
                            uriBuilder -> uriBuilder.path(this.urlPathPrefix + AUTH_RESOURCE_URI).build())
                    .accept(MediaType.APPLICATION_JSON)
                    .bodyValue(new UserCredentials(username, password))
                    .retrieve()
                    .toEntity(AuthResponse.class)
                    .block();

//            AuthResponse authResponse =responseEntity.getBody();

            this.sessionId = getSessionCookie(responseEntity)
                    .orElseThrow(() -> new JiraException("Cookie JSESSIONID отсутствует в ответе сервера"))
                    .getValue();

        } catch (WebClientRequestException e) {
            throw new JiraException(e.getCause());
        } catch (WebClientResponseException.Unauthorized e) {
            throw new JiraException(
                    String.format("Неизвестное имя пользователя или пароль (Пользователь '%s').", username), e);
        } catch (IllegalArgumentException | NoSuchElementException e) {
            throw new JiraException("Неизвестный ответ от сервера при авторизации."); //TODO не протестировано
        }
    }

    public static JiraClientBuilder builder(URL jiraUrl, String username, String password) {
        return internalBuilder()
                .withJiraUrl(jiraUrl)
                .withUsername(username)
                .withPassword(password);
    }

    /**
     * Закрывает сессию пользователя на сервере jira. Применяется в сочетании try-with-resources
     * После вызова данной функции данный экземпляр JiraClient нельзя испльзвоатать для полключения к Jira
     */
    @Override
    public void close() {
        ResponseEntity<Void> logoutResponse = webClient.delete()
                .uri(
                        serverUrl.toString(),
                        uriBuilder -> uriBuilder.path(urlPathPrefix + AUTH_RESOURCE_URI).build())
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
                    .uri(
                            serverUrl.toString(),
                            uriBuilder -> uriBuilder.path(urlPathPrefix + uri).build())
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

    /**
     * Возвращает jira Issue
     * @param id идентификатор
     * @return экземпляр Issue
     */
    public Optional<Issue> getIssue(long id) {
        return get(Issue.class, String.format(ISSUE_URI_TEMPLATE, id));
    }

    /**
     * Возращает jira Board
     * @param id идентификатор
     * @return экземпляр Board
     */
    public Optional<Board> getBoard(long id) {
        return get(Board.class, String.format(BOARD_URI_TEMPLATE, id));
    }

    /**
     * Возращает jira BoardConfig
     * @param id идентификатор
     * @return экземпляр BoardConfig
     */
    public Optional<BoardConfig> getBoardConfig(long id) {
        return get(BoardConfig.class, String.format(BOARD_CONFIG_URI_TEMPLATE, id));
    }

    /**
     * Возвращает одну страницу c Issues с заданной доски.
     * @param board доска, фильтр которой испольуется для отбора issues
     * @param jqlSubFilter дополнительный к основному фильтру jql запрос (применяется черех AND)
     * @param jiraFields список полей, которые необходимо выгрузить для каждого issue. Если не задан,
     *                   то выгружается минимальный набор полей (например key, дата созрания и история изменений)
     * @param startAt   номер issue (начиная с 0) начиная с которого будет выгружена страница.
     * @param maxResults максимальный размер страницы. Если указан 0, то импользуется значение по-умолчанию (50)
     * @return Объект Mono, содержащий страницу BoardIssuesPage с найденными issues
     */
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
                    .uri(serverUrl.toString(), uriBuilder -> uriBuilder
                            .path(String.format(urlPathPrefix + BOARD_ISSUES_URI_TEMPLATE, board.getId()))
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
                        return getBoardIssuesPage(board,
                                jqlSubFilter,
                                jiraFields,
                                page.nextPageStartAt(),
                                BoardIssuesPage.DEFAULT_MAX_RESULTS);
                    else
                        return Mono.empty();
                })
                .flatMap(page -> Flux.fromIterable(page.getIssues())).collectList();
    }

    //TODO experimental
    public Flux<Issue> getBoardIssuesFlux(Board board,
                                          String jqlSubFilter,
                                          List<String> jiraFields) {

        return getBoardIssuesPage(board, jqlSubFilter, jiraFields, 0, BoardIssuesPage.DEFAULT_MAX_RESULTS)
                .expand(page -> {
                    if (page.hasNextPage())
                        return getBoardIssuesPage(board,
                                jqlSubFilter, jiraFields,
                                page.nextPageStartAt(),
                                BoardIssuesPage.DEFAULT_MAX_RESULTS);
                    else
                        return Mono.empty();
                })
                .flatMap(page -> Flux.fromIterable(page.getIssues()));
    }
}
