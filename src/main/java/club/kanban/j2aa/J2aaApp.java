package club.kanban.j2aa;

import club.kanban.j2aa.j2aaconverter.J2aaConverter;
import club.kanban.j2aa.j2aaconverter.fileadapters.FileAdapterFactory;
import club.kanban.j2aa.jirarestclient.uilogger.UILogInterface;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import lombok.Getter;
import net.rcarz.jiraclient.BasicCredentials;
import net.rcarz.jiraclient.JiraException;
import net.rcarz.jiraclient.RestException;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

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
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

import static javax.swing.JFileChooser.APPROVE_OPTION;
import static javax.swing.JOptionPane.*;

@SpringBootApplication
public class J2aaApp extends JFrame implements UILogInterface {
    private static final Logger logger = LoggerFactory.getLogger(J2aaApp.class);
    public static final String CONFIG_FILE_NAME = ".j2aa";
    private static final String DEFAULT_APP_TITLE = "Jira to ActionableAgile converter";
    private static final String DEFAULT_CONNECTION_PROFILE_FORMAT = "xml";
    private static final String KEY_VERSION = "version";
    private static final LocalDate expires = LocalDate.of(2023, 6, 16);

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

    private volatile Thread conversionThread = null;

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
            chooser.setSelectedFile(getConnectionProfile().getFile());
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

                    getData(this);
                    connectionProfile.writeConnProfile(file);
                    setAppTitle();

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
                    connectionProfile.readConnProfile(chooser.getSelectedFile());
                    setData(this);
                    setAppTitle();
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
                            if (showConfirmDialog(getAppFrame(), String.format("Перейти на доску '%s'?", url), "Открытие страницы", YES_NO_OPTION, INFORMATION_MESSAGE) == YES_OPTION) {
                                desktop.browse(URI.create(fBoardURL.getText()));
                            }
                        } catch (IOException ignored) {
                        }
                    }
                }
            }
        });
        //TODO Удалить поскольку это дублирование следующего обработчика
//        labelsJiraFields.addMouseListener(new MouseAdapter() {
//            @Override
//            public void mouseClicked(MouseEvent e) {
//                super.mouseClicked(e);
//                if (e.getClickCount() == 2) {
//                    if (!labelsJiraFields.getText().isEmpty()) {
//                        if (showConfirmDialog(getAppFrame(), "Заменить настройки для полей jira на значения по-умолчанию?", "Подтверждение", YES_NO_OPTION) != YES_NO_OPTION) {
//                            return;
//                        }
//                    }
//                    fJiraFields.setText("issuetype, labels");
//                }
//            }
//        });

        labelsJiraFields.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                if (e.getClickCount() == 2) {
                    if (!labelsJiraFields.getText().isEmpty()) {
                        if (showConfirmDialog(getAppFrame(), "Заменить настройки для полей jira на значения по-умолчанию?", "Подтверждение", YES_NO_OPTION) != YES_NO_OPTION) {
                            return;
                        }
                    }
                    File configFile = new File(System.getProperty("user.home"), CONFIG_FILE_NAME);
                    try (FileInputStream fileInputStream = new FileInputStream(configFile.getAbsoluteFile())) {
                        Properties properties = new Properties();
                        properties.load(fileInputStream);
                        fJiraFields.setText(properties.getProperty("jira-fields"));
                    } catch (Exception ignored) {}

                }
            }
        });
    }

    public static void main(String[] args) {
        if (expires != null && expires.isBefore(LocalDate.now())) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            String errorMsg = String.format("Срок действия данной версии закончился %s.\nОбновите приложение до актуальной версии", expires.format(formatter));
            showMessageDialog(null, errorMsg);
            logger.info(errorMsg);
            System.exit(-1);
        }

        File configFile = new File(System.getProperty("user.home") + "/" + CONFIG_FILE_NAME);
        if (!configFile.exists()) {
            try {
                IOUtils.copy(Objects.requireNonNull(Thread.currentThread().getContextClassLoader().getResourceAsStream(CONFIG_FILE_NAME)), new FileOutputStream(configFile));
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
    }

    private void doConversion() {
        getData(this);

        List<String> missedParams = new ArrayList<>(10);
        if (getUserName() == null || getUserName().trim().isEmpty()) missedParams.add("Пользователь");
        if (getPassword() == null || getPassword().trim().isEmpty()) missedParams.add("Пароль");
        if (connectionProfile.getBoardAddress() == null || connectionProfile.getBoardAddress().trim().isEmpty())
            missedParams.add("Ссылка на доску");
        if (connectionProfile.getOutputFileName() == null || connectionProfile.getOutputFileName().trim().isEmpty())
            missedParams.add("Файл для экспорта");

        if (missedParams.size() > 0) {
            showMessageDialog(getAppFrame(), "Не указаны обязательные параметры: " + String.join(", ", missedParams), "Ошибка", ERROR_MESSAGE);
            return;
        }

        // Проверяем наличие выходного файла на диске
        File outputFile = new File(connectionProfile.getOutputFileName());
        if (outputFile.exists() && showConfirmDialog(getAppFrame(), String.format("Файл %s существует. Перезаписать?", outputFile.getAbsoluteFile()), "Подтверждение", YES_NO_OPTION) != YES_OPTION) {
            showMessageDialog(getAppFrame(), "Конвертация остановлена", "Информация", INFORMATION_MESSAGE);
            return;
        }

        fLog.setText(null);
        enableControls(false);

        // Подключаемся к доске и конвертируем данные
        try {
            logger.info(String.format("Пользователь %s", getUserName()));
            var converter = new J2aaConverter(new BasicCredentials(getUserName(), getPassword()), connectionProfile);
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
            } else logger.info(e.getMessage());
        } catch (IOException e) {
            logger.info(e.getMessage());
        } catch (InterruptedException e) {
            logger.info("Конвертация прервана");
        }

        conversionThread = null;
        enableControls(true);
    }

    private void enableControls(boolean state) {
        if (state) startButton.setText("Конвертировать");
        else startButton.setText("Остановить");
        loadSettingsButton.setEnabled(state);
        saveSettingsButton.setEnabled(state);
        selectOutputFileButton.setEnabled(state);
        fBoardURL.setEnabled(state);
        fJQLSubFilter.setEnabled(state);
        fOutputFileName.setEnabled(state);
        fUsername.setEnabled(state);
        fPassword.setEnabled(state);
        fJiraFields.setEnabled(state);
    }

    public void setAppTitle() {
        StringBuffer buffer = new StringBuffer(DEFAULT_APP_TITLE);
        if (!version.isEmpty()) {
            buffer.append(" v");
            buffer.append(version);
        }

        if (expires != null) {
            buffer.append(" (expires ");
            buffer.append(expires);
            buffer.append(")");
        }

        if (connectionProfile.getFile() != null) {
            buffer.append(" [");
            buffer.append(connectionProfile.getFile().getName());
            buffer.append("]");
        }
        getAppFrame().setTitle(buffer.toString());

        // TODO убрать закоменнтированный код
//        String newTitle = DEFAULT_APP_TITLE + ((!version.isEmpty()) ? " v" + version : "")
//                + (connectionProfile.getFile() != null ? " [" + connectionProfile.getFile().getName() + "]" : "");
//        getAppFrame().setTitle(newTitle);
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
        rootPanel.setLayout(new GridLayoutManager(7, 3, new Insets(0, 0, 0, 10), -1, -1));
        labelBoardUrl = new JLabel();
        labelBoardUrl.setText("Ссылка на доску*");
        rootPanel.add(labelBoardUrl, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        fBoardURL = new JTextField();
        rootPanel.add(fBoardURL, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JScrollPane scrollPane1 = new JScrollPane();
        rootPanel.add(scrollPane1, new GridConstraints(6, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        fLog = new JTextArea();
        fLog.setEditable(false);
        scrollPane1.setViewportView(fLog);
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1));
        rootPanel.add(panel1, new GridConstraints(6, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
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
        rootPanel.add(fPassword, new GridConstraints(5, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JLabel label1 = new JLabel();
        label1.setText("Пароль*");
        rootPanel.add(label1, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        fUsername = new JTextField();
        rootPanel.add(fUsername, new GridConstraints(4, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JLabel label2 = new JLabel();
        label2.setText("Пользователь*");
        rootPanel.add(label2, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
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
        fJiraFields.setToolTipText("issuetype, labels, epic, priority, components, project, assignee, reporter, projectkey, fixVersions, summary");
        rootPanel.add(fJiraFields, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        labelsJiraFields = new JLabel();
        labelsJiraFields.setText("Поля jira");
        rootPanel.add(labelsJiraFields, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return rootPanel;
    }

}
