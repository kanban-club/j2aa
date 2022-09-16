package club.kanban.j2aaconverter;

import club.kanban.jirarestclient.*;
import lombok.Getter;
import net.rcarz.jiraclient.JiraException;
import net.sf.json.JSONArray;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class BoardIssue {
    @Getter
    private String key;
    @Getter
    private String link;
    @Getter
    private String name;
    @Getter
    private String projectKey;
    @Getter
    private String issueTypeName;
    @Getter
    private String labels;
    @Getter
    private Date[] columnTransitionsLog;
    @Getter
    private Long blockedDays;
    @Getter
    private String priority;

    public static BoardIssue createFromIssue(_Issue issue, _BoardConfig boardConfig) throws JiraException {
        BoardIssue boardIssue = new BoardIssue();
        boardIssue.key = issue.getKey();

        boardIssue.link = "";
        try {
            URL restApiUrl = new URL(issue.getSelfURL());
            boardIssue.link = restApiUrl.getProtocol() + "://" + restApiUrl.getHost() + (restApiUrl.getPort() == -1 ? "" : restApiUrl.getPort()) + "/browse/" + issue.getKey();
        } catch (MalformedURLException e) {
        }

        boardIssue.name = issue.getName();
        boardIssue.projectKey = issue.getKey().substring(0, issue.getKey().indexOf("-")); // project key: issue.getProject.getKey()
        boardIssue.issueTypeName = issue.getIssueType().getName();
        boardIssue.labels = (((JSONArray) issue.getAttribute("labels")).size() > 0 ? "[" + ((JSONArray) issue.getAttribute("labels")).join("|", true) + "]" : "");
        boardIssue.priority = issue.getPriority().getName();

        boardIssue.initTransitionsLog(issue, boardConfig);
        boardIssue.initBlockedDays(issue);
        return boardIssue;
    }

    private static long getDaysBetween(Date start, Date end){
        LocalDate localStart = start.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate localEnd = end.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        return ChronoUnit.DAYS.between(localStart, localEnd);
    }

    private void initTransitionsLog(_Issue issue, _BoardConfig boardConfig) throws JiraException {
        // 1.Отсортировать переходы статусов по времени
        List<_Change> statusChanges = issue.getStatusChanges();
        statusChanges.sort(Comparator.comparing(_Change::getDate));

        // 2.Создать цепочку статусов по истории переходов
        List<Status> statuses = new ArrayList<>(20);

        //Добавляем начальный статус вручную т.к. в Jira его нет
        if (statusChanges.size() > 0)
            statuses.add(new Status(issue.getCreated(), statusChanges.get(0).getFrom(), statusChanges.get(0).getFromString()));
        else statuses.add(new Status(issue.getCreated(), issue.getStatus().getId(), issue.getStatus().getName()));

        // Достраиваем цепочку статусов
        for (_Change change : statusChanges) {
            if (statuses.size() > 0) {
                Status prevStatus = statuses.get(statuses.size() - 1);
                if (prevStatus.getStatusId() == change.getFrom())
                    prevStatus.setDateOut(change.getDate());
                else
                    throw new JiraException("Inconsistent statuses");
            }

            Status newStatus = new Status(change.getDate(), change.getTo(), change.getToString());
            statuses.add(newStatus);
        }

        // 3. Подсчитать время, проведенное в колонках
        Long[] columnLTs = new Long[boardConfig.getBoardColumns().size()];
        Map<Long, Long> status2Column = new HashMap<>(20);

        // Инициализирум рабочие массивы и делаем маппинг статусов в таблицы
        for (_BoardColumn boardColumn : boardConfig.getBoardColumns()) {
            for (_BoardColumnStatus boardColumnStatus : boardColumn.getStatuses())
                status2Column.put(boardColumnStatus.getId(), boardColumn.getId());
            columnLTs[(int) boardColumn.getId()] = null;
        }

        //считаем время цикла в каждом столбце
        for (Status status : statuses) {
            Long columnId = status2Column.get(status.getStatusId());
            if (columnId != null) {
                if (columnLTs[columnId.intValue()] == null)
                    columnLTs[columnId.intValue()] = status.getCycleTimeInMillis();
                else columnLTs[columnId.intValue()] += status.getCycleTimeInMillis();
            } else {
                // DEBUG
                System.out.println(issue.getKey() + " Found status not associated with any column: " + status.getName());
            }
        }

        // 4. Рассчитать новое время прохождения столбцов задачей по доске
        Long currentIssueColumn = status2Column.get(issue.getStatus().getId());
        columnTransitionsLog = new Date[columnLTs.length];
        Arrays.fill(columnTransitionsLog, null);

        // Определяем первый столбец доски в который попала задача на доске
        Date firstDate = null;
        Long firstColumnId = null;
        for (Status status : statuses) {
            Long columnId = status2Column.get(status.getStatusId());
            if (columnId != null) {
                firstDate = status.getDateIn();
                firstColumnId = columnId;
                break;
            }
        }

        // если карточка присутствует на доске, то рассчитываем новые даты переходов между столбцами,
        // начиная с первого найденного ранее
        if (firstColumnId != null) {

            // по первому столбцу дата совпадает с датой перехода в нее
            Long columnId = firstColumnId;
            columnTransitionsLog[columnId.intValue()] = firstDate;
            Date prevDate = columnTransitionsLog[columnId.intValue()];
            Long leadTimeMillis = columnLTs[columnId.intValue()];
            columnId++;

            //далее рассчитываем новые даты, по всем остальным столбцам доски, но не далее нынешнего
            while (columnId < columnLTs.length && columnId <= currentIssueColumn) {
                if (columnLTs[columnId.intValue()] != null) {
                    columnTransitionsLog[columnId.intValue()] = new Date(prevDate.getTime() + leadTimeMillis);
                    prevDate = columnTransitionsLog[columnId.intValue()];
                    leadTimeMillis = columnLTs[columnId.intValue()];
                }
                columnId++;
            }

            // если колонка Backlog не сопоставлена ни с какими статусами, то сопоставляем ее с моментом создания задачи
            if (columnTransitionsLog[0] == null) columnTransitionsLog[0] = issue.getCreated();
        }
    }

    private void initBlockedDays(_Issue issue) {
        List<_Change> flaggedChanges = issue.getFlaggedChanges();
        flaggedChanges.sort(Comparator.comparing(_Change::getDate));

        blockedDays = (long) 0;

        List<_Change> statusChanges = issue.getStatusChanges();

        if (statusChanges != null && statusChanges.size() > 0) {
            Date startWFDate = statusChanges.get(0).getDate();
            Date endWFDate = statusChanges.get(statusChanges.size() - 1).getDate();

            Date startOfBlockedTimePeriod = null;

            for (_Change fc : flaggedChanges) {
                if (fc.getDate().compareTo(startWFDate) >= 0 && fc.getDate().compareTo(endWFDate) <= 0) {
                    if (fc.getToString().equals("Impediment" /*or getTo() = [10000] ? */)) {
                        if (startOfBlockedTimePeriod == null) startOfBlockedTimePeriod = fc.getDate();
                    } else if (fc.getFromString().equals("Impediment" /*or getTo() = [10000] ? */)) {
                        if (startOfBlockedTimePeriod == null) startOfBlockedTimePeriod = startWFDate;
                        blockedDays += getDaysBetween(startOfBlockedTimePeriod, fc.getDate());
                        startOfBlockedTimePeriod = null;
                    }
                }
            }

            if (startOfBlockedTimePeriod != null) blockedDays += getDaysBetween(startOfBlockedTimePeriod, endWFDate);
        }
    }
}
