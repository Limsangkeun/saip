package com.saip.blog.note;

import java.util.ArrayList;
import java.util.List;

/** 두 글의 줄 단위 비교. 공통 줄(LCS)은 SAME, 앞 글에만 있으면 REMOVED, 뒤 글에만 있으면 ADDED. */
public final class LineDiff {

	public enum Kind {

		SAME, ADDED, REMOVED

	}

	public record Line(Kind kind, String text) {
	}

	private LineDiff() {
	}

	// ponytail: O(n·m) LCS 표. 수백 줄짜리 노트에는 충분하고, 수천 줄이 되면 java-diff-utils(Myers 알고리즘)로 바꾼다.
	public static List<Line> of(String before, String after) {
		String[] a = lines(before);
		String[] b = lines(after);
		// lcs[i][j] = a[i..]와 b[j..]의 최장 공통 줄 수
		int[][] lcs = new int[a.length + 1][b.length + 1];
		for (int i = a.length - 1; i >= 0; i--) {
			for (int j = b.length - 1; j >= 0; j--) {
				lcs[i][j] = a[i].equals(b[j]) ? lcs[i + 1][j + 1] + 1 : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
			}
		}
		List<Line> out = new ArrayList<>();
		int i = 0;
		int j = 0;
		while (i < a.length && j < b.length) {
			if (a[i].equals(b[j])) {
				out.add(new Line(Kind.SAME, a[i++]));
				j++;
			}
			else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
				out.add(new Line(Kind.REMOVED, a[i++]));
			}
			else {
				out.add(new Line(Kind.ADDED, b[j++]));
			}
		}
		while (i < a.length) {
			out.add(new Line(Kind.REMOVED, a[i++]));
		}
		while (j < b.length) {
			out.add(new Line(Kind.ADDED, b[j++]));
		}
		return out;
	}

	private static String[] lines(String text) {
		return text.isEmpty() ? new String[0] : text.split("\\R", -1);
	}

}
