package club.kanban.j2aa.j2aaconverter.fileadapters;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class FileAdapterFactory {
    @Autowired
    private List<FileAdapter> adapters;
    @Autowired @Getter
    private CsvAdapter defaultAdapter;

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

    public List<FileAdapter> getAdapters() {
        return adapters;
    }

    public Map<String, String> getFormats() {
        var formats = new HashMap<String, String>(getAdapters().size());
        for(FileAdapter adapter : getAdapters()) {
            formats.put(adapter.getDefaultExtension(), adapter.getDescription());
        }
        return formats;
    }
}
