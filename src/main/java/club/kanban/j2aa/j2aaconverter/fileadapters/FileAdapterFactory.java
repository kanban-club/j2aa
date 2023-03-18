package club.kanban.j2aa.j2aaconverter.fileadapters;

public class FileAdapterFactory {
    public static FileAdapter getAdapter(String fileExtension) {
        FileAdapter adapter = null;

        for (FileFormat fileFormat: FileFormat.values()) {
            if (fileFormat.getDefaultExtension().equalsIgnoreCase(fileExtension)) {
                adapter = fileFormat.getAdapter();
                break;
            }
        }

        if (adapter == null)
            adapter = FileFormat.CSV.getAdapter(); // адаптер по-умолчанию

        return adapter;
    }
}
