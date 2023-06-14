package club.kanban.j2aa.j2aaconverter.fileadapters;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class FileAdapterFactory {
    private final List<Exportable> adapters;
    @Getter
    private final CsvAdapter defaultAdapter;

    @Autowired
    private FileAdapterFactory(List<Exportable> adapters, CsvAdapter defaultAdapter) {
        this.adapters = adapters;
        this.defaultAdapter = defaultAdapter;
    }

    public Exportable getAdapter(String fileExtension) {
        for (Exportable exportable : adapters) {
            if (exportable.getDefaultExtension().equalsIgnoreCase(fileExtension)) {
                return exportable;
            }
        }
        return defaultAdapter;
    }

    public Map<String, String> getFormats() {
        var formats = new HashMap<String, String>(adapters.size());
        for (Exportable adapter : adapters) {
            formats.put(adapter.getDefaultExtension(), adapter.getDescription());
        }
        return formats;
    }
}
