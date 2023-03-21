package club.kanban.j2aa;

import club.kanban.j2aa.j2aaconverter.fileadapters.FileAdapter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.*;

import java.util.List;

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