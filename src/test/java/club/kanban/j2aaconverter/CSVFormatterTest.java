package club.kanban.j2aaconverter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CSVFormatterTest {

    @Test
    void formatString() {
        String actual = CSVFormatter.formatString("\",");
        assertEquals("\"\\\"\\,\"", actual);
    }

    @BeforeEach
    void setUp() {
    }

    @Test
    void formatList() {
        List<String> testList = Arrays.asList("first\",", "second");
        String actual = CSVFormatter.formatList(testList);
        assertEquals("[\"first\\\"\\,\"|\"second\"]", actual);

        actual = CSVFormatter.formatList(null);
        assertEquals("", actual);
    }


}