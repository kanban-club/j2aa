package club.kanban.j2aa.j2aaconverter;

import club.kanban.j2aa.ConnectionProfile;
import club.kanban.j2aa.J2aaConfig;
import club.kanban.j2aa.j2aaconverter.fileadapters.Exportable;
import club.kanban.j2aa.j2aaconverter.fileadapters.FileAdapterFactory;
import club.kanban.j2aa.jirarestclient.Board;
import club.kanban.j2aa.jirarestclient.BoardIssuesSet;
import club.kanban.j2aa.jirarestclient.Issue;
import lombok.Getter;
import lombok.Setter;
import net.rcarz.jiraclient.BasicCredentials;
import net.rcarz.jiraclient.JiraClient;
import net.rcarz.jiraclient.JiraException;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class J2aaConverter {
    private static final Logger logger = LoggerFactory.getLogger(J2aaConverter.class);
    private static final int MAX_ALLOWED_ISSUES = 1000;
    private final BasicCredentials credentials;
    @Getter
    private final ConnectionProfile profile;

    @Getter
    private Board board; // TODO доделать

    private final static String[] REQUIRED_HTTP_FIELDS = {"status", "created"};

    @Getter
    @Setter
    private List<ConvertedIssue> convertedIssues;

    public void importFromJira() throws JiraException, InterruptedException, MalformedURLException {
        convertedIssues = null;

        URL boardUrl = new URL(profile.getBoardAddress());

        String jiraAddress = boardUrl.getProtocol() + "://" + boardUrl.getHost() + (boardUrl.getPort() == -1 ? "" : ":" + boardUrl.getPort()) + "/";

        logger.info(String.format("Подключаемся к серверу: %s", jiraAddress));
        JiraClient jiraClient = new JiraClient(jiraAddress, credentials);
        board = Board.newInstance(jiraClient.getRestClient(), boardUrl);
        logger.info(String.format("Установлено соединение с доской: %s", board.getName()));

        BoardIssuesSet boardIssuesSet;
        int startAt = 0;
        do {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }
            String[] actualHttpFields = new String[REQUIRED_HTTP_FIELDS.length + profile.getJiraFields().length];
            System.arraycopy(REQUIRED_HTTP_FIELDS, 0, actualHttpFields, 0, REQUIRED_HTTP_FIELDS.length);
            System.arraycopy(profile.getJiraFields(), 0, actualHttpFields, REQUIRED_HTTP_FIELDS.length, profile.getJiraFields().length);

            boardIssuesSet = board.getBoardIssuesSet(profile.getJqlSubFilter(), startAt, 0, actualHttpFields);

            if (boardIssuesSet.getTotal() > MAX_ALLOWED_ISSUES) {
                throw new JiraException(
                        String.format("Число задач в выгрузке (%d) больше, чем максимально допустимое (%d). \nПопробуйте уточнить период или параметры в Доп.JQL фильтре.", boardIssuesSet.getTotal(), MAX_ALLOWED_ISSUES));
            }

//            if (boardIssuesSet.getIssues().size() > 0) { // TODO удалить закомментиованный код
            if (convertedIssues == null)
                convertedIssues = new ArrayList<>(boardIssuesSet.getTotal());

            // Map issue's changelog to board columns
            List<ConvertedIssue> convertedIssuesSet = new ArrayList<>(boardIssuesSet.getIssues().size());
            for (Issue issue : boardIssuesSet.getIssues()) {
                try {
                    ConvertedIssue convertedIssue = ConvertedIssue.newInstance(this, issue);
                    convertedIssuesSet.add(convertedIssue);
                } catch (Exception e) {
                    logger.info(String.format("Не удается конвертировать %s: %s", issue.getKey(), e.getMessage()));
                }
            }

            convertedIssues.addAll(convertedIssuesSet);
            startAt += boardIssuesSet.getMaxResults();
            logger.info(String.format("%d из %d issues получено", convertedIssues.size(), boardIssuesSet.getTotal()));
//            }
        } while (startAt < boardIssuesSet.getTotal()); // alternative (boardIssuesSet.getBoardIssues().size() > 0)
    }

    public void export2File() throws IOException {
        File outputFile = new File(profile.getOutputFileName());

        if (outputFile.getParentFile() != null)
            Files.createDirectories(outputFile.getParentFile().toPath());

        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(outputFile.getAbsoluteFile()), StandardCharsets.UTF_8))
        {

            var context = J2aaConfig.getContext();
            Exportable exportable = context.getBean(FileAdapterFactory.class).getAdapter(FilenameUtils.getExtension(outputFile.getName()));
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

    public void doConversion() throws JiraException, InterruptedException, IOException {

        Date startDate = new Date();

        importFromJira();

        if (getConvertedIssues().size() > 0) {
            Date endDate = new Date();
            long timeInSec = (endDate.getTime() - startDate.getTime()) / 1000;
            logger.info(String.format("Всего получено: %d issues. Время: %d сек. Скорость: %.2f issues/сек", getConvertedIssues().size(), timeInSec, (1.0 * getConvertedIssues().size()) / timeInSec));

            // экспортируем данные в файл
            export2File();
        } else
            logger.info("Не найдены элементы для выгрузки, соответствующие заданным критериям.");
    }

    public J2aaConverter(BasicCredentials credentials, ConnectionProfile profile) {
        this.profile = profile;
        this.credentials = credentials;
    }

}
