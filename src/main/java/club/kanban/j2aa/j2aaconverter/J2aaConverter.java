package club.kanban.j2aa.j2aaconverter;

import club.kanban.j2aa.ConnectionProfile;
import club.kanban.j2aa.J2aaConfig;
import club.kanban.j2aa.j2aaconverter.fileadapters.Exportable;
import club.kanban.j2aa.j2aaconverter.fileadapters.FileAdapterFactory;
import club.kanban.j2aa.jiraclient.JiraClient;
import club.kanban.j2aa.jiraclient.JiraException;
import club.kanban.j2aa.jiraclient.dto.Board;
import club.kanban.j2aa.jiraclient.dto.BoardIssuesPage;
import club.kanban.j2aa.jiraclient.dto.boardconfig.BoardConfig;
import club.kanban.j2aa.jiraclient.dto.issue.Issue;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

public class J2aaConverter {
    private static final Logger logger = LoggerFactory.getLogger(J2aaConverter.class);
    private static final int MAX_ALLOWED_ISSUES = 1000;

    private final JiraClient jiraClient;

    @Getter
    private final ConnectionProfile profile;

    @Getter
    private Board board; // TODO доделать
    @Getter
    private BoardConfig boardConfig; // TODO доделать

    private final static String[] REQUIRED_HTTP_FIELDS = {"status", "created"};

    @Getter
    @Setter
    private List<ConvertedIssue> convertedIssues;

    private static long getBoardId(URI uri) {
        Objects.requireNonNull(uri);

        String strBoardId = null;
        String query = uri.getQuery();
        if (query != null && !query.trim().isEmpty()) {
            String[] tokens = query.split("&");
            for (String token : tokens) {
                int i = token.indexOf("=");
                if (i > 0 && token.substring(0, i).trim().equalsIgnoreCase("rapidView")) {
                    strBoardId = token.substring(i + 1).trim();
                    break;
                }
            }
        }

        long boardId;
        try {
            Objects.requireNonNull(strBoardId);
            boardId = Long.parseLong(Objects.requireNonNull(strBoardId));
        } catch (Exception e) {
            throw new JiraException(
                    String.format("Адрес (%s) не содержит ссылки на доску (параметр rapidView)", uri));
        }

        return boardId;
    }

    public void importFromJira() throws URISyntaxException {
        convertedIssues = null;

        URI boardUri = new URI(profile.getBoardAddress());
        long boardId = getBoardId(boardUri);

        logger.info(String.format("Подключаемся к серверу: %s", jiraClient.getJiraUrl()));

        board = jiraClient.getBoard(boardId).orElseThrow(
                () -> new JiraException(
                        String.format("Доска с id = %d не найдена", boardId)));
        boardConfig = jiraClient.getBoardConfig(boardId).orElseThrow(
                () -> new JiraException(
                        String.format("Конфигурация доски с id = %d не найдена", boardId)));

        logger.info(String.format("Установлено соединение с доской: %s", board.getName()));

        BoardIssuesPage page = null;
        int startAt = 0;
        do {
//            if (Thread.currentThread().isInterrupted()) {//TODO
//                throw new InterruptedException();
//            }

            if (page != null) {
                startAt = page.nextPageStartAt();
            }

            //TODO
            String[] actualHttpFields = new String[REQUIRED_HTTP_FIELDS.length + profile.getJiraFields().length];
            System.arraycopy(REQUIRED_HTTP_FIELDS, 0,
                    actualHttpFields, 0, REQUIRED_HTTP_FIELDS.length);
            System.arraycopy(profile.getJiraFields(), 0,
                    actualHttpFields, REQUIRED_HTTP_FIELDS.length, profile.getJiraFields().length);

            page = jiraClient.getBoardIssuesPage(board, profile.getJqlSubFilter(),
                    Arrays.asList(actualHttpFields), startAt, BoardIssuesPage.DEFAULT_MAX_RESULTS).block();

            assert page != null;
            if (page.getTotal() > MAX_ALLOWED_ISSUES) {
                throw new JiraException(
                        String.format("Число задач в выгрузке (%d) больше, чем максимально допустимое (%d).\nПопробуйте уточнить период или параметры в Доп.JQL фильтре.",
                                page.getTotal(), MAX_ALLOWED_ISSUES));
            }

            if (convertedIssues == null) {
                convertedIssues = new ArrayList<>(page.getTotal());
            }

            // Map issue's changelog to board columns
            List<ConvertedIssue> convertedIssuesSet = new ArrayList<>(page.getIssues().size());
            for (Issue issue : page.getIssues()) {
                try {
                    ConvertedIssue convertedIssue = ConvertedIssue.newInstance(this, issue);
                    convertedIssuesSet.add(convertedIssue);
                } catch (Exception e) {
                    logger.info(String.format("Не удается конвертировать %s: %s", issue.getKey(), e.getMessage()));
                }
            }

            convertedIssues.addAll(convertedIssuesSet);
            logger.info(String.format("%d из %d issues получено", convertedIssues.size(), page.getTotal()));
        } while (page.hasNextPage());

    }

    public void export2File() throws IOException {
        File outputFile = new File(profile.getOutputFileName());

        if (outputFile.getParentFile() != null)
            Files.createDirectories(outputFile.getParentFile().toPath());

        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(outputFile.getAbsoluteFile()), StandardCharsets.UTF_8)) {

            var context = J2aaConfig.getContext();
            Exportable exportable = context
                    .getBean(FileAdapterFactory.class)
                    .getAdapter(FilenameUtils.getExtension(outputFile.getName()));
            writer.write(exportable.getPrefix());
            for (int i = 0; i < convertedIssues.size(); i++) {
                ConvertedIssue convertedIssue = convertedIssues.get(i);

                if (i == 0)
                    writer.write(exportable.getHeaders(convertedIssue));

                writer.write(exportable.getValues(convertedIssue));
            }
            writer.write(exportable.getPostfix());
            writer.flush();
            logger.info(String.format("Данные выгружены в файл:\n%s", outputFile.getAbsoluteFile()));
        }

    }

    public void doConversion() throws InterruptedException, IOException, URISyntaxException {

        Date startDate = new Date();

        importFromJira();

        if (getConvertedIssues().size() > 0) {
            Date endDate = new Date();
            long timeInSec = (endDate.getTime() - startDate.getTime()) / 1000;
            logger.info(String.format("Всего получено: %d issues. Время: %d сек. Скорость: %.2f issues/сек",
                    getConvertedIssues().size(), timeInSec, (1.0 * getConvertedIssues().size()) / timeInSec));

            // экспортируем данные в файл
            export2File();
        } else
            logger.info("Не найдены элементы для выгрузки, соответствующие заданным критериям.");
    }

    public J2aaConverter(JiraClient jiraClient, ConnectionProfile profile) {
        Objects.requireNonNull(jiraClient);
        Objects.requireNonNull(profile);

        this.jiraClient = jiraClient;
        this.profile = profile;
    }
}
