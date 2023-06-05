package club.kanban.j2aa.jiraclient;

public class JiraException extends RuntimeException {
    public JiraException(String message) {
        super(message);
    }

    public JiraException(Throwable e) {
        super(e);
    }

    public JiraException(String message, Throwable cause) {
        super(message, cause);
    }
}
