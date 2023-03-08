package club.kanban.j2aa;

import org.springframework.context.annotation.*;

@ComponentScans({
        @ComponentScan("club.kanban.j2aa"),
        @ComponentScan("club.kanban.j2aa.j2aaconverter")
})

@PropertySources({
        @PropertySource("classpath:default-profile.xml"),
        @PropertySource(value = "file:${user.home}/" + J2aaApp.CONFIG_FILE_NAME, ignoreResourceNotFound = true)
})
@Configuration
public class J2aaConfiguration {
}