package club.kanban.j2aaconverter;

public interface ProgressMonitor {
    void update(int current, int max);
}
