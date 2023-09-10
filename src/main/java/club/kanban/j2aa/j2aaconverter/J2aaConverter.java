package club.kanban.j2aa.j2aaconverter;

import club.kanban.j2aa.J2aaConfig;
import club.kanban.j2aa.j2aaconverter.fileadapters.Exportable;
import club.kanban.j2aa.j2aaconverter.fileadapters.FileAdapterFactory;
import club.kanban.j2aa.jiraclient.JiraClient;
import club.kanban.j2aa.jiraclient.JiraException;
import club.kanban.j2aa.jiraclient.dto.Board;
import club.kanban.j2aa.jiraclient.dto.BoardIssuesPage;
import club.kanban.j2aa.jiraclient.dto.boardconfig.BoardConfig;
import club.kanban.j2aa.jiraclient.dto.issue.Issue;
import lombok.Builder;
import lombok.Getter;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class J2aaConverter {
    private static final Logger logger = LoggerFactory.getLogger(J2aaConverter.class);
    private static final int MAX_ALLOWED_ISSUES = 1000;
    private final static List<String> REQUIRED_HTTP_FIELDS = Arrays.asList("status", "created");

    private final JiraClient jiraClient;
    private final URL boardUrl;
    private final String jqlSubFilter;
    @Getter
    private final List<String> jiraFields;
    @Getter
    private final boolean useMaxColumn;

    @Getter
    private BoardConfig boardConfig;

    @Getter
    private List<ConvertedIssue> convertedIssues;

    @Builder(setterPrefix = "with", builderMethodName = "internalBuilder")
    public J2aaConverter(JiraClient jiraClient,
                         URL boardUrl, String jqlSubFilter, List<String> jiraFields, boolean useMaxColumn) {
        this.jiraClient = jiraClient;
        this.boardUrl = boardUrl;
        this.jqlSubFilter = jqlSubFilter;
        this.jiraFields = jiraFields;
        this.useMaxColumn = useMaxColumn;
    }

    public static J2aaConverterBuilder builder(JiraClient jiraClient, URL boardUrl) {
        Objects.requireNonNull(jiraClient);
        Objects.requireNonNull(boardUrl);
        return internalBuilder().withJiraClient(jiraClient).withBoardUrl(boardUrl);
    }

    private static long getBoardUrl(URL url) {
        Objects.requireNonNull(url);

        String strBoardId = null;
        String query = url.getQuery();
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
                    String.format("Адрес (%s) не содержит ссылки на доску (параметр rapidView)", url));
        }

        return boardId;
    }

    public int fetchData() {
        convertedIssues = null;

        logger.info(String.format("Подключаемся к серверу: %s", jiraClient.getServerUrl()));

        long boardId = getBoardUrl(boardUrl);
        Board board = jiraClient.getBoard(boardId).orElseThrow(
                () -> new JiraException(
                        String.format("Доска с id = %d не найдена", boardId)));
        boardConfig = jiraClient.getBoardConfig(boardId).orElseThrow(
                () -> new JiraException(
                        String.format("Конфигурация доски с id = %d не найдена", boardId)));

        logger.info(String.format("Установлено соединение с доской: %s", boardConfig.getName()));

        BoardIssuesPage page = null;
        int startAt = 0;
        do {
            if (page != null) {
                startAt = page.nextPageStartAt();
            }

            List<String> actualHttpFields = new ArrayList<>(REQUIRED_HTTP_FIELDS.size() + jiraFields.size());
            actualHttpFields.addAll(REQUIRED_HTTP_FIELDS);
            actualHttpFields.addAll(jiraFields);
            page = jiraClient.getBoardIssuesPage(board, jqlSubFilter,
                    actualHttpFields, startAt, BoardIssuesPage.DEFAULT_MAX_RESULTS).block();

            assert page != null;
            if (page.getTotal() > MAX_ALLOWED_ISSUES) {
                throw new JiraException(
                        String.format("Число задач в выгрузке (%d) больше, чем максимально допустимое (%d).\n"
                                        + "Попробуйте уточнить период или параметры в Доп.JQL фильтре.",
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

        return convertedIssues.size();
    }

    public void exportIssues(String outputFileName) throws IOException {
        File outputFile = new File(outputFileName);

        if (outputFile.getParentFile() != null) {
            Files.createDirectories(outputFile.getParentFile().toPath());
        }

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

    public void exportBlockers(String outputFileName) throws IOException {
        Objects.requireNonNull(outputFileName);
        // Экспортруем календарь блокировок
        // Формируем данные
        LocalDate calendarStartDay = LocalDate.from(convertedIssues.stream()
                .map(issue -> issue.getColumnTransitionsLog()[0])
                .min(Date::compareTo).orElseThrow().toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
        LocalDate calendarEndDay = LocalDate.now();
        Map<String, BlockersCalendar> calendars = new HashMap<>(10);
        convertedIssues.forEach(issue -> {
            String issueType = Objects.requireNonNull(issue.getAttributes().get("Issue Type").toString());
            BlockersCalendar issueTypeCalendar = calendars.get(issueType);
            if (issueTypeCalendar == null) {
                issueTypeCalendar = BlockersCalendar.newInstance(calendarStartDay, calendarEndDay);
                calendars.put(issueType, issueTypeCalendar);
            }
//            if (!issue.isBlocked())//TODO удалить
            issueTypeCalendar.importBlockerChanges(issue.getFlaggedChanges());
        });

        String name = FilenameUtils.getBaseName(outputFileName);
        String path = FilenameUtils.getFullPath(outputFileName);
//            File blockersFile = new  File(path + name + " Impediments.xls" + (!ext.isEmpty() ? "." + ext : "" ));
        File blockersFile = new File(path + name + " Impediments.xls");

        //Записываем файл
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(blockersFile), StandardCharsets.UTF_8)) {

            writer.write(
                    new StringBuilder()
                            .append("<html>\n<head>\n<meta charset=\"utf-8\">\n<title>Impediments</title>\n</head>\n")
                            .append("<body>\n<table border=1 align=\"center\" cellpadding=\"4\" cellspacing=\"0\">\n<tr>")
                            .append("<td bgcolor=\"#CCCCFF\"><b>Date</b></td><td bgcolor=\"#CCCCFF\"><b>Sum</td>")
                            .append((calendars.keySet().size() > 0 ? "<td bgcolor=\"#CCCCFF\"><b>" : ""))
                            .append(String.join("</b></td><td bgcolor=\"#CCCCFF\"><b>", calendars.keySet()))
                            .append((calendars.keySet().size() > 0 ? "</b></td>" : ""))
                            .append("</tr>\n")
                            .toString()
            );

            LocalDate calendarDate = calendarStartDay;
            while (calendarDate.isBefore(calendarEndDay) || calendarDate.equals(calendarEndDay)) {
                int sum = 0;
                List<String> values = new ArrayList<>(calendars.keySet().size());
                for (String key : calendars.keySet()) {
                    int value = calendars.get(key).getValue(calendarDate);
                    values.add(String.valueOf(value));
                    sum += value;
                }

                writer.write(new StringBuilder("<tr><td>")
                        .append(calendarDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                        .append("</td><td>")
                        .append(sum)
                        .append("</td>")
                        .append((values.size() > 0 ? "<td>" : ""))
                        .append(String.join("</td><td>", values))
                        .append((values.size() > 0 ? "</td>" : ""))
                        .append("</tr>\n")
                        .toString());

                calendarDate = calendarDate.plusDays(1);
            }

            writer.write("</table>\n</body>");
            writer.flush();
            logger.info(String.format("Блокировки выгружены в файл:\n%s", blockersFile.getAbsoluteFile()));
        }
    }
}
