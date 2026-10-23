package com.saip.blog.web;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

/** 템플릿에서 {@code ${@dates.day(note.updatedAt)}}로 쓴다. */
@Component("dates")
class Dates {

	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd")
		.withZone(ZoneId.of("Asia/Seoul"));

	public String day(Instant instant) {
		return DAY.format(instant);
	}

}
