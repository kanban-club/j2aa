package club.kanban.j2aa;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import lombok.Setter;

public class UILogger extends AppenderBase<ILoggingEvent> {
    @Setter
    private J2aaApp j2aaApp;

    @Override
    protected void append(ILoggingEvent iLoggingEvent) {
        if (j2aaApp != null) {
            j2aaApp.logToUI(iLoggingEvent.getFormattedMessage());
        }
    }
}
