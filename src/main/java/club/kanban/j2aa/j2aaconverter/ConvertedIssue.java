package club.kanban.j2aa.j2aaconverter;

import club.kanban.j2aa.jiraclient.JiraException;
import club.kanban.j2aa.jiraclient.dto.boardconfig.BoardConfig;
import club.kanban.j2aa.jiraclient.dto.boardconfig.columnconfig.column.Column;
import club.kanban.j2aa.jiraclient.dto.boardconfig.columnconfig.column.Status;
import club.kanban.j2aa.jiraclient.dto.issue.Issue;
import club.kanban.j2aa.jiraclient.dto.issue.changelog.history.History;
import club.kanban.j2aa.jiraclient.dto.issue.changelog.history.HistoryItem;
import club.kanban.j2aa.jiraclient.dto.issue.fields.Fields;
import club.kanban.j2aa.jiraclient.dto.issue.fields.Resource;
import lombok.Getter;
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

public class ConvertedIssue {
    private static final Logger logger = LoggerFactory.getLogger(ConvertedIssue.class);
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
    private boolean blocked;
    @Getter
    private J2aaConverter converter;
    private final List<ChangeLogItem> statusChanges = new ArrayList<>(10);
    @Getter
    private final List<ChangeLogItem> flaggedChanges = new ArrayList<>(10);

    public static ConvertedIssue newInstance(J2aaConverter converter, Issue issue) throws JiraException {
        ConvertedIssue convertedIssue = new ConvertedIssue();
        convertedIssue.converter = converter;

        convertedIssue.key = issue.getKey();
        convertedIssue.name = "";

        convertedIssue.link = "";
        try {
            URL restApiUrl = new URL(issue.getSelf());
            String pathPrefix;
            int startPos = restApiUrl.getPath().indexOf("/rest/agile");
            if (startPos > 0) {
                pathPrefix = restApiUrl.getPath().substring(0, startPos);
            } else {
                pathPrefix = "";
            }
            convertedIssue.link = new StringBuffer()
                    .append(restApiUrl.getProtocol())
                    .append("://")
                    .append(restApiUrl.getHost())
                    .append((restApiUrl.getPort() == -1 ? "" : restApiUrl.getPort()))
                    .append(pathPrefix)
                    .append("/browse/")
                    .append(issue.getKey())
                    .toString();
        } catch (MalformedURLException ignored) {
        }

        //Считываем запрашиваемые поля для Issue
        convertedIssue.attributes = new LinkedHashMap<>();
        Fields fields
                = issue.getFields();
        for (String jiraField : converter.getJiraFields()) {
            switch (jiraField) {
                case "projectkey":
                    convertedIssue.attributes.put("Project Key",
                            issue.getKey() != null ? issue.getKey().substring(0, issue.getKey().indexOf("-")) : "");
                    break;
                case "summary":
                    convertedIssue.name = (fields.getSummary() != null) ? fields.getSummary() : "";
                    break;
                case "project":
                    convertedIssue.attributes.put("Project",
                            fields.getProject() != null ? fields.getProject().getName() : "");
                    break;
                case "issuetype":
                    convertedIssue.attributes.put("Issue Type",
                            fields.getIssueType() != null ? fields.getIssueType().getName() : "");
                    break;
                case "assignee":
                    convertedIssue.attributes.put("Assignee",
                            fields.getAssignee() != null ? fields.getAssignee().getDisplayName() : "");
                    break;
                case "reporter":
                    convertedIssue.attributes.put("Reporter",
                            fields.getReporter() != null ? fields.getReporter().getDisplayName() : "");
                    break;
                case "priority":
                    convertedIssue.attributes.put("Priority",
                            fields.getPriority() != null ? fields.getPriority().getName() : "");
                    break;
                case "labels":
                    convertedIssue.attributes.put("Labels",
                            fields.getLabels() != null ? fields.getLabels() : new ArrayList<>(0));
                    break;
                case "components":
                    convertedIssue.attributes.put("Components",
                            fields.getComponents() != null ? fields.getComponents().stream()
                                    .map(Resource::getName)
                                    .collect(Collectors.toList()) : new ArrayList<>(0));
                    break;
                case "fixVersions":
                    convertedIssue.attributes.put("Fix Versions",
                            fields.getFixVersions() != null ? fields.getFixVersions().stream()
                                    .map(Resource::getName)
                                    .collect(Collectors.toList()) : new ArrayList<>(0));
                    break;
                case "epic":
                    convertedIssue.attributes.put("Epic Key",
                            fields.getEpic() != null ? fields.getEpic().getKey() : "");
                    convertedIssue.attributes.put("Epic Name",
                            fields.getEpic() != null ? fields.getEpic().getName() : "");
                    break;
            }
        }

        convertedIssue.initTransitionsLog(issue, converter.getBoardConfig(), converter.isUseMaxColumn());
        convertedIssue.initBlockedDays();

        if (convertedIssue.isBlockedInDone()) {
            logger.info("{} находится в конечном статусе доски с флагом блокировки", issue.getKey());
        }

        return convertedIssue;
    }

    private boolean isBlockedInDone() {
        return blocked && columnTransitionsLog.length > 0
                && columnTransitionsLog[columnTransitionsLog.length - 1] != null;
    }

    private static long getDaysBetween(Date start, Date end) {
        LocalDate localStart = start.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate localEnd = end.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        return ChronoUnit.DAYS.between(localStart, localEnd);
    }

    private void initTransitionsLog(Issue issue, BoardConfig boardConfig, boolean useMaxColumn) throws JiraException {
        // 1.Отсортировать переходы статусов по времени
        parseChangelog(issue);

        statusChanges.sort(Comparator.comparing(ChangeLogItem::getDate));

        // 2.Создать цепочку статусов по истории переходов
        List<IssueStatus> issueStatuses = new ArrayList<>(20);

        //Добавляем начальный статус вручную т.к. в Jira его нет
        if (statusChanges.size() > 0)
            issueStatuses.add(new IssueStatus(issue.getFields().getCreated(),
                    statusChanges.get(0).getFrom(), statusChanges.get(0).getFromString()));
        else
            issueStatuses.add(new IssueStatus(issue.getFields().getCreated(),
                    issue.getFields().getStatus().getId(), issue.getFields().getStatus().getName()));

        // Достраиваем цепочку статусов
        for (ChangeLogItem changeLogItem : statusChanges) {
            if (issueStatuses.size() > 0) {
                IssueStatus prevIssueStatus = issueStatuses.get(issueStatuses.size() - 1);
                if (prevIssueStatus.getStatusId() == changeLogItem.getFrom())
                    prevIssueStatus.setDateOut(changeLogItem.getDate());
                else {
                    throw new JiraException("Inconsistent statuses");
                }
            }

            IssueStatus newIssueStatus = new IssueStatus(
                    changeLogItem.getDate(),
                    changeLogItem.getTo(),
                    changeLogItem.getToString());

            issueStatuses.add(newIssueStatus);
        }

        // 3. Подсчитать время, проведенное в колонках
        Long[] columnLTs = new Long[boardConfig.getColumnConfig().getColumns().size()];
        Map<Long, Long> status2Column = new HashMap<>(20);

        // Инициализируем рабочие массивы и делаем маппинг статусов в таблицы
        List<Column> columns = boardConfig.getColumnConfig().getColumns();
        for (int i = 0; i < columns.size(); i++) {
            Column column = columns.get(i);
            for (Status boardColumnStatus : column.getStatuses())
                status2Column.put(boardColumnStatus.getId(), (long) i);
            columnLTs[i] = null;
        }

        //считаем время цикла в каждом столбце
        long maxColumnId = 0;
        for (IssueStatus issueStatus : issueStatuses) {
            Long columnId = status2Column.get(issueStatus.getStatusId());
            if (columnId != null) {
                if (columnLTs[columnId.intValue()] == null) {
                    columnLTs[columnId.intValue()] = issueStatus.getCycleTimeInMillis();
                } else {
                    columnLTs[columnId.intValue()] += issueStatus.getCycleTimeInMillis();
                }

                if (columnId > maxColumnId) {
                    maxColumnId = columnId;
                }
            } else {
                logger.info(String.format("%s: статус '%s' не привязан ни к одному из столбцов на доске",
                        issue.getKey(), issueStatus.getName()));
            }
        }

        // 4. Рассчитать новое время прохождения столбцов задачей по доске
        columnTransitionsLog = new Date[columnLTs.length];
        Arrays.fill(columnTransitionsLog, null);

        // Определяем первый столбец доски в который попала задача на доске
        Date firstDate = null;
        Long firstColumnId = null;
        for (IssueStatus issueStatus : issueStatuses) {
            Long columnId = status2Column.get(issueStatus.getStatusId());
            if (columnId != null) {
                if (firstDate == null) {
                    // Инициализируем первое значение firstColumnId & firstDate
                    Resource lastIssueStatus = issue.getFields().getStatus();
                    if (columnId <= (useMaxColumn ? maxColumnId : status2Column.get(lastIssueStatus.getId()))) {
                        firstDate = issueStatus.getDateIn();
                        firstColumnId = columnId;
                    }
                } else {
                    if (firstDate.after(issueStatus.getDateIn())) {
                        // Если нашли переход раньше нынешнего, то считаем его первым в цепочке
                        firstDate = issueStatus.getDateIn();
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
            Resource lastIssueStatus = issue.getFields().getStatus();
            while (columnId < columnLTs.length
                    && columnId <= (useMaxColumn ? maxColumnId : status2Column.get(lastIssueStatus.getId()))) {
                if (columnLTs[(int) columnId] != null) {
                    columnTransitionsLog[(int) columnId] = new Date(prevDate.getTime() + leadTimeMillis);
                    prevDate = columnTransitionsLog[(int) columnId];
                    leadTimeMillis = columnLTs[(int) columnId];
                }
                columnId++;
            }

            // если колонка Backlog не сопоставлена ни с какими статусами, то сопоставляем ее с моментом создания задачи
            if (columnTransitionsLog[0] == null) columnTransitionsLog[0] = issue.getFields().getCreated();
        }
    }

    private void initBlockedDays() {
        flaggedChanges.sort(Comparator.comparing(ChangeLogItem::getDate));

        blockedDays = (long) 0;

        Date startOfBlockedTimePeriod = null;

        for (ChangeLogItem fc : flaggedChanges) {

            if (fc.getToString().equals("Impediment" /*or getTo() = [10000] ? */)) {
                if (startOfBlockedTimePeriod == null) {
                    startOfBlockedTimePeriod = fc.getDate();
                }
            } else if (fc.getFromString().equals("Impediment" /*or getTo() = [10000] ? */)) {
                if (startOfBlockedTimePeriod != null) {
                    blockedDays += getDaysBetween(startOfBlockedTimePeriod, fc.getDate());
                    startOfBlockedTimePeriod = null;
                } else {
                    logger.info(String.format("%s: ошибка в данных по блокировкам. "
                            + "Снят флаг на незаблокированной задаче", getKey()));
//                        startOfBlockedTimePeriod = startWFDate;
                }
            }

            blocked = startOfBlockedTimePeriod != null;
            if (blocked) {
                blockedDays += getDaysBetween(startOfBlockedTimePeriod, new Date());
            }
        }
    }

    private void parseChangelog(Issue issue) {
        if (issue.getChangelog() != null && issue.getChangelog().getHistories() != null) {
            for (History history : issue.getChangelog().getHistories()) {
                for (HistoryItem item : history.getHistoryItems()) {
                    switch (item.getField()) {
                        case "status":
                            statusChanges.add(ChangeLogItem.of(history, item, 0));
                            break;
                        case "Flagged":
                            flaggedChanges.add(ChangeLogItem.of(history, item,
                                    ChangeLogItem.SKIP_FROM | ChangeLogItem.SKIP_TO));
                            break;
                    }
                }
            }
        }
    }
}