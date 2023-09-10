package club.kanban.j2aa.j2aaconverter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BlockersCalendarTest {

    private static final LocalDate START_DATE = LocalDate.of(2023, 1, 1);
    private static final LocalDate END_DATE = LocalDate.of(2023, 1, 10);
    private BlockersCalendar blockersCalendar;
    
    @BeforeEach
    void setUp() {
        blockersCalendar = BlockersCalendar.newInstance(START_DATE, END_DATE);
    }

    @Test
    void newInstance() {
        assertDoesNotThrow(() -> BlockersCalendar.newInstance(START_DATE, END_DATE));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> BlockersCalendar.newInstance(END_DATE, START_DATE));
    }

    @Test
    void incrementRange() {
        assertDoesNotThrow(() -> {
            blockersCalendar.incrementRange(START_DATE.plusDays(2), START_DATE.plusDays(4));
            blockersCalendar.incrementRange(START_DATE.plusDays(3), START_DATE.plusDays(4));
            blockersCalendar.incrementRange(START_DATE.plusDays(4), START_DATE.plusDays(4));
            assert blockersCalendar.getValue(START_DATE) == 0;
            assert blockersCalendar.getValue(START_DATE.plusDays(1)) == 0;
            assert blockersCalendar.getValue(START_DATE.plusDays(2)) == 1;
            assert blockersCalendar.getValue(START_DATE.plusDays(3)) == 2;
            assert blockersCalendar.getValue(START_DATE.plusDays(4)) == 3;
            for (LocalDate localDate = START_DATE.plusDays(5);
                 localDate.isBefore(END_DATE) || localDate.isEqual(END_DATE); localDate = localDate.plusDays(1)) {
                assert blockersCalendar.getValue(localDate) == 0;
            }
        });

//        assertThrows(ArrayIndexOutOfBoundsException.class, () -> blockersCalendar.incrementRange(END_DATE, START_DATE));

        assertThrows(ArrayIndexOutOfBoundsException.class, () ->
                blockersCalendar.incrementRange(START_DATE.minusDays(1), END_DATE));

        assertThrows(ArrayIndexOutOfBoundsException.class, () ->
                blockersCalendar.incrementRange(START_DATE, END_DATE.plusDays(1)));
    }

    @Test
    void getValue() {
        assertDoesNotThrow(() -> blockersCalendar.getValue(START_DATE));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> blockersCalendar.getValue(START_DATE.minusDays(1)));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> blockersCalendar.getValue(END_DATE.plusDays(1)));
    }

    @Test
    void importBlockerChanges() {
        List<ChangeLogItem> blockerChangesSet1 = new ArrayList<>(Arrays.asList(
                ChangeLogItem.of(Date.from(START_DATE.plusDays(5)
                        .atStartOfDay().atZone(ZoneId.systemDefault()).toInstant()), true),
                ChangeLogItem.of(Date.from(END_DATE.minusDays(0)
                        .atStartOfDay().atZone(ZoneId.systemDefault()).toInstant()), false)
        ));
        List<ChangeLogItem> blockerChangesSet2 = new ArrayList<>(List.of(
                ChangeLogItem.of(Date.from(END_DATE.minusDays(2)
                        .atStartOfDay().atZone(ZoneId.systemDefault()).toInstant()), true)
        ));
        assertDoesNotThrow(() -> blockersCalendar.importBlockerChanges(blockerChangesSet1));
        assertDoesNotThrow(() -> blockersCalendar.importBlockerChanges(blockerChangesSet2));

        for (int i = 0; i < 5; i++) {
            assertEquals(0, blockersCalendar.getValue(START_DATE.plusDays(i)));
        }
        for (int i = 5; i < 7; i++) {
            assertEquals(1, blockersCalendar.getValue(START_DATE.plusDays(i)));
        }
        for (int i = 7; i < 10; i++) {
            assertEquals(2, blockersCalendar.getValue(START_DATE.plusDays(i)));
        }
    }
}