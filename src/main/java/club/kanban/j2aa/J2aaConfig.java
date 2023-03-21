package club.kanban.j2aa;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.*;

@ComponentScans({
        @ComponentScan("club.kanban.j2aa")
})

@PropertySources({
        @PropertySource("classpath:default-profile.xml"),
        @PropertySource(value = "file:${user.home}/" + J2aaApp.CONFIG_FILE_NAME, ignoreResourceNotFound = true)
})
@Configuration
public class J2aaConfig implements ApplicationContextAware {
    @Getter
    private static ApplicationContext context;
    @Override
    public void setApplicationContext(@NotNull ApplicationContext applicationContext) throws BeansException {
        context = applicationContext;
    }
}