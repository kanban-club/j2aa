package club.kanban.j2aa.j2aaconverter.fileadapters;

public class FileAdapterFactory {
    public static FileAdapter getAdapter(String fileExtension) {
        FileAdapter adapter;

        for (FileFormat fileFormat: FileFormat.values()) {
            if (fileFormat.getDefaultExtension().equalsIgnoreCase(fileExtension)) {
                adapter = fileFormat.getAdapter();
                return adapter;
            }
        }

        return FileFormat.CSV.getAdapter(); // адаптер по-умолчанию
    }
}
