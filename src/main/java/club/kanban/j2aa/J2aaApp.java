package club.kanban.j2aa;

import club.kanban.j2aa.j2aaconverter.J2aaConverter;
import club.kanban.j2aa.j2aaconverter.fileadapters.FileAdapterFactory;
import club.kanban.j2aa.jiraclient.JiraClient;
import club.kanban.j2aa.jiraclient.JiraException;
import club.kanban.j2aa.uilogger.UILogInterface;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import lombok.Getter;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

import javax.annotation.PostConstruct;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.FontUIResource;
import javax.swing.text.StyleContext;
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
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.*;

import static javax.swing.JFileChooser.APPROVE_OPTION;
import static javax.swing.JOptionPane.*;

@SpringBootApplication
public class J2aaApp extends JFrame implements UILogInterface {
    private static final Logger logger = LoggerFactory.getLogger(J2aaApp.class);
    public static final String CONFIG_FILE_NAME = ".j2aa";
    private static final String DEFAULT_APP_TITLE = "Jira to ActionableAgile converter";
    private static final String DEFAULT_CONNECTION_PROFILE_FORMAT = "xml";
    private static final String KEY_VERSION = "version";
    private static final LocalDate expires = LocalDate.of(2023, 12, 31);

    @Getter
    private final JFrame appFrame;

    @Value("${username:}")
    @Getter
    private String userName;

    @Value("${password:}")
    @Getter
    private String password;

    @Autowired
    @Getter
    private ConnectionProfile connectionProfile;
    @Value("${user.dir}")
    private String lastConnFileDir;

    @Getter
    @Value("${" + KEY_VERSION + ":}")
    private String version;

    @Getter
    @Value("${javax.net.ssl.trustStore:}")
    private String trustStore;

    @Getter
    @Value("${javax.net.ssl.trustStorePassword:}")
    private String trustStorePassword;

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
    private JTextField fJiraFields;
    private JLabel labelsJiraFields;
    private JCheckBox fExportBlockersCalendar;

    private volatile Thread conversionThread;

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
                if (conversionThread.isAlive()) {
                    logger.info("Прерывание конвертации ...");
                    conversionThread.interrupt();
                } else {
                    conversionThread = null;
                }
            }
        });

        saveSettingsButton.addActionListener(actionEvent -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setCurrentDirectory(new File(lastConnFileDir));
            chooser.setSelectedFile(getConnectionProfile().getFile());
            chooser.setDialogTitle("Укажите файл для сохранения настроек");
            chooser.setFileFilter(new FileNameExtensionFilter("Connection profiles (.xml)", "xml"));
            int returnVal = chooser.showSaveDialog(getAppFrame());
            if (returnVal == APPROVE_OPTION) {
                try {
                    File file = chooser.getSelectedFile();
                    if (FilenameUtils.getExtension(file.getAbsolutePath()).equals(""))
                        file = new File(file.getAbsoluteFile() + "." + DEFAULT_CONNECTION_PROFILE_FORMAT);

                    if (file.exists() && showConfirmDialog(getAppFrame(),
                            String.format("Файл %s уже существует. Перезаписать?", file.getAbsoluteFile()),
                            "Подтверждение", YES_NO_OPTION) != YES_OPTION)
                        return;

                    getData(this);
                    connectionProfile.writeConnProfile(file);
                    setAppTitle();

                    lastConnFileDir = file.getParent();
                    showMessageDialog(getAppFrame(), String.format("Настройки сохранены в файл %s", file.getName()),
                            "Сохранение настроек", INFORMATION_MESSAGE);
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
                    getData(this);
                    connectionProfile.readConnProfile(chooser.getSelectedFile());
                    setData(this);
                    setAppTitle();
                    fLog.setText(null);
                    lastConnFileDir = chooser.getSelectedFile().getParent();
                } catch (IOException ex) {
                    showMessageDialog(getAppFrame(),
                            String.format("Не удалось прочитать файл %s", chooser.getSelectedFile().getName()),
                            "Ошибка чтения файла", ERROR_MESSAGE);
                }
            }
        });

        selectOutputFileButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Выберите расположение и имя файла");

            FileAdapterFactory faf = J2aaConfig.getContext().getBean(FileAdapterFactory.class);
            faf.getFormats().forEach((ext, desc) -> {
                FileNameExtensionFilter filter = new FileNameExtensionFilter(desc, ext);
                chooser.addChoosableFileFilter(filter);
                if (ext.equalsIgnoreCase("csv")) chooser.setFileFilter(filter);
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
                            if (showConfirmDialog(getAppFrame(), String.format("Перейти на доску '%s'?", url),
                                    "Открытие страницы", YES_NO_OPTION, INFORMATION_MESSAGE) == YES_OPTION) {
                                desktop.browse(URI.create(fBoardURL.getText()));
                            }
                        } catch (IOException ignored) {
                        }
                    }
                }
            }
        });

        labelsJiraFields.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                if (e.getClickCount() == 2) {
                    if (!labelsJiraFields.getText().isEmpty()) {
                        if (showConfirmDialog(
                                getAppFrame(),
                                "Заменить настройки для полей jira на значения по-умолчанию?",
                                "Подтверждение",
                                YES_NO_OPTION) != YES_OPTION) {
                            return;
                        }
                    }
                    File configFile = new File(System.getProperty("user.home"), CONFIG_FILE_NAME);
                    try (FileInputStream fileInputStream = new FileInputStream(configFile.getAbsoluteFile())) {
                        Properties properties = new Properties();
                        properties.load(fileInputStream);
                        fJiraFields.setText(properties.getProperty("jira-fields"));
                    } catch (Exception ignored) {
                    }

                }
            }
        });
    }

    public static void main(String[] args) {
        if (expires != null && expires.isBefore(LocalDate.now())) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            String errorMsg = String.format(
                    "Срок действия данной версии закончился %s.\nОбновите приложение до актуальной версии",
                    expires.format(formatter));
            showMessageDialog(null, errorMsg);
            logger.info(errorMsg);
            System.exit(-1);
        }

        File configFile = new File(System.getProperty("user.home") + "/" + CONFIG_FILE_NAME);
        if (!configFile.exists()) {
            try {
                IOUtils.copy(
                        Objects.requireNonNull(Thread
                                .currentThread()
                                .getContextClassLoader()
                                .getResourceAsStream(CONFIG_FILE_NAME)),
                        new FileOutputStream(configFile));
            } catch (IOException e) {
                logger.error("Ошибка создания конфигурационного файла '{}'", configFile.getAbsoluteFile());
            }
        }

//        SpringApplication.run(J2aaApp.class);
        new SpringApplicationBuilder(J2aaApp.class).headless(false).run(args);
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
     * Передает значения переменных приложения в поля экранной формы
     *
     * @param data класс приложения
     */
    public void setData(J2aaApp data) {
        fUsername.setText(data.userName);
        fPassword.setText(data.getPassword());

        fBoardURL.setText(data.connectionProfile.getBoardAddress());
        fOutputFileName.setText(data.connectionProfile.getOutputFileName());
        fJQLSubFilter.setText(data.connectionProfile.getJqlSubFilter());
        fJiraFields.setText(String.join(", ", data.connectionProfile.getJiraFields()));
        fExportBlockersCalendar.setSelected(data.connectionProfile.isExportBlockersCalendar());
    }

    /**
     * Передает значения полей экранной формы в переменные приложения
     *
     * @param data класс приложения
     */
    public void getData(J2aaApp data) {
        data.userName = fUsername.getText();
        data.password = String.valueOf(fPassword.getPassword());

        data.connectionProfile.setBoardAddress(fBoardURL.getText());
        data.connectionProfile.setOutputFileName(fOutputFileName.getText());
        data.connectionProfile.setJqlSubFilter(fJQLSubFilter.getText());
        data.connectionProfile.setJiraFields(fJiraFields.getText().split("\\s*,\\s*"));
        data.connectionProfile.setExportBlockersCalendar(fExportBlockersCalendar.isSelected());
    }

    private void doConversion() {
        getData(this);

        List<String> missedParams = new ArrayList<>(10);
        if (getUserName() == null || getUserName().trim().isEmpty()) {
            missedParams.add("Пользователь");
        }
        if (getPassword() == null || getPassword().trim().isEmpty()) {
            missedParams.add("Пароль");
        }
        if (connectionProfile.getBoardAddress() == null || connectionProfile.getBoardAddress().trim().isEmpty()) {
            missedParams.add("Ссылка на доску");
        }
        if (connectionProfile.getOutputFileName() == null || connectionProfile.getOutputFileName().trim().isEmpty()) {
            missedParams.add("Файл для экспорта");
        }
        if (missedParams.size() > 0) {
            showMessageDialog(getAppFrame(), "Не указаны обязательные параметры: "
                    + String.join(", ", missedParams), "Ошибка", ERROR_MESSAGE);
            return;
        }

        File outputFile = new File(connectionProfile.getOutputFileName());
        if (outputFile.exists()) {
            try {
                archiveFile(outputFile);
            } catch (IOException ignored) {
            }
        }

        URL boardUrl;
        try {
            boardUrl = new URL(connectionProfile.getBoardAddress());
        } catch (MalformedURLException e) {
            logger.info("Неверный формат адреса\n{}", connectionProfile.getBoardAddress());
            return;
        }

        fLog.setText(null);
        enableControls(false);

        // Подключаемся к доске и конвертируем данные
        try (JiraClient jiraClient = JiraClient
                .builder(boardUrl, getUserName(), getPassword())
                .withUrlPathPrefix(connectionProfile.getUrlPathPrefix())
                .build()
        ) {
            logger.info(String.format("Пользователь %s", getUserName()));

            J2aaConverter converter = J2aaConverter.builder(jiraClient, boardUrl)
                    .withJiraFields(Arrays.asList(connectionProfile.getJiraFields()))
                    .withJqlSubFilter(connectionProfile.getJqlSubFilter())
                    .withUseMaxColumn(connectionProfile.isUseMaxColumn())
                    .build();

            LocalDateTime startDate = LocalDateTime.now();
            if (converter.fetchData() > 0) {
                LocalDateTime endDate = LocalDateTime.now();
                long timeInSec = Duration.between(startDate, endDate).getSeconds();
                logger.info(String.format(
                        "Всего получено: %d issues. Время: %d сек. Скорость: %.2f issues/сек",
                        converter.getConvertedIssues().size(),
                        timeInSec,
                        (1.0 * converter.getConvertedIssues().size()) / timeInSec));
                converter.exportIssues(connectionProfile.getOutputFileName());

                if (connectionProfile.isExportBlockersCalendar()) {
                    converter.exportBlockers(connectionProfile.getOutputFileName());
                }
            } else {
                logger.info("Не найдены элементы для выгрузки, соответствующие заданным критериям.");
            }
        } catch (
                JiraException e) {
            if (e.getCause() instanceof SSLHandshakeException || e.getCause() instanceof SSLPeerUnverifiedException) {
                logger.info("Ошибка проверки SSL сертификата хоста.\n"
                        + "Попробуйте указать следующие параметры Java VM при запуске программы:\n"
                        + " -Djavax.net.ssl.trustStore=<your_cacerts_store>\n"
                        + " -Djavax.net.ssl.trustStorePassword=<your_cacerts_store_password>");
            } else if (e.getCause() instanceof UnknownHostException) {
                logger.info("Адрес не найден.\n'{}'", connectionProfile.getBoardAddress());
            } else {
                logger.info(e.getMessage());
            }
        } catch (
                Exception e) {
            if (e.getCause() instanceof InterruptedException) {
                logger.info("Конвертация прервана.");
            } else {
                logger.info(e.getMessage());
            }
        }

        conversionThread = null;

        enableControls(true);
    }

    private void archiveFile(File file) throws IOException {
        BasicFileAttributes attr = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
        LocalDateTime fileDateTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(attr.lastModifiedTime().toMillis()), ZoneId.systemDefault());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("_yyyyMMdd_HHmm");

        String path = FilenameUtils.getFullPath(file.getAbsolutePath());
        String name = FilenameUtils.getBaseName(file.getAbsolutePath());
        String ext = FilenameUtils.getExtension(file.getAbsolutePath());
        File newFile = new File(path
                + name
                + fileDateTime.format(formatter) + (!ext.isEmpty() ? "." + ext : "")
        );
        file.renameTo(newFile);
    }

    private void enableControls(boolean state) {
        if (state) {
            startButton.setText("Конвертировать");
        } else {
            startButton.setText("Остановить");
        }
        loadSettingsButton.setEnabled(state);
        saveSettingsButton.setEnabled(state);
        selectOutputFileButton.setEnabled(state);
        fBoardURL.setEnabled(state);
        fJQLSubFilter.setEnabled(state);
        fOutputFileName.setEnabled(state);
        fUsername.setEnabled(state);
        fPassword.setEnabled(state);
        fJiraFields.setEnabled(state);
        fExportBlockersCalendar.setEnabled(state);
    }

    public void setAppTitle() {
        StringBuilder builder = new StringBuilder(DEFAULT_APP_TITLE);
        if (!version.isEmpty()) {
            builder.append(" v").append(version);
        }

        if (expires != null) {
            builder.append(" (expires ").append(expires).append(")");
        }

        if (connectionProfile.getFile() != null) {
            builder.append(" [").append(connectionProfile.getFile().getName()).append("]");
        }
        getAppFrame().setTitle(builder.toString());
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
        rootPanel.setLayout(new GridLayoutManager(8, 3, new Insets(0, 0, 0, 10), -1, -1));
        labelBoardUrl = new JLabel();
        labelBoardUrl.setText("Ссылка на доску*");
        rootPanel.add(labelBoardUrl, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        fBoardURL = new JTextField();
        rootPanel.add(fBoardURL, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JScrollPane scrollPane1 = new JScrollPane();
        rootPanel.add(scrollPane1, new GridConstraints(7, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        fLog = new JTextArea();
        fLog.setEditable(false);
        scrollPane1.setViewportView(fLog);
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1));
        rootPanel.add(panel1, new GridConstraints(7, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
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
        rootPanel.add(selectOutputFileButton, new GridConstraints(3, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        fPassword = new JPasswordField();
        rootPanel.add(fPassword, new GridConstraints(6, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JLabel label1 = new JLabel();
        label1.setText("Пароль*");
        rootPanel.add(label1, new GridConstraints(6, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        fUsername = new JTextField();
        rootPanel.add(fUsername, new GridConstraints(5, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JLabel label2 = new JLabel();
        label2.setText("Пользователь*");
        rootPanel.add(label2, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        final JLabel label3 = new JLabel();
        label3.setText("Файл для экспорта*");
        rootPanel.add(label3, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        fOutputFileName = new JTextField();
        rootPanel.add(fOutputFileName, new GridConstraints(3, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JLabel label4 = new JLabel();
        label4.setText("Доп.JQL фильтр");
        rootPanel.add(label4, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        fJQLSubFilter = new JTextField();
        fJQLSubFilter.setText("");
        rootPanel.add(fJQLSubFilter, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        fJiraFields = new JTextField();
        fJiraFields.setToolTipText("issuetype, labels, epic, priority, components, project, assignee, reporter, projectkey, fixVersions");
        rootPanel.add(fJiraFields, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        labelsJiraFields = new JLabel();
        labelsJiraFields.setText("Поля jira");
        rootPanel.add(labelsJiraFields, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        fExportBlockersCalendar = new JCheckBox();
        Font fExportBlockersCalendarFont = this.$$$getFont$$$(null, Font.PLAIN, -1, fExportBlockersCalendar.getFont());
        if (fExportBlockersCalendarFont != null) fExportBlockersCalendar.setFont(fExportBlockersCalendarFont);
        fExportBlockersCalendar.setText("выгрузить календарь блокировок");
        rootPanel.add(fExportBlockersCalendar, new GridConstraints(4, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    private Font $$$getFont$$$(String fontName, int style, int size, Font currentFont) {
        if (currentFont == null) return null;
        String resultName;
        if (fontName == null) {
            resultName = currentFont.getName();
        } else {
            Font testFont = new Font(fontName, Font.PLAIN, 10);
            if (testFont.canDisplay('a') && testFont.canDisplay('1')) {
                resultName = fontName;
            } else {
                resultName = currentFont.getName();
            }
        }
        Font font = new Font(resultName, style >= 0 ? style : currentFont.getStyle(), size >= 0 ? size : currentFont.getSize());
        boolean isMac = System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH).startsWith("mac");
        Font fontWithFallback = isMac ? new Font(font.getFamily(), font.getStyle(), font.getSize()) : new StyleContext().getFont(font.getFamily(), font.getStyle(), font.getSize());
        return fontWithFallback instanceof FontUIResource ? fontWithFallback : new FontUIResource(fontWithFallback);
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return rootPanel;
    }
}
