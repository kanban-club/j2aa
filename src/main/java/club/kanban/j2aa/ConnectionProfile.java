package club.kanban.j2aa;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.InvalidPropertiesFormatException;
import java.util.Properties;

@Repository
public class ConnectionProfile {
    // Ключи профиля подключения
    public static final String KEY_BOARD_URL = "board_url";
    public static final String KEY_OUTPUT_FILE = "output_file_name";
    public static final String KEY_JQL_SUB_FILTER = "jql_sub_filter";
    public static final String KEY_JIRA_FIELDS = "jira_fields";

    @Value("${board-url:}")
    @Getter @Setter
    private String boardAddress;

    @Value("${output-file:}")
    @Getter @Setter
    private String outputFileName;

    @Value("${sub-filter:}")
    @Getter @Setter
    private String jqlSubFilter;

    @Value("${jira-fields:}")
    @Getter @Setter
    private String[] jiraFields;

    @Value("${use-max-column:false}")
    @Getter
    private boolean useMaxColumn;

    @Getter
    private File file;

    /**
     * Загружает профиль подключения из заданного файла.
     * В случае успеха этот файл становится активным профилем подключения
     *
     * @param file Файл для загрузки
     * @throws IOException                      в случае если файл не найден
     * @throws InvalidPropertiesFormatException если файл имеет неверный формат
     */
    public void readConnProfile(File file) throws IOException, InvalidPropertiesFormatException {
        Properties p = new Properties();
        FileInputStream fis = new FileInputStream(file.getAbsoluteFile());

        if (FilenameUtils.getExtension(file.getName()).equalsIgnoreCase("xml"))
            p.loadFromXML(fis);
        else
            p.load(fis);

        boardAddress = p.getProperty(KEY_BOARD_URL);
        jqlSubFilter = p.getProperty(KEY_JQL_SUB_FILTER);
        outputFileName = p.getProperty(KEY_OUTPUT_FILE);
        jiraFields = (p.getProperty(KEY_JIRA_FIELDS) != null ? p.getProperty(KEY_JIRA_FIELDS).split("\\s*,\\s*") : new String[0]);

        if (this.getBoardAddress() == null || this.getBoardAddress().trim().equals(""))
            throw new InvalidPropertiesFormatException(String.format("Не заполнены обязательные поля %s",
                    (this.getBoardAddress() == null || this.getBoardAddress().trim().equals("") ? " " + KEY_BOARD_URL : "")));
        this.file = file;
    }

    /**
     * Записывает текущее состояние полей в заданный file.
     * В случае успеха новый файл становится активным профилем подключения
     *
     * @param file файл для записи
     * @throws IOException в случае если не удается записать файл
     */
    public void writeConnProfile(File file) throws IOException {
        Properties p = new Properties();
        FileOutputStream fos = new FileOutputStream(file.getAbsoluteFile());
        p.setProperty(KEY_BOARD_URL, this.getBoardAddress());
        p.setProperty(KEY_OUTPUT_FILE, this.getOutputFileName().trim());
        p.setProperty(KEY_JQL_SUB_FILTER, this.getJqlSubFilter());
        p.setProperty(KEY_JIRA_FIELDS, String.join(",", this.getJiraFields()));

        if (FilenameUtils.getExtension(file.getName()).equalsIgnoreCase("xml"))
            p.storeToXML(fos, null);
        else
            p.store(fos, null);

        fos.flush();
        fos.close();
        this.file = file;
    }
}
