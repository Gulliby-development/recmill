package io.github.gulliby.recmill.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.gulliby.recmill.core.Failure;
import io.github.gulliby.recmill.syntax.Entry;
import io.github.gulliby.recmill.syntax.Picture;
import java.util.List;
import org.junit.jupiter.api.Test;

class LayoutTest {
    @Test
    void buildsThePublicNodeAndSlotContractsWithOriginalIdentities() {
        Layout layout = Layout.build("01 R. 05 A PIC X(2). 05 B REDEFINES A PIC X.");
        Node root = layout.root();
        Node a = root.children().get(0);
        Node b = root.children().get(1);

        assertEquals(new Entry(1, 1, "R", null, 0, null), root.entry());
        assertEquals("R", root.name());
        assertNull(root.parent());
        assertNull(root.base());
        assertTrue(root.group());
        assertEquals(List.of(a, b), root.children());
        assertEquals(2, root.unit());
        assertEquals(2, root.total());
        assertEquals(2, layout.length());

        assertEquals(new Entry(2, 5, "A", new Picture(false, false, 2, 0, 2), 0, null), a.entry());
        assertSame(root, a.parent());
        assertNull(a.base());
        assertEquals(List.of(b), a.redefiners());
        assertFalse(a.group());
        assertEquals("A", a.name());
        assertEquals(2, a.unit());
        assertEquals(2, a.total());

        assertEquals(new Entry(3, 5, "B", new Picture(false, false, 1, 0, 1), 0, "A"), b.entry());
        assertSame(root, b.parent());
        assertSame(a, b.base());
        assertEquals(List.of(b), a.redefiners());
        assertTrue(b.redefiners().isEmpty());
        assertFalse(b.group());
        assertEquals("B", b.name());
        assertEquals(1, b.unit());
        assertEquals(1, b.total());

        assertEquals(2, layout.slots().size());
        Slot aSlot = layout.slots().get(0);
        Slot bSlot = layout.slots().get(1);
        assertEquals(new Slot("A", 1, 2, new Picture(false, false, 2, 0, 2), false, List.of(a)), aSlot);
        assertEquals(new Slot("B", 1, 1, new Picture(false, false, 1, 0, 1), false, List.of(b)), bSlot);
        assertFalse(aSlot.group());
        assertFalse(bSlot.group());
        assertEquals(2, aSlot.end());
        assertEquals(1, bSlot.end());
        assertSame(a, aSlot.chain().get(0));
        assertSame(b, bSlot.chain().get(0));
    }

    @Test
    void validatesHierarchyInEntryOrder() {
        assertFailure("05 R. 05 A PIC X.", "entry 1: the layout must start with level 01");
        assertFailure("01 R. 05 A PIC X. 01 S.", "entry 3: only one level 01 entry is allowed");
        assertFailure("01 R. 05 A. 10 B PIC X. 07 C PIC X.",
                "entry 4: level 07 does not match an enclosing level");
    }

    @Test
    void validatesStructureInEntryOrder() {
        assertFailure("01 R PIC X.", "entry 1: level 01 must be a group");
        assertFailure("01 R PIC X. 05 A PIC X.", "entry 1: group \"R\" cannot have PIC");
        assertFailure("01 R. 05 A PIC X. 10 B PIC X.", "entry 2: group \"A\" cannot have PIC");
        assertFailure("01 R. 05 A.", "entry 2: \"A\" needs PIC");
        assertFailure("01 R. 05 A PIC X. 05 A PIC X.", "entry 3: duplicate name \"A\"");
        assertFailure("01 R REDEFINES Z. 05 A PIC X.", "entry 1: invalid REDEFINES target \"Z\"");
        assertFailure("01 R. 05 A PIC X. 05 B REDEFINES C PIC X.",
                "entry 3: invalid REDEFINES target \"C\"");
        assertFailure("01 R. 05 A PIC X. 05 B PIC X. 05 C REDEFINES A PIC X.",
                "entry 4: invalid REDEFINES target \"A\"");
        assertFailure("01 R. 05 A PIC X. 05 B REDEFINES A PIC X. 05 C REDEFINES B PIC X.",
                "entry 4: invalid REDEFINES target \"B\"");
        assertFailure("01 R. 05 A PIC X OCCURS 1. 05 B REDEFINES A PIC X OCCURS 2.",
                "entry 3: REDEFINES target \"A\" has OCCURS");
        assertFailure("01 R. 05 A PIC X. 05 B REDEFINES A PIC X OCCURS 1.",
                "entry 3: REDEFINES cannot have OCCURS");
    }

    @Test
    void preservesValidationPassPrecedenceAcrossAllPasses() {
        assertFailure("05 R. 05 A PIC X(0).", "entry 2: invalid PIC \"X(0)\"");
        assertFailure("01 R PIC X. 05 A. 10 B PIC X. 07 C PIC X.",
                "entry 4: level 07 does not match an enclosing level");
        assertFailure("01 R. 05 A PIC X. 05 B REDEFINES A PIC X(2). 05 C.",
                "entry 4: \"C\" needs PIC");
        assertFailure("01 R. 05 A PIC X. 05 A.",
                "entry 3: \"A\" needs PIC");
        assertFailure("01 R. 05 A PIC X. 05 B REDEFINES A PIC X(2). 05 C PIC X(255) OCCURS 99.",
                "entry 3: \"B\" (2 characters) is longer than \"A\" (1 characters)");
        assertFailure("01 R. 05 A OCCURS 99. 10 B OCCURS 99. 15 C PIC X.",
                "layout is 9801 characters (above 4096)");
    }

    @Test
    void emitsFillersScopedNamesOverlayRunsAndRootOccurrences() {
        Layout fillers = Layout.build("01 R. 05 FILLER PIC X. 05 FILLER PIC X.");
        assertEquals(2, fillers.length());
        assertEquals(List.of("FILLER", "FILLER"), fillers.slots().stream().map(Slot::path).toList());
        assertEquals(List.of(1, 2), fillers.slots().stream().map(Slot::start).toList());

        Layout scoped = Layout.build("01 R. 05 A. 10 X PIC X. 05 B. 10 X PIC X.");
        assertEquals(List.of("A", "A.X", "B", "B.X"),
                scoped.slots().stream().map(Slot::path).toList());
        assertEquals(2, scoped.length());

        Layout overlays = Layout.build(
                "01 R. 05 A PIC X(2). 05 P REDEFINES A PIC X. 05 Q REDEFINES A PIC X(2). 05 Z PIC X.");
        assertEquals(List.of("A@1", "P@1", "Q@1", "Z@3"),
                overlays.slots().stream().map(slot -> slot.path() + "@" + slot.start()).toList());
        assertEquals(3, overlays.length());

        Layout rootOccurs = Layout.build("01 R OCCURS 2. 05 A PIC X OCCURS 1.");
        assertEquals(1, rootOccurs.root().unit());
        assertEquals(2, rootOccurs.root().total());
        assertEquals(1, rootOccurs.length());
        assertEquals(List.of("A(1)"), rootOccurs.slots().stream().map(Slot::path).toList());
        assertEquals(1, rootOccurs.slots().get(0).start());
        assertEquals(1, rootOccurs.slots().get(0).end());
    }

    @Test
    void emitsNestedOccurrencesInColumnOrderWithOriginalChains() {
        Layout layout = Layout.build(
                "01 R. 05 G OCCURS 2. 10 A PIC X OCCURS 2. 10 B PIC X(2). 05 Z PIC X.");
        Node g = layout.root().children().get(0);
        Node a = g.children().get(0);
        Node b = g.children().get(1);

        assertEquals(9, layout.length());
        assertEquals(4, g.unit());
        assertEquals(8, g.total());
        assertEquals(1, a.unit());
        assertEquals(2, a.total());
        assertEquals(List.of(
                "(G(1),1,4)",
                "(G(1).A(1),1,1)",
                "(G(1).A(2),2,1)",
                "(G(1).B,3,2)",
                "(G(2),5,4)",
                "(G(2).A(1),5,1)",
                "(G(2).A(2),6,1)",
                "(G(2).B,7,2)",
                "(Z,9,1)"),
                layout.slots().stream().map(slot -> "(" + slot.path() + "," + slot.start() + "," + slot.length() + ")").toList());

        Slot firstGroup = layout.slots().get(0);
        Slot firstA = layout.slots().get(1);
        Slot firstB = layout.slots().get(3);
        Slot secondGroup = layout.slots().get(4);
        assertTrue(firstGroup.group());
        assertNull(firstGroup.pic());
        assertEquals(List.of(g), firstGroup.chain());
        assertEquals(List.of(g, a), firstA.chain());
        assertSame(g, firstA.chain().get(0));
        assertSame(a, firstA.chain().get(1));
        assertEquals(List.of(g, b), firstB.chain());
        assertEquals(List.of(g), secondGroup.chain());
        assertSame(g, secondGroup.chain().get(0));
    }

    @Test
    void acceptsLengthAndLeafCountBoundariesBeforeExpansion() {
        Layout maxLength = Layout.build("01 R. 05 A PIC X(128) OCCURS 32.");
        assertEquals(4096, maxLength.length());
        assertEquals(32, maxLength.slots().size());
        assertEquals("A(1)", maxLength.slots().get(0).path());
        assertEquals(1, maxLength.slots().get(0).start());
        assertEquals(128, maxLength.slots().get(0).length());
        assertEquals("A(32)", maxLength.slots().get(31).path());
        assertEquals(3969, maxLength.slots().get(31).start());
        assertEquals(4096, maxLength.slots().get(31).end());

        Layout maxFields = Layout.build("01 R. 05 A OCCURS 40. 10 B PIC X OCCURS 50.");
        assertEquals(2000, maxFields.length());
        assertEquals(2040, maxFields.slots().size());
        assertEquals("A(1)", maxFields.slots().get(0).path());
        assertTrue(maxFields.slots().get(0).group());
        assertEquals("A(1).B(1)", maxFields.slots().get(1).path());
        assertEquals(1, maxFields.slots().get(1).start());
        assertEquals("A(1).B(50)", maxFields.slots().get(50).path());
        assertEquals(50, maxFields.slots().get(50).start());
        assertEquals("A(2)", maxFields.slots().get(51).path());
        assertEquals("A(40).B(50)", maxFields.slots().get(2039).path());
        assertEquals(2000, maxFields.slots().get(2039).start());
    }

    @Test
    void rejectsExcessLengthAndLeafCountsBeforeExpandingSlots() {
        assertFailure("01 R. 05 A PIC X(128) OCCURS 32. 05 B PIC X.",
                "layout is 4097 characters (above 4096)");
        assertFailure("01 R. 05 A OCCURS 40. 10 B PIC X OCCURS 50. 05 C PIC X.",
                "layout has 2001 fields (above 2000)");
        assertFailure("01 R. 05 A OCCURS 50. 10 B OCCURS 50. 15 C PIC X.",
                "layout has 2500 fields (above 2000)");
        assertFailure("01 R. 05 G OCCURS 40. 10 A PIC X OCCURS 50. 05 FILLER PIC X.",
                "layout has 2001 fields (above 2000)");
        assertFailure("01 R. 05 G OCCURS 40. 10 A PIC X. 10 P REDEFINES A PIC X. 10 B PIC X OCCURS 49.",
                "layout has 2040 fields (above 2000)");
        assertFailure("01 R. 05 A OCCURS 99. 10 B OCCURS 99. 15 C OCCURS 99. 20 D OCCURS 99. 25 E PIC X OCCURS 99.",
                "layout is 9509900499 characters (above 4096)");
    }

    @Test
    void expandsAnExplicitThirtyOccurrenceBoundary() {
        Layout layout = Layout.build("01 R. 05 A PIC X OCCURS 30.");

        assertEquals(30, layout.slots().size());
        assertEquals("A(1)", layout.slots().get(0).path());
        assertEquals(1, layout.slots().get(0).start());
        assertEquals("A(30)", layout.slots().get(29).path());
        assertEquals(30, layout.slots().get(29).end());
    }

    private void assertFailure(String text, String expectedMessage) {
        Failure failure = assertThrows(Failure.class, () -> Layout.build(text));
        assertEquals(expectedMessage, failure.getMessage());
    }
}
