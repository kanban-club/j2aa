package club.kanban.j2aa.j2aaconverter.fileadapters;

import lombok.Getter;

public enum FileFormat {
    CSV(new CsvAdapter(), "csv", "CSV файлы"),
    JSON(new JsonAdapter(), "json", "JSON файлы");
    @Getter
    final FileAdapter adapter;
    @Getter
    final String defaultExtension;
    @Getter
    final String description;

    FileFormat(FileAdapter adapter, String defaultExtension, String description) {
        this.adapter = adapter;
        this.defaultExtension = defaultExtension;
        this.description = description;
    }
}
