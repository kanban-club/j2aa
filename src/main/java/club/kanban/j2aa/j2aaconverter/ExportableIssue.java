package club.kanban.j2aa.j2aaconverter;

import club.kanban.j2aa.jirarestclient.*;
import lombok.Getter;
import net.rcarz.jiraclient.JiraException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Класс для аггрегации атрибутов, подлежащих экспорту, их последовательности,
 * а также полей которые нужно запросить в jira
 */

public class ExportableIssue {
    private static final Logger logger = LoggerFactory.getLogger(ExportableIssue.class);
    @Getter
    private String key;
    @Getter
    private String link;
    @Getter
    private String name;
    @Getter
    private Map<String, Object> attributes; // Object == String || Object == List<String> && Object != null
    @Getter
    private Date[] columnTransitionsLog;
    @Getter
    private Long blockedDays;
    @Getter
    private J2aaConverter converter;

    public static ExportableIssue fromIssue(J2aaConverter converter, Issue issue) throws JiraException {
        ExportableIssue exportableIssue = new ExportableIssue();
        exportableIssue.converter = converter;

        exportableIssue.key = issue.getKey();
        exportableIssue.name = "";
        exportableIssue.link = "";
        try {
            URL restApiUrl = new URL(issue.getSelfURL());
            exportableIssue.link = restApiUrl.getProtocol() + "://" + restApiUrl.getHost()
                    + (restApiUrl.getPort() == -1 ? "" : restApiUrl.getPort()) + "/browse/" + issue.getKey();
        } catch (MalformedURLException ignored) {
        }

        //Считываем запрашиваемые поля для Issue
        exportableIssue.attributes = new LinkedHashMap<>();
        for(String field : converter.getHttpFields()) {
            switch (field) {
                case "projectkey":
                    exportableIssue.attributes.put("Project Key", issue.getKey() != null ? issue.getKey().substring(0, issue.getKey().indexOf("-")) : "");
                    break;
                case "summary":
                    exportableIssue.name = (issue.getSummary() != null) ? issue.getSummary() : "";
                    break;
                case "project":
                    exportableIssue.attributes.put("Project", issue.getProject() != null ? issue.getProject().getName() : "");
                    break;
                case "issuetype":
                    exportableIssue.attributes.put("Issue Type", issue.getIssueType() != null ? issue.getIssueType().getName() : "");
                    break;
                case "assignee":
                    exportableIssue.attributes.put("Assignee", issue.getAssignee() != null ? issue.getAssignee().getAttribute("displayName") : "");
                    break;
                case "reporter":
                    exportableIssue.attributes.put("Reporter", issue.getReporter() != null ? issue.getReporter().getAttribute("displayName") : "");
                    break;
                case "priority":
                    exportableIssue.attributes.put("Priority", issue.getPriority() != null ? issue.getPriority().getName() : "");
                    break;
                case "labels":
                    exportableIssue.attributes.put("Labels", issue.getLabels() != null ? issue.getLabels() : new ArrayList<>(0));
                    break;
                case "components":
                    exportableIssue.attributes.put("Components", issue.getComponents() != null ? issue.getComponents()
                            .stream()
                            .map(JiraResource::getName)
                            .collect(Collectors.toList()) : new ArrayList<>(0));
                    break;
                case "fixVersions":
                    exportableIssue.attributes.put("Fix Versions", issue.getFixVersions() != null ? issue.getFixVersions()
                            .stream()
                            .map(JiraResource::getName)
                            .collect(Collectors.toList()) : new ArrayList<>(0));
                    break;
                case "epic":
                    exportableIssue.attributes.put("Epic Key", issue.getEpic() != null ? issue.getEpic().getKey() : "");
                    exportableIssue.attributes.put("Epic Name", issue.getEpic() != null ? issue.getEpic().getName() : "");
                    break;
            }
        }

        exportableIssue.initTransitionsLog(issue, converter.getBoardConfig(), converter.getUseMaxColumn());
        exportableIssue.initBlockedDays(issue);
        return exportableIssue;
    }

    private static long getDaysBetween(Date start, Date end) {
        LocalDate localStart = start.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate localEnd = end.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        return ChronoUnit.DAYS.between(localStart, localEnd);
    }

    private void initTransitionsLog(Issue issue, BoardConfig boardConfig, boolean useMaxColumn) throws JiraException {
        // 1.Отсортировать переходы статусов по времени
        List<ChangeLogItem> statusChanges = issue.getStatusChanges();
        statusChanges.sort(Comparator.comparing(ChangeLogItem::getDate));

        // 2.Создать цепочку статусов по истории переходов
        List<Status> statuses = new ArrayList<>(20);

        //Добавляем начальный статус вручную т.к. в Jira его нет
        if (statusChanges.size() > 0)
            statuses.add(new Status(issue.getCreated(), statusChanges.get(0).getFrom(), statusChanges.get(0).getFromString()));
        else
            statuses.add(new Status(issue.getCreated(), issue.getStatus().getId(), issue.getStatus().getName()));

        // Достраиваем цепочку статусов
        for (ChangeLogItem changeLogItem : statusChanges) {
            if (statuses.size() > 0) {
                Status prevStatus = statuses.get(statuses.size() - 1);
                if (prevStatus.getStatusId() == changeLogItem.getFrom())
                    prevStatus.setDateOut(changeLogItem.getDate());
                else
                    throw new JiraException("Inconsistent statuses");
            }

            Status newStatus = new Status(changeLogItem.getDate(), changeLogItem.getTo(), changeLogItem.getToString());
            statuses.add(newStatus);
        }

        // 3. Подсчитать время, проведенное в колонках
        Long[] columnLTs = new Long[boardConfig.getBoardColumns().size()];
        Map<Long, Long> status2Column = new HashMap<>(20);

        // Инициализирум рабочие массивы и делаем маппинг статусов в таблицы
        for (BoardColumn boardColumn : boardConfig.getBoardColumns()) {
            for (BoardColumnStatus boardColumnStatus : boardColumn.getStatuses())
                status2Column.put(boardColumnStatus.getId(), boardColumn.getId());
            columnLTs[(int) boardColumn.getId()] = null;
        }

        //считаем время цикла в каждом столбце
        long maxColumnId = 0;
        for (Status status : statuses) {
            Long columnId = status2Column.get(status.getStatusId());
            if (columnId != null) {
                if (columnLTs[columnId.intValue()] == null)
                    columnLTs[columnId.intValue()] = status.getCycleTimeInMillis();
                else
                    columnLTs[columnId.intValue()] += status.getCycleTimeInMillis();

                if (columnId > maxColumnId)
                    maxColumnId = columnId;
            } else {
                logger.info(String.format("%s: статус '%s' не привязан ни к одному из столбцов на доске", issue.getKey(), status.getName()));
            }
        }

        // 4. Рассчитать новое время прохождения столбцов задачей по доске
        columnTransitionsLog = new Date[columnLTs.length];
        Arrays.fill(columnTransitionsLog, null);

        // Определяем первый столбец доски в который попала задача на доске
        Date firstDate = null;
        Long firstColumnId = null;
        for (Status status : statuses) {

            Long columnId = status2Column.get(status.getStatusId());
            if (columnId != null) {
                if (firstDate == null) {
                    // Инициалихируем первое значение firstColumnId & firstDate
                    if (columnId <= (useMaxColumn ? maxColumnId : status2Column.get(issue.getStatus().getId()))) {
                        firstDate = status.getDateIn();
                        firstColumnId = columnId;
                    }
                } else {
                    if (firstDate.after(status.getDateIn())) {
                        // Если нашли перезод раньше нынешнего, то считаем его первым в цепочке
                        firstDate = status.getDateIn();
                        firstColumnId = columnId;
                    }
                }
            }
        }

        // если карточка присутствует на доске, то рассчитываем новые даты переходов между столбцами,
        // начиная с первого найденного ранее
        if (firstColumnId != null) {

            // по первому столбцу дата совпадает с датой перехода в нее
            long columnId = firstColumnId;
            columnTransitionsLog[(int) columnId] = firstDate;
            Date prevDate = columnTransitionsLog[(int) columnId];
            Long leadTimeMillis = columnLTs[(int) columnId];
            columnId++;

            //далее рассчитываем новые даты, по всем остальным столбцам доски
            while (columnId < columnLTs.length && columnId <= (useMaxColumn ? maxColumnId : status2Column.get(issue.getStatus().getId()))) {
                if (columnLTs[(int) columnId] != null) {
                    columnTransitionsLog[(int) columnId] = new Date(prevDate.getTime() + leadTimeMillis);
                    prevDate = columnTransitionsLog[(int) columnId];
                    leadTimeMillis = columnLTs[(int) columnId];
                }
                columnId++;
            }

            // если колонка Backlog не сопоставлена ни с какими статусами, то сопоставляем ее с моментом создания задачи
            if (columnTransitionsLog[0] == null)
                columnTransitionsLog[0] = issue.getCreated();
        }
    }

    private void initBlockedDays(Issue issue) {
        List<ChangeLogItem> flaggedChanges = issue.getFlaggedChanges();
        flaggedChanges.sort(Comparator.comparing(ChangeLogItem::getDate));

        blockedDays = (long) 0;

        List<ChangeLogItem> statusChanges = issue.getStatusChanges();

        if (statusChanges != null && statusChanges.size() > 0) {
            Date startWFDate = statusChanges.get(0).getDate();
            Date endWFDate = statusChanges.get(statusChanges.size() - 1).getDate();

            Date startOfBlockedTimePeriod = null;

            for (ChangeLogItem fc : flaggedChanges) {
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

            if (startOfBlockedTimePeriod != null)
                blockedDays += getDaysBetween(startOfBlockedTimePeriod, endWFDate);
        }
    }
}