package club.kanban.jirarestclient;

public interface ProgressMonitor {
    void update(int current, int max);
}
