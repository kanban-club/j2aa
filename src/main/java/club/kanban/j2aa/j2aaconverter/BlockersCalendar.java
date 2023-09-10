package club.kanban.j2aa.j2aaconverter;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Класс для представления календаря блокеров - число заблочированных задач на каждый день в заданном диапазоне
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class BlockersCalendar {
    private LocalDate startDate;
    private LocalDate endDate;
    private int[] values;

    /**
     * Создает экземпляр календаря блокеров
     *
     * @param startDate стартовая дата календаря
     * @param endDate   конечная дата календаря
     * @return календарь блокеров, инициализированный нулевыми значениями
     * @throws ArrayIndexOutOfBoundsException если startDate > endDate
     */
    public static BlockersCalendar newInstance(LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            throw new ArrayIndexOutOfBoundsException();
        }
        BlockersCalendar blockersCalendar = new BlockersCalendar();
        blockersCalendar.startDate = startDate;
        blockersCalendar.endDate = endDate;
        int daysBetween = (int) Duration.between(startDate.atStartOfDay(), endDate.atStartOfDay()).toDays() + 1;
        blockersCalendar.values = new int[daysBetween];
        Arrays.fill(blockersCalendar.values, 0);
        return blockersCalendar;
    }

    /**
     * Возвращает актуальное значение на дату
     *
     * @param date дата, на которую возвражается значение числа блокеров
     * @return число блокеров
     * @throws ArrayIndexOutOfBoundsException в случае когда date вне диапазона дат календаря
     */
    public int getValue(LocalDate date) {
        int index = (int) Duration.between(startDate.atStartOfDay(), date.atStartOfDay()).toDays();
        return values[index];
    }

    /**
     * Добавляет +1 к значениям в указанном диапазоне. Если startRange > endRange, то ничего оне делает
     *
     * @param startRange начало диапазона
     * @param endRange   окончание диапазона
     * @throws ArrayIndexOutOfBoundsException в случае если startRange или rangeRange выходят за пределы календаря
     */
    public void incrementRange(LocalDate startRange, LocalDate endRange) {
        int startIndex = (int) Duration.between(startDate.atStartOfDay(), startRange.atStartOfDay()).toDays();
        int count = (int) Duration.between(startRange.atStartOfDay(), endRange.atStartOfDay()).toDays() + 1;
        for (int i = startIndex; i < startIndex + count; i++) {
            values[i]++;
        }
    }

    /**
     * Импортирует в календарь данные о блокировках от задачи
     *
     * @param blockerChanges список блокировок по задаче, которую нужно обработать
     */
    public void importBlockerChanges(List<ChangeLogItem> blockerChanges) {
        blockerChanges.sort(Comparator.comparing(ChangeLogItem::getDate));

        // Достраиваем цепочку блокировок
        LocalDate startBlockDate = null;
        for (ChangeLogItem changeLogItem : blockerChanges) {
            LocalDate changeDate = LocalDate.from(changeLogItem.getDate().toInstant()
                    .atZone(ZoneId.systemDefault()).toLocalDate());
            if (startBlockDate == null) {
                startBlockDate = changeDate;
            } else {
                try {
                    incrementRange(
                            startBlockDate.isAfter(startDate) ? startBlockDate : startDate,
                            changeDate.isBefore(endDate) ? changeDate : endDate);
                } catch (ArrayIndexOutOfBoundsException ignored) {
                }
                startBlockDate = null;
            }
        }
        //Если последний блокер не закрыт, то добавляем период блокировки до конца календаря
        if (startBlockDate != null) {
            try {
                incrementRange(
                        startBlockDate.isAfter(startDate) ? startBlockDate : startDate,
                        endDate);
            } catch (ArrayIndexOutOfBoundsException ignored) {
            }
        }
    }
}