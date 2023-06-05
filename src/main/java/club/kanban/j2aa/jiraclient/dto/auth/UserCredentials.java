package club.kanban.j2aa.jiraclient.dto.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import lombok.experimental.FieldDefaults;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@ToString
public class UserCredentials {
    private String username;
    private String password;
}
