package club.kanban.j2aa.j2aaconverter.fileadapters;

import lombok.Getter;

public enum FileFormat {
    CSV(new CsvAdapter()),
    JSON(new JsonAdapter());
    @Getter
    final FileAdapter adapter;
    FileFormat(FileAdapter adapter) {
        this.adapter = adapter;
    }
}
