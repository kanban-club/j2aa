package club.kanban.j2aa;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import club.kanban.j2aa.jirarestclient.uilogger.UILogger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Iterator;

@Component
public class J2aaInitializer implements ApplicationRunner {
    @Autowired
    ApplicationContext context;

    @Autowired
    J2aaApp app;

    public static final String ARG_PROFILE = "profile";

    @Override
    public void run(ApplicationArguments args) throws Exception {

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
            app.readConnProfile(new File(args.getOptionValues(ARG_PROFILE).get(0)));
        }
    }
}

