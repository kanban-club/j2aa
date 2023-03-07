package club.kanban.j2aa;

import org.springframework.context.annotation.*;

@Configuration
@ComponentScans({
        @ComponentScan("club.kanban.j2aa"),
        @ComponentScan("club.kanban.j2aa.j2aaconverter")
})

@PropertySources({
        @PropertySource("classpath:default-profile.xml"),
        @PropertySource(value = "file:${user.home}/" + J2aaApp.CONFIG_FILE_NAME, ignoreResourceNotFound = true)
})

public class J2aaConfig {
}
