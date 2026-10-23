package com.saip.blog.note;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SlugsTest {

	@Test
	void keepsKoreanAndLowercases() {
		assertEquals("docker-멀티스테이지-빌드", Slugs.of("Docker 멀티스테이지 빌드"));
	}

	@Test
	void collapsesSymbolsAndTrims() {
		assertEquals("spring-boot-4", Slugs.of("  Spring Boot 4! "));
	}

	@Test
	void longTitleIsCutAtHyphenWithin60() {
		String slug = Slugs.of("Spring Boot에서 docker compose로 PostgreSQL 자동 실행하고 Testcontainers로 테스트하는 방법");
		assertTrue(slug.length() <= 60, slug);
		assertFalse(slug.endsWith("-"), slug);
		assertTrue(slug.startsWith("spring-boot에서-docker-compose로-postgresql"), slug);
	}

}
