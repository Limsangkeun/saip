package com.saip.blog.note;

import static com.saip.blog.note.LineDiff.Kind.ADDED;
import static com.saip.blog.note.LineDiff.Kind.REMOVED;
import static com.saip.blog.note.LineDiff.Kind.SAME;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.saip.blog.note.LineDiff.Line;
import java.util.List;
import org.junit.jupiter.api.Test;

class LineDiffTest {

	@Test
	void identicalTextIsAllSame() {
		assertEquals(List.of(new Line(SAME, "a"), new Line(SAME, "b")), LineDiff.of("a\nb", "a\nb"));
	}

	@Test
	void addedAndRemovedLines() {
		assertEquals(List.of(new Line(SAME, "a"), new Line(REMOVED, "b"), new Line(ADDED, "x"), new Line(SAME, "c")),
				LineDiff.of("a\nb\nc", "a\nx\nc"));
	}

	@Test
	void emptyBefore() {
		assertEquals(List.of(new Line(ADDED, "a")), LineDiff.of("", "a"));
	}

	@Test
	void appendAtEnd() {
		assertEquals(List.of(new Line(SAME, "a"), new Line(SAME, "c"), new Line(SAME, "d"), new Line(ADDED, "e")),
				LineDiff.of("a\nc\nd", "a\nc\nd\ne"));
	}

	@Test
	void windowsLineEndingsCompareEqual() {
		assertEquals(List.of(new Line(SAME, "a"), new Line(SAME, "b")), LineDiff.of("a\r\nb", "a\nb"));
	}

}
