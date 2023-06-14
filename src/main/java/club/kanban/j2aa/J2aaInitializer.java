package club.kanban.j2aa;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import club.kanban.j2aa.uilogger.UILogger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;

import static javax.swing.JOptionPane.showMessageDialog;

@Component
public class J2aaInitializer implements ApplicationRunner {
    @Autowired
    ApplicationContext context;

    @Autowired
    ConnectionProfile connectionProfile;

    @Autowired
    J2aaApp app;

    public static final String ARG_PROFILE = "profile";

    @Override
    public void run(ApplicationArguments args) {

        // Инициализация UI логгера
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        for (ch.qos.logback.classic.Logger logger : loggerContext.getLoggerList()) {
            for (Iterator<Appender<ILoggingEvent>> index = logger.iteratorForAppenders(); index.hasNext(); ) {
                Appender<ILoggingEvent> appender = index.next();
                if (appender instanceof UILogger)
                    ((UILogger) appender).setJ2aaApp(context.getBean(J2aaApp.class));
            }
        }

        if (args.containsOption(ARG_PROFILE)) {
            try {
                connectionProfile.readConnProfile(new File(args.getOptionValues(ARG_PROFILE).get(0)));
            } catch (IOException e) {
                showMessageDialog(app.getAppFrame(), e.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
            }
            app.setData(app);
            app.setAppTitle();
        }

        // Настраиваем ssl сертификаты из конфигурационного файла
        if (!app.getTrustStore().isEmpty()) {
            System.setProperty("javax.net.ssl.trustStore", app.getTrustStore());
        }
        if (!app.getTrustStorePassword().isEmpty()) {
            System.setProperty("javax.net.ssl.trustStorePassword", app.getTrustStorePassword());
        }
    }
}

