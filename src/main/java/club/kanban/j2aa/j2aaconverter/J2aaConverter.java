package club.kanban.j2aa.j2aaconverter;

import club.kanban.j2aa.J2aaConfiguration;
import club.kanban.j2aa.j2aaconverter.fileadapters.FileAdapter;
import club.kanban.j2aa.j2aaconverter.fileadapters.FileAdapterFactory;
import club.kanban.j2aa.jirarestclient.Board;
import club.kanban.j2aa.jirarestclient.BoardIssuesSet;
import club.kanban.j2aa.jirarestclient.Issue;
import lombok.Getter;
import lombok.Setter;
import net.rcarz.jiraclient.JiraException;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class J2aaConverter {
    private static final Logger logger = LoggerFactory.getLogger(J2aaConverter.class);

    /**
     * Дополнительыне поля для выгрузки. Задаются в формате значений поля fields в REST запросе в Jira API
     */
    @Getter
    private final String[] jiraFields;

    /**
     * Алгоритм обработки обратный движений карточек
     * true - в качестве предельной колонки используется максимальный достигнутый issue статус
     * false - в качестве предельной колонки используется текущий статус issue
     */
    @Getter
    private final Boolean useMaxColumn;

    /**
     * Доска, откуда будут выгружаться задачи
     */
    @Getter
    private final Board board;

    /**
     * Дополнительный jsq фильтьр, который нужно наложить к выгружаемым задачам
     */
    private final String jqlSubFilter;

    /**
     * Имя итогового файла для выгрузки. Формат определяется расширением
     */
    private final File outputFile;

    private final static String[] REQUIRED_HTTP_FIELDS = {"status", "created"};

    @Getter
    @Setter
    private List<ExportableIssue> exportableIssues;

    public void importFromJira() throws JiraException, InterruptedException {
        exportableIssues = null;

        BoardIssuesSet boardIssuesSet;
        int startAt = 0;
        do {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }
            String[] actualHttpFields = new String[REQUIRED_HTTP_FIELDS.length + jiraFields.length];
            System.arraycopy(REQUIRED_HTTP_FIELDS, 0, actualHttpFields, 0, REQUIRED_HTTP_FIELDS.length);
            System.arraycopy(jiraFields, 0, actualHttpFields, REQUIRED_HTTP_FIELDS.length, jiraFields.length);

            boardIssuesSet = board.getBoardIssuesSet(jqlSubFilter, startAt, 0, actualHttpFields);

//            if (boardIssuesSet.getIssues().size() > 0) { // TODO удалить закомментиованный код
            if (exportableIssues == null)
                exportableIssues = new ArrayList<>(boardIssuesSet.getTotal());

            // Map issue's changelog to board columns
            List<ExportableIssue> exportableIssuesSet = new ArrayList<>(boardIssuesSet.getIssues().size());
            for (Issue issue : boardIssuesSet.getIssues()) {
                try {
                    ExportableIssue exportableIssue = ExportableIssue.fromIssue(this, issue);
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
        if (outputFile.getParentFile() != null) Files.createDirectories(outputFile.getParentFile().toPath());

        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(outputFile.getAbsoluteFile()), StandardCharsets.UTF_8)) {

            var context = new AnnotationConfigApplicationContext(J2aaConfiguration.class);
            FileAdapter adapter = context.getBean(FileAdapterFactory.class).getAdapter(FilenameUtils.getExtension(outputFile.getName()));
            writer.write(adapter.getPrefix());
            for (int i = 0; i < exportableIssues.size(); i++) {
                ExportableIssue exportableIssue = exportableIssues.get(i);

                if (i == 0)
                    writer.write(adapter.getHeaders(exportableIssue));

                writer.write(adapter.getValues(exportableIssue));
            }
            writer.write(adapter.getPostfix());
            writer.flush();
        }
    }

    public void doConversion() throws JiraException, InterruptedException, IOException {
        logger.info(String.format("Установлено соединение с доской: %s", board.getName()));

        Date startDate = new Date();

        importFromJira();

        if (getExportableIssues().size() > 0) {
            Date endDate = new Date();
            long timeInSec = (endDate.getTime() - startDate.getTime()) / 1000;
            logger.info(String.format("Всего получено: %d issues. Время: %d сек. Скорость: %.2f issues/сек", getExportableIssues().size(), timeInSec, (1.0 * getExportableIssues().size()) / timeInSec));

            // экспортируем данные в файл
            export2File();
            logger.info(String.format("Данные выгружены в файл:\n%s", outputFile.getAbsoluteFile()));
        } else
            logger.info("Не найдены элементы для выгрузки, соответствующие заданным критериям.");

    }

    public static class Builder {
        private String[] jiraFields;
        private boolean useMaxColumn = false;
        private Board board;
        private String jqlSubFilter;
        private File outputFile;

        public Builder() {}
        public J2aaConverter build() {
            return new J2aaConverter(this);
        }
        public Builder setFields(String[] fields) {this.jiraFields = fields; return this;}
        public Builder setUseMaxColumn(boolean useMaxColumn) {this.useMaxColumn = useMaxColumn; return this;}
        public Builder setBoard(Board board) {this.board = board; return this;}
        public Builder setJqlSubFilter(String jqlSubFilter) {this.jqlSubFilter = jqlSubFilter; return this;}
        public Builder setOutputFile(File outputFile) {this.outputFile = outputFile; return this;}

    }
    private J2aaConverter(Builder builder) {
        useMaxColumn = builder.useMaxColumn;
        jiraFields = builder.jiraFields;
        board = builder.board;
        jqlSubFilter = builder.jqlSubFilter;
        outputFile = builder.outputFile;
    }
}
