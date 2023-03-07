package club.kanban.j2aa;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;

@Configuration
@ComponentScan("club.kanban")
@PropertySources({
        @PropertySource("classpath:default-profile.xml"),
        @PropertySource(value = "file:${user.home}/" + J2aaApp.CONFIG_FILE_NAME, ignoreResourceNotFound = true)
})

public class J2aaConfig {
}
