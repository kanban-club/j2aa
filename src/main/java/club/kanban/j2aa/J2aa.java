package club.kanban.j2aa;

import club.kanban.j2aaconverter.J2aaConverter;
import club.kanban.jirarestclient.Board;
import lombok.Getter;
import lombok.Setter;
import net.rcarz.jiraclient.*;
import org.apache.commons.cli.*;
import org.apache.commons.io.FilenameUtils;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.*;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import static javax.swing.JFileChooser.APPROVE_OPTION;
import static javax.swing.JOptionPane.*;

public class J2aa {
    public static final String VERSION_KEY = "version";
    public static final String APP_CMD_LINE = "java -jar j2aa.jar";
    public static final String JVM_OPTIONS_FILE = "jvm.config";
    public static final String PROFILE_ARG = "profile";

    public static final String DEFAULT_APP_TITLE = "Jira to ActionableAgile converter";
    public static final String DEFAULT_CONNECTION_PROFILE_FORMAT = "xml";
    public static final String DEFAULT_PROFILE = "default_profile.xml";

    public static final String KEY_BOARD_URL = "board_url";
    public static final String KEY_USER_NAME = "user_name";
    public static final String KEY_PASSWORD = "password";
    public static final String KEY_OUTPUT_FILE = "output_file_name";
    public static final String KEY_JQL_SUB_FILTER = "jql_sub_filter";

    @Getter
    private final JFrame appFrame;
    @Getter
    @Setter
    private String appTitle;
    @Getter
    @Setter
    private String boardUrl;
    @Getter
    @Setter
    private String userName;
    @Getter
    @Setter
    private String password;
    @Getter
    @Setter
    private String outputFileName;
    @Getter
    @Setter
    private String jqlSubFilter;
    @Getter
    @Setter
    private File connProfile;
    private String lastConnFileDir;

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

    private void createUIComponents() {
        // TODO: place custom component creation code here
    }

    public J2aa() {
        super();
        appFrame = new JFrame();

        try {
            String version = getVersionFromManifest();
            if (version != null)
                setAppTitle(DEFAULT_APP_TITLE + " v" + version);
            else
                setAppTitle(DEFAULT_APP_TITLE);
        } catch (Exception ignored) {
        }

        appFrame.setTitle(getAppTitle());
        appFrame.setContentPane(rootPanel);
        appFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        appFrame.setPreferredSize(new Dimension(800, 450));
        appFrame.pack();
        appFrame.setVisible(true);
        appFrame.getRootPane().setDefaultButton(startButton);

        startButton.addActionListener(actionEvent -> {
            Runnable r = this::doConversion;
            r.run();
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
                    showMessageDialog(/*appFrame.getContentPane()*/ getAppFrame(), String.format("Настройки сохранены в файл %s", file.getName()), "Сохранение настроек", INFORMATION_MESSAGE);
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
                    setData(this);
                    fLog.setText(null);
                    lastConnFileDir = chooser.getSelectedFile().getParent();
                } catch (IOException ex) {
                    showMessageDialog(getAppFrame(), String.format("не удалось прочитать файл %s", chooser.getSelectedFile().getName()), "Ошибка чтения файла", ERROR_MESSAGE);
                }
            }
        });
        selectOutputFileButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("выберите расположение и имя файла");
            chooser.setFileFilter(new FileNameExtensionFilter("CSV files", "csv"));
            chooser.setSelectedFile(new File(fOutputFileName.getText()));
            chooser.setCurrentDirectory(new File(fOutputFileName.getText()).getAbsoluteFile().getParentFile());
            if (chooser.showSaveDialog(getAppFrame()) == APPROVE_OPTION) {
                File file = chooser.getSelectedFile();
                if (FilenameUtils.getExtension(file.getName()).equals(""))
                    file = new File(file.getAbsoluteFile() + ".csv");
                fOutputFileName.setText(file.getAbsolutePath());
                getData(this);
            }
        });
    }

    public static void main(String[] args) {
        new J2aa().run(args);
    }

    public void run(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }

        File jvmOptionsFile = new File(JVM_OPTIONS_FILE);
        if (jvmOptionsFile.exists()) readJVMOptions(jvmOptionsFile);

        try {
            parseCommandLine(args);
        } catch (ParseException e) {
            showMessageDialog(getAppFrame(), e.getMessage(), "Ошибка", ERROR_MESSAGE);
            System.exit(1);
        } /* catch (IOException e) {
            showMessageDialog(getAppFrame(), e.getMessage(), "Ошибка", ERROR_MESSAGE);
        }*/

        lastConnFileDir = System.getProperty("user.dir");
        try {
            if ((getConnProfile() != null && !getConnProfile().getName().trim().isEmpty()) && getConnProfile().exists())
                readConnProfile(getConnProfile());
            else {
                setDefaults();
            }

            if (getConnProfile() != null)
                lastConnFileDir = connProfile.getAbsoluteFile().getParent();
        } catch (IOException e) {
            showMessageDialog(getAppFrame(), e.getMessage(), "Ошибка", ERROR_MESSAGE);
            setConnProfile(null);
        }
    }

    private void setDefaults() throws IOException {
        File defaultProfile = new File(DEFAULT_PROFILE);
        if (defaultProfile.exists())
            readConnProfile(defaultProfile);
    }

    private void readJVMOptions(File file) {
        try {
            FileInputStream fis = new FileInputStream(file.getAbsolutePath());
            Properties p = new Properties();
            if (FilenameUtils.getExtension(file.getName()).equalsIgnoreCase("xml"))
                p.loadFromXML(fis);
            else
                p.load(fis);

            for (Object key : p.keySet()) {
                System.setProperty(key.toString(), p.getProperty(key.toString()));
            }
        } catch (Exception ignored) {
        }
    }

    private void readConnProfile(File file) throws IOException {
        Properties p = new Properties();
        FileInputStream fis = new FileInputStream(file.getAbsoluteFile());

        if (FilenameUtils.getExtension(file.getName()).equalsIgnoreCase("xml"))
            p.loadFromXML(fis);
        else
            p.load(fis);

        setBoardUrl(p.getProperty(KEY_BOARD_URL));
        setUserName(p.getProperty(KEY_USER_NAME));

        setPassword(p.getProperty(KEY_PASSWORD));
        setJqlSubFilter(p.getProperty(KEY_JQL_SUB_FILTER));

        setOutputFileName(p.getProperty(KEY_OUTPUT_FILE));
        if (this.getBoardUrl() == null || this.getBoardUrl().trim().equals("")
                || this.getUserName() == null || getUserName().trim().equals("")
        )
            throw new InvalidPropertiesFormatException(String.format("Не заполнены обязательные поля %s",
                    (this.getBoardUrl() == null || this.getBoardUrl().trim().equals("") ? " " + KEY_BOARD_URL : "") +
                            (this.getUserName() == null || getUserName().trim().equals("") ? " " + KEY_USER_NAME : "")
            ));

        this.getAppFrame().setTitle(getAppTitle() + " [" + file.getName() + "]");
        setData(this);
        setConnProfile(file);
    }

    private void writeConnProfile(File file) throws IOException {
        getData(this);

        Properties p = new Properties();
        FileOutputStream fos = new FileOutputStream(file.getAbsoluteFile());
        p.setProperty(KEY_BOARD_URL, this.getBoardUrl());
        p.setProperty(KEY_USER_NAME, this.getUserName());

        p.setProperty(KEY_OUTPUT_FILE, this.getOutputFileName().trim());
        p.setProperty(KEY_JQL_SUB_FILTER, this.getJqlSubFilter());

        if (FilenameUtils.getExtension(file.getName()).equalsIgnoreCase("xml"))
            p.storeToXML(fos, null);
        else
            p.store(fos, null);

        fos.flush();
        fos.close();
        this.getAppFrame().setTitle(getAppTitle() + " [" + file.getName() + "]");
        setConnProfile(file);
    }

    private void parseCommandLine(String[] args) throws ParseException {
        Options options = new Options();
        Option argument;
        argument = new Option("profile", true, "Connection profile (.xml)");
        options.addOption(argument);
        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        formatter.setOptionComparator(null);
        CommandLine cmd;
        try {
            cmd = parser.parse(options, args);
            String profileName = cmd.getOptionValue(PROFILE_ARG);
            if (profileName != null)
                setConnProfile(new File(profileName));
        } catch (ParseException e) {
            StringWriter writer = new StringWriter();
            formatter.printHelp(new PrintWriter(writer), 800, APP_CMD_LINE, null, options, 0, 5, null, true);
            throw new ParseException("Ошибка: " + e.getMessage() + "\n" + writer);
        }
    }

    private String getVersionFromManifest() throws IOException {
        InputStream manifestStream = getClass().getClassLoader().getResourceAsStream("META-INF/MANIFEST.MF");
        if (manifestStream != null) {
            Manifest manifest = new Manifest(manifestStream);
            Attributes attributes = manifest.getMainAttributes();
            return attributes.getValue(VERSION_KEY);
        }
        return null;
    }

    public void setData(J2aa data) {
        fBoardURL.setText(data.getBoardUrl());
        fUsername.setText(data.getUserName());
        fOutputFileName.setText(data.getOutputFileName());
        fJQLSubFilter.setText(data.getJqlSubFilter());
        fPassword.setText(data.getPassword());
    }

    public void getData(J2aa data) {
        data.setBoardUrl(fBoardURL.getText());
        data.setUserName(fUsername.getText());
        data.setOutputFileName(fOutputFileName.getText());
        data.setJqlSubFilter(fJQLSubFilter.getText());
        data.setPassword(String.valueOf(fPassword.getPassword()));
    }

    private void doConversion() {
        getData(this);

        List<String> missedparams = new ArrayList<>(10);
        if (getBoardUrl() == null || getBoardUrl().trim().isEmpty()) missedparams.add("Ссылка на доску");
        if (getUserName() == null || getUserName().trim().isEmpty()) missedparams.add("Пользователь");
        if (getPassword() == null || getPassword().trim().isEmpty()) missedparams.add("Пароль");
        if (getOutputFileName() == null || getOutputFileName().trim().isEmpty())
            missedparams.add("Файл для экспорта");
        if (missedparams.size() > 0) {
            showMessageDialog(getAppFrame(), "Не указаны обязательные параметры: " + String.join(", ", missedparams), "Ошибка", ERROR_MESSAGE);
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
//                String[] tokens = query.split("[\\&]");
                String[] tokens = query.split("[&]");
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
            showMessageDialog(getAppFrame(), "Конвертайция остановлена", "Информация", INFORMATION_MESSAGE);
            return;
        }

        startButton.setEnabled(false);
        startButton.update(startButton.getGraphics());

        fLog.setText(null);

        fLog.append(String.format("Подключаемся к серверу: %s\n", jiraUrl));
        fLog.append(String.format("Пользователь %s\n", getUserName()));
        fLog.update(fLog.getGraphics());

        J2aaConverter converter = new J2aaConverter();
// Подключаемся к доске и конвертируем данные
        try {
            startButton.setEnabled(false);
            startButton.update(startButton.getGraphics());

            JiraClient jiraClient = new JiraClient(jiraUrl, new BasicCredentials(getUserName(), getPassword()));
            Board board = Board.get(jiraClient.getRestClient(), Long.parseLong(boardId));
            fLog.append(String.format("Установлено соединение с доской: %s\n", board.getName()));
            fLog.update(fLog.getGraphics());

            Date startDate = new Date();

            converter.importFromJira_v2(board, getJqlSubFilter(), (current, max) -> {
                if (max > 0) fLog.append(String.format("%d из %d issues получено\n", current, max));
                fLog.update(fLog.getGraphics());
            });

            if (converter.getBoardIssues().size() > 0) {
                Date endDate = new Date();
                long timeInSec = (endDate.getTime() - startDate.getTime()) / 1000;
                fLog.append(String.format("Всего получено: %d issues. Время: %d сек. Скорость: %.2f issues/сек\n", converter.getBoardIssues().size(), timeInSec, (1.0 * converter.getBoardIssues().size()) / timeInSec));
                fLog.update(fLog.getGraphics());

                // экспортируем данные в csv файл
                converter.export2File(outputFile);
                fLog.append(String.format("\nДанные выгружены в файл:\n%s\n", outputFile.getAbsoluteFile()));
            } else fLog.append("Не найдены элементы для выгрузки, соответствующие заданным критериям.");
        } catch (JiraException e) {
            Exception ex = (Exception) e.getCause();
            if (ex instanceof SSLPeerUnverifiedException)
                fLog.append(String.format("SSL peer unverified: %s\n", ex.getMessage()));
            else if (ex instanceof UnknownHostException)
                fLog.append(String.format("Не удается соединиться с сервером %s\n", ex.getMessage()));
            else if (ex instanceof RestException) {
                if (((RestException) ex).getHttpStatusCode() == 401) {
                    fLog.append(ex.getMessage().substring(0, 56));
                } else {
                    fLog.append(ex.getMessage());
                }
            } else
                fLog.append(e.getMessage());

        } catch (IOException e) {
            fLog.append(e.getMessage());
        } finally {
            startButton.setEnabled(true);
            startButton.update(startButton.getGraphics());
        }
    }
}
