package io.github.gulliby.recmill.records;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class RecordsFirstTest {
    private static final List<Records.Line> LINES = List.of(
            new Records.Line(1, "a"), new Records.Line(3, "b"), new Records.Line(4, "c"));

    @Test
    void keepsTheFirstLinesWithTheirNumbers() {
        assertEquals(
                List.of(new Records.Line(1, "a"), new Records.Line(3, "b")),
                Records.first(LINES, 2));
    }

    @Test
    void aLimitAtOrAboveTheSizeKeepsEverything() {
        assertEquals(LINES, Records.first(LINES, 3));
        assertEquals(LINES, Records.first(LINES, Integer.MAX_VALUE));
    }

    @Test
    void theResultCannotBeChanged() {
        List<Records.Line> first = Records.first(LINES, 1);

        assertThrows(UnsupportedOperationException.class, () -> first.add(new Records.Line(9, "z")));
    }
}
