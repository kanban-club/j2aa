package club.kanban.j2aa.jiraclient.dto.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

import java.util.NoSuchElementException;

@Getter
@ToString
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuthResponse {
    private static final String JSESSIONID_COOKIE = "JSESSIONID";
    Session session;

    public String getSessionId() {
        if (session.getName() != null && session.getName().equals(JSESSIONID_COOKIE)) {
            return session.getValue();
        } else {
            throw new NoSuchElementException(JSESSIONID_COOKIE);
        }
    }
}