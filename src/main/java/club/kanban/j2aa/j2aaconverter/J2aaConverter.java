package club.kanban.j2aa.j2aaconverter;

import club.kanban.j2aa.ConnectionProfile;
import club.kanban.j2aa.J2aaConfig;
import club.kanban.j2aa.j2aaconverter.fileadapters.FileAdapter;
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

    private final BasicCredentials credentials;
    @Getter
    private final ConnectionProfile profile;

    @Getter
    private Board board; // TODO доделать

    private final static String[] REQUIRED_HTTP_FIELDS = {"status", "created"};

    @Getter
    @Setter
    private List<ExportableIssue> exportableIssues;

    public void importFromJira() throws JiraException, InterruptedException, MalformedURLException {
        exportableIssues = null;

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

//            if (boardIssuesSet.getIssues().size() > 0) { // TODO удалить закомментиованный код
            if (exportableIssues == null)
                exportableIssues = new ArrayList<>(boardIssuesSet.getTotal());

            // Map issue's changelog to board columns
            List<ExportableIssue> exportableIssuesSet = new ArrayList<>(boardIssuesSet.getIssues().size());
            for (Issue issue : boardIssuesSet.getIssues()) {
                try {
                    ExportableIssue exportableIssue = ExportableIssue.newInstance(this, issue);
                    exportableIssuesSet.add(exportableIssue);
                } catch (Exception e) {
                    logger.info(String.format("Не удается конвертировать %s: %s", issue.getKey(), e.getMessage()));
                }
            }

            exportableIssues.addAll(exportableIssuesSet);
            startAt += boardIssuesSet.getMaxResults();
            logger.info(String.format("%d из %d issues получено", exportableIssues.size(), boardIssuesSet.getTotal()));
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
            FileAdapter fileAdapter = context.getBean(FileAdapterFactory.class).getAdapter(FilenameUtils.getExtension(outputFile.getName()));
            writer.write(fileAdapter.getPrefix());
            for (int i = 0; i < exportableIssues.size(); i++) {
                ExportableIssue exportableIssue = exportableIssues.get(i);

                if (i == 0)
                    writer.write(fileAdapter.getHeaders(exportableIssue));

                writer.write(fileAdapter.getValues(exportableIssue));
            }
            writer.write(fileAdapter.getPostfix());
            writer.flush();
            logger.info(String.format("Данные выгружены в файл:\n%s", outputFile.getAbsoluteFile()));
        }

    }

    public void doConversion() throws JiraException, InterruptedException, IOException {

        Date startDate = new Date();

        importFromJira();

        if (getExportableIssues().size() > 0) {
            Date endDate = new Date();
            long timeInSec = (endDate.getTime() - startDate.getTime()) / 1000;
            logger.info(String.format("Всего получено: %d issues. Время: %d сек. Скорость: %.2f issues/сек", getExportableIssues().size(), timeInSec, (1.0 * getExportableIssues().size()) / timeInSec));

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
