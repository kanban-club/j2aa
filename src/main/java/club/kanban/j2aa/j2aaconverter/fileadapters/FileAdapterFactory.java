package club.kanban.j2aa.j2aaconverter.fileadapters;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class FileAdapterFactory {
    private final List<FileAdapter> adapters;
    @Getter private final CsvAdapter defaultAdapter;

    @Autowired
    private FileAdapterFactory(List<FileAdapter> adapters, CsvAdapter defaultAdapter) {
        this.adapters = adapters;
        this.defaultAdapter = defaultAdapter;
    }

    public FileAdapter getAdapter(String fileExtension) {
        for (FileAdapter fileAdapter : adapters) {
            if (fileAdapter.getDefaultExtension().equalsIgnoreCase(fileExtension)) {
                return fileAdapter;
            }
        }
        return defaultAdapter;
    }

    public Map<String, String> getFormats() {
        var formats = new HashMap<String, String>(adapters.size());
        for (FileAdapter adapter : adapters) {
            formats.put(adapter.getDefaultExtension(), adapter.getDescription());
        }
        return formats;
    }
}
