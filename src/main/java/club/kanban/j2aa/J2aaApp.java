package club.kanban.j2aa;

import club.kanban.j2aa.j2aaconverter.J2aaConverter;
import club.kanban.j2aa.j2aaconverter.fileadapters.FileAdapterFactory;
import club.kanban.j2aa.jirarestclient.Board;
import club.kanban.j2aa.jirarestclient.uilogger.UILogInterface;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import lombok.Getter;
import lombok.Setter;
import net.rcarz.jiraclient.BasicCredentials;
import net.rcarz.jiraclient.JiraClient;
import net.rcarz.jiraclient.JiraException;
import net.rcarz.jiraclient.RestException;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import javax.annotation.PostConstruct;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.List;
import java.util.*;

import static javax.swing.JFileChooser.APPROVE_OPTION;
import static javax.swing.JOptionPane.*;

@SpringBootApplication
public class J2aaApp extends JFrame implements UILogInterface {
    private static final Logger logger = LoggerFactory.getLogger(J2aaApp.class);
    public static final String VERSION_KEY = "version";
    public static final String CONFIG_FILE_NAME = ".j2aa";

    public static final String DEFAULT_APP_TITLE = "Jira to ActionableAgile converter";
    public static final String DEFAULT_CONNECTION_PROFILE_FORMAT = "xml";

    public static final String KEY_BOARD_URL = "board_url";
    public static final String KEY_OUTPUT_FILE = "output_file_name";
    public static final String KEY_JQL_SUB_FILTER = "jql_sub_filter";

    public static final String KEY_USER_NAME = "default.username";
    public static final String KEY_PASSWORD = "default.password";

    @Getter
    private final JFrame appFrame;

    @Value("${" + KEY_BOARD_URL + ":}")
    @Getter
    @Setter
    private String boardUrl;

    @Value("${" + KEY_USER_NAME + ":}")
    @Getter
    @Setter
    private String userName;

    @Value("${" + KEY_PASSWORD + ":}")
    @Getter
    @Setter
    private String password;

    @Value("${" + KEY_OUTPUT_FILE + ":}")
    @Getter
    @Setter
    private String outputFileName;

    @Value("${" + KEY_JQL_SUB_FILTER + ":}")
    @Getter
    @Setter
    private String jqlSubFilter;

    @Getter
    private File connProfile;
    @Value("${user.dir}")
    private String lastConnFileDir;

    @Getter
    @Value("${" + VERSION_KEY + ":}")
    private String version;

    @Value("${converter.jira-fields:issuetype,labels,epic}")
    private String[] jiraFields;

    @Value("${converter.use-max-column:false}")
    private boolean useMaxColumn;

    private JPanel rootPanel;
    private JTextField fBoardURL;
    private JButton startButton;
    private JTextField fUsername;
    private JTextField fOutputFileName;
    private JTextField fJQLSubFilter;
    private JTextArea fLog;
    private JButton selectOutputFileButton;
    private JButton loadSettingsButton;
    private JButton saveSettingsButton;
    private JPasswordField fPassword;
    private JLabel labelBoardUrl;

    private Thread conversionThread;

    public J2aaApp() {
        super();

        appFrame = new JFrame();

        URL imgURL = getClass().getResource("/app-icon.jpg");
        if (imgURL != null) {
            ImageIcon appIcon = new ImageIcon(imgURL);
            getAppFrame().setIconImage(appIcon.getImage());
        }

        appFrame.setContentPane(rootPanel);
        appFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        appFrame.setPreferredSize(new Dimension(800, 450));
        appFrame.pack();
        appFrame.getRootPane().setDefaultButton(startButton);

        startButton.addActionListener(actionEvent -> {
            if (conversionThread == null) {
                conversionThread = new Thread(this::doConversion);
                conversionThread.start();
            } else {
                logger.info("Прерывание конвертации ...");
                conversionThread.interrupt();
            }
        });
        saveSettingsButton.addActionListener(actionEvent -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setCurrentDirectory(new File(lastConnFileDir));
            chooser.setSelectedFile(getConnProfile());
            chooser.setDialogTitle("Укажите файл для сохранения настроек");
            chooser.setFileFilter(new FileNameExtensionFilter("Connection profiles (.xml)", "xml"));
            int returnVal = chooser.showSaveDialog(getAppFrame());
            if (returnVal == APPROVE_OPTION) {
                try {
                    File file = chooser.getSelectedFile();
                    if (FilenameUtils.getExtension(file.getAbsolutePath()).equals(""))
                        file = new File(file.getAbsoluteFile() + "." + DEFAULT_CONNECTION_PROFILE_FORMAT);

                    if (file.exists() && showConfirmDialog(getAppFrame(), String.format("Файл %s уже существует. Перезаписать?", file.getAbsoluteFile()), "Подтверждение", YES_NO_OPTION) != YES_OPTION)
                        return;

                    writeConnProfile(file);
                    lastConnFileDir = file.getParent();
                    showMessageDialog(getAppFrame(), String.format("Настройки сохранены в файл %s", file.getName()), "Сохранение настроек", INFORMATION_MESSAGE);
                } catch (IOException e) {
                    showMessageDialog(getAppFrame(), "Ошибка сохранения настроек", "Ошибка", ERROR_MESSAGE);
                }
            }
        });

        loadSettingsButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new FileNameExtensionFilter("Connection profiles (.xml)", "xml"));
            chooser.setDialogTitle("Выберите файл с настройками");
            chooser.setCurrentDirectory(new File(lastConnFileDir));
            int returnVal = chooser.showOpenDialog(getAppFrame());
            if (returnVal == APPROVE_OPTION) {
                try {
                    readConnProfile(chooser.getSelectedFile());
                    fLog.setText(null);
                    lastConnFileDir = chooser.getSelectedFile().getParent();
                } catch (IOException ex) {
                    showMessageDialog(getAppFrame(), String.format("Не удалось прочитать файл %s", chooser.getSelectedFile().getName()), "Ошибка чтения файла", ERROR_MESSAGE);
                }
            }
        });

        selectOutputFileButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Выберите расположение и имя файла");

            FileAdapterFactory faf = J2aaConfig.getContext().getBean(FileAdapterFactory.class);
            faf.getFormats().forEach((ext, desc) -> {
                var filter = new FileNameExtensionFilter(desc, ext);
                chooser.addChoosableFileFilter(filter);
                if (ext.equalsIgnoreCase("csv"))
                    chooser.setFileFilter(filter);
            });

            chooser.setSelectedFile(new File(fOutputFileName.getText()));
            chooser.setCurrentDirectory(new File(fOutputFileName.getText()).getAbsoluteFile().getParentFile());
            if (chooser.showSaveDialog(getAppFrame()) == APPROVE_OPTION) {
                File file = chooser.getSelectedFile();
                if (FilenameUtils.getExtension(file.getName()).isBlank())
                    file = new File(file.getAbsoluteFile() + faf.getDefaultAdapter().getDefaultExtension());
                fOutputFileName.setText(file.getAbsolutePath());
                getData(this);
            }
        });
        labelBoardUrl.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                if (e.getClickCount() == 2) {
                    Desktop desktop = Desktop.isDesktopSupported() ? Desktop.getDesktop() : null;
                    if (desktop != null && desktop.isSupported(Desktop.Action.BROWSE)) {
                        try {
                            String url = fBoardURL.getText();
                            if (showConfirmDialog(getAppFrame(),
                                    String.format("Перейти на доску '%s'?", url),
                                    "Открытие страницы", YES_NO_OPTION, INFORMATION_MESSAGE) == YES_OPTION) {
                                desktop.browse(URI.create(fBoardURL.getText()));
                            }
                        } catch (IOException ignored) {
                        }
                    }
                }
            }
        });
    }

    public static void main(String[] args) {
        File configFile = new File(System.getProperty("user.home") + "/" + CONFIG_FILE_NAME);
        if (!configFile.exists()) {
            try {
                IOUtils.copy(Objects.requireNonNull(Thread.currentThread().getContextClassLoader()
                        .getResourceAsStream(CONFIG_FILE_NAME)), new FileOutputStream(configFile));
            } catch (IOException e) {
                logger.error("Ошибка создания конфигурационного файла '{}'", configFile.getAbsoluteFile());
            }
        }

        SpringApplication.run(J2aaApp.class);
    }

    @PostConstruct
    private void init() {
        setData(this);
        setAppTitle();
        getAppFrame().setVisible(true);
    }

    public void logToUI(String msg) {
        EventQueue.invokeLater(() -> { // или SwingUtilities.invokeLater()
            fLog.append(msg + "\n");
            fLog.setCaretPosition(fLog.getDocument().getLength());
        });
    }

    /**
     * Загружает профиль подключения из заданного файла.
     * В случае успеха этот файл становится активным профилем подключения
     *
     * @param file Файл для загрузки
     * @throws IOException                      в случае если файл не найден
     * @throws InvalidPropertiesFormatException если файл имеет неверный формат
     */
    protected void readConnProfile(File file) throws IOException, InvalidPropertiesFormatException {
        Properties p = new Properties();
        FileInputStream fis = new FileInputStream(file.getAbsoluteFile());

        if (FilenameUtils.getExtension(file.getName()).equalsIgnoreCase("xml"))
            p.loadFromXML(fis);
        else
            p.load(fis);

        setBoardUrl(p.getProperty(KEY_BOARD_URL));
        setJqlSubFilter(p.getProperty(KEY_JQL_SUB_FILTER));
        setOutputFileName(p.getProperty(KEY_OUTPUT_FILE));

        if (this.getBoardUrl() == null || this.getBoardUrl().trim().equals(""))
            throw new InvalidPropertiesFormatException(String.format("Не заполнены обязательные поля %s",
                    (this.getBoardUrl() == null || this.getBoardUrl().trim().equals("") ? " " + KEY_BOARD_URL : "")));
        connProfile = file;
        setData(this);
        setAppTitle();
    }

    /**
     * Записывает текущее состояние полей экранной формы в заданный file.
     * В случае успеха новый файл становится активным профилем подключения
     *
     * @param file файл для записи
     * @throws IOException в случае если не удается записать файл
     */
    private void writeConnProfile(File file) throws IOException {
        getData(this);

        Properties p = new Properties();
        FileOutputStream fos = new FileOutputStream(file.getAbsoluteFile());
        p.setProperty(KEY_BOARD_URL, this.getBoardUrl());
        p.setProperty(KEY_OUTPUT_FILE, this.getOutputFileName().trim());
        p.setProperty(KEY_JQL_SUB_FILTER, this.getJqlSubFilter());

        if (FilenameUtils.getExtension(file.getName()).equalsIgnoreCase("xml"))
            p.storeToXML(fos, null);
        else
            p.store(fos, null);

        fos.flush();
        fos.close();
        connProfile = file;
        setAppTitle();
    }

    /**
     * Передает значения переменных приложения в поля экранной формы
     *
     * @param data класс приложения
     */
    public void setData(J2aaApp data) {
        fBoardURL.setText(data.getBoardUrl());
        fUsername.setText(data.getUserName());
        fOutputFileName.setText(data.getOutputFileName());
        fJQLSubFilter.setText(data.getJqlSubFilter());
        fPassword.setText(data.getPassword());
    }

    /**
     * Передает значения полей экранной формы в переменные приложения
     *
     * @param data класс приложения
     */
    public void getData(J2aaApp data) {
        data.setBoardUrl(fBoardURL.getText());
        data.setUserName(fUsername.getText());
        data.setOutputFileName(fOutputFileName.getText());
        data.setJqlSubFilter(fJQLSubFilter.getText());
        data.setPassword(String.valueOf(fPassword.getPassword()));
    }

    private void doConversion() {
        getData(this);

        List<String> missedParams = new ArrayList<>(10);
        if (getBoardUrl() == null || getBoardUrl().trim().isEmpty()) missedParams.add("Ссылка на доску");
        if (getUserName() == null || getUserName().trim().isEmpty()) missedParams.add("Пользователь");
        if (getPassword() == null || getPassword().trim().isEmpty()) missedParams.add("Пароль");
        if (getOutputFileName() == null || getOutputFileName().trim().isEmpty())
            missedParams.add("Файл для экспорта");
        if (missedParams.size() > 0) {
            showMessageDialog(getAppFrame(), "Не указаны обязательные параметры: " + String.join(", ", missedParams), "Ошибка", ERROR_MESSAGE);
            return;
        }

        // Парсим адрес доски. Вычисляя адрес jira и boardId (rapidView)
        String jiraUrl;
        String boardId = null;
        try {
            URL url = new URL(getBoardUrl());
            jiraUrl = url.getProtocol() + "://" + url.getHost() + (url.getPort() == -1 ? "" : ":" + url.getPort()) + "/";
            String query = url.getQuery();
            if (query != null && !query.trim().isEmpty()) {
                String[] tokens = query.split("&");
                for (String token : tokens) {
                    int i = token.indexOf("=");
                    if (i > 0 && token.substring(0, i).trim().equalsIgnoreCase("rapidView")) {
                        boardId = token.substring(i + 1).trim();
                        break;
                    }
                }
            }

            if (boardId == null || boardId.trim().isEmpty()
                    || url.getHost() == null || url.getHost().trim().isEmpty())
                throw new MalformedURLException("Указан неверный адрес доски");
        } catch (MalformedURLException e) {
            showMessageDialog(getAppFrame(), e.getMessage(), "Ошибка", ERROR_MESSAGE);
            return;
        }

        // Проверяем наличие выходного файла на диске
        File outputFile = new File(getOutputFileName());
        if (outputFile.exists() && showConfirmDialog(getAppFrame(), String.format("Файл %s существует. Перезаписать?", outputFile.getAbsoluteFile()), "Подтверждение", YES_NO_OPTION) != YES_OPTION) {
            showMessageDialog(getAppFrame(), "Конвертация остановлена", "Информация", INFORMATION_MESSAGE);
            return;
        }

        fLog.setText(null);
        enableControls(false);

        // Подключаемся к доске и конвертируем данные
        try {
            logger.info(String.format("Подключаемся к серверу: %s", jiraUrl));
            logger.info(String.format("Пользователь %s", getUserName()));

            JiraClient jiraClient = new JiraClient(jiraUrl, new BasicCredentials(getUserName(), getPassword()));
            Board board = Board.get(jiraClient.getRestClient(), Long.parseLong(boardId));

            var converter = new J2aaConverter.Builder()
                    .setBoard(board)
                    .setJqlSubFilter(jqlSubFilter)
                    .setUseMaxColumn(useMaxColumn)
                    .setFields(jiraFields)
                    .setOutputFile(outputFile)
                    .build();
            converter.doConversion();
        } catch (JiraException e) {
            Exception ex = (Exception) e.getCause();
            if (ex instanceof SSLPeerUnverifiedException)
                logger.info(String.format("SSL peer unverified: %s", ex.getMessage()));
            else if (ex instanceof UnknownHostException)
                logger.info(String.format("Не удается соединиться с сервером %s", ex.getMessage()));
            else if (ex instanceof RestException) {
                if (((RestException) ex).getHttpStatusCode() == 401) {
                    logger.info(ex.getMessage().substring(0, 56));
                } else {
                    logger.info(ex.getMessage());
                }
            } else
                logger.info(e.getMessage());
        } catch (IOException e) {
            logger.info(e.getMessage());
        } catch (InterruptedException e) {
            logger.info("Конвертация прервана");
        }
        conversionThread = null;
        enableControls(true);
    }

    private void enableControls(boolean state) {
        if (state)
            startButton.setText("Конвертировать");
        else
            startButton.setText("Остановить");
        loadSettingsButton.setEnabled(state);
        saveSettingsButton.setEnabled(state);
        selectOutputFileButton.setEnabled(state);
        fBoardURL.setEnabled(state);
        fJQLSubFilter.setEnabled(state);
        fOutputFileName.setEnabled(state);
        fUsername.setEnabled(state);
        fPassword.setEnabled(state);
    }

    public void setAppTitle() {
        String newTitle = DEFAULT_APP_TITLE
                + ((!version.isEmpty()) ? " v" + version : "")
                + (connProfile != null ? " [" + connProfile.getName() + "]" : "");
        getAppFrame().setTitle(newTitle);
    }

    {
// GUI initializer generated by IntelliJ IDEA GUI Designer
// >>> IMPORTANT!! <<<
// DO NOT EDIT OR ADD ANY CODE HERE!
        $$$setupUI$$$();
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        rootPanel = new JPanel();
        rootPanel.setLayout(new GridLayoutManager(6, 3, new Insets(0, 0, 0, 10), -1, -1));
        labelBoardUrl = new JLabel();
        labelBoardUrl.setText("Ссылка на доску*");
        rootPanel.add(labelBoardUrl, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        fBoardURL = new JTextField();
        rootPanel.add(fBoardURL, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JScrollPane scrollPane1 = new JScrollPane();
        rootPanel.add(scrollPane1, new GridConstraints(5, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        fLog = new JTextArea();
        fLog.setEditable(false);
        scrollPane1.setViewportView(fLog);
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1));
        rootPanel.add(panel1, new GridConstraints(5, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final Spacer spacer1 = new Spacer();
        panel1.add(spacer1, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        startButton = new JButton();
        startButton.setText("Конвертировать");
        panel1.add(startButton, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        loadSettingsButton = new JButton();
        loadSettingsButton.setText("Выбрать профиль");
        rootPanel.add(loadSettingsButton, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        saveSettingsButton = new JButton();
        saveSettingsButton.setText("Сохранить профиль");
        rootPanel.add(saveSettingsButton, new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        selectOutputFileButton = new JButton();
        selectOutputFileButton.setText("Обзор");
        rootPanel.add(selectOutputFileButton, new GridConstraints(2, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        fPassword = new JPasswordField();
        rootPanel.add(fPassword, new GridConstraints(4, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JLabel label1 = new JLabel();
        label1.setText("Пароль*");
        rootPanel.add(label1, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        fUsername = new JTextField();
        rootPanel.add(fUsername, new GridConstraints(3, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JLabel label2 = new JLabel();
        label2.setText("Пользователь*");
        rootPanel.add(label2, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        final JLabel label3 = new JLabel();
        label3.setText("Файл для экспорта*");
        rootPanel.add(label3, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        fOutputFileName = new JTextField();
        rootPanel.add(fOutputFileName, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JLabel label4 = new JLabel();
        label4.setText("Доп.JQL фильтр");
        rootPanel.add(label4, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        fJQLSubFilter = new JTextField();
        fJQLSubFilter.setText("");
        rootPanel.add(fJQLSubFilter, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return rootPanel;
    }

}
