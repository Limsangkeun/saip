package com.saip.blog.note;

import java.util.Locale;

public final class Slugs {

	static final int MAX_LENGTH = 60;

	private Slugs() {
	}

	/** 소문자, 글자·숫자 외 연속 문자는 '-' 하나로, 60자를 넘으면 '-' 경계에서 자른다. */
	public static String of(String text) {
		String slug = trimHyphens(text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "-"));
		if (slug.length() <= MAX_LENGTH) {
			return slug;
		}
		String cut = slug.substring(0, MAX_LENGTH);
		int lastHyphen = cut.lastIndexOf('-');
		return trimHyphens(lastHyphen > 0 ? cut.substring(0, lastHyphen) : cut);
	}

	private static String trimHyphens(String s) {
		return s.replaceAll("^-+|-+$", "");
	}

}
