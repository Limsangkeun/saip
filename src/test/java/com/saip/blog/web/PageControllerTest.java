package com.saip.blog.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saip.blog.TestcontainersConfiguration;
import com.saip.blog.note.NoteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class PageControllerTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	NoteService notes;

	@Test
	void layoutLinksServedFavicon() throws Exception {
		mvc.perform(get("/")).andExpect(content().string(containsString("<link rel=\"icon\" href=\"/favicon.svg\"")));
		mvc.perform(get("/favicon.svg"))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith("image/svg+xml"));
	}

	@Test
	void layoutRestoresSidebarStateBeforeRender() throws Exception {
		// head에서 동기로 실행해야 접힌 사이드바가 그려졌다 사라지는 깜빡임이 없다
		String html = mvc.perform(get("/")).andReturn().getResponse().getContentAsString();
		int script = html.indexOf("<script src=\"/js/side.js\"></script>");
		org.junit.jupiter.api.Assertions.assertTrue(script > 0 && script < html.indexOf("</head>"), "side.js가 head 안에 있어야 한다");
		mvc.perform(get("/js/side.js")).andExpect(status().isOk());
	}

	@Test
	void homeShowsTreeAndRecent() throws Exception {
		notes.create("Docker", "deploy", "배포", "# hi");

		mvc.perform(get("/"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("배포")))
			.andExpect(content().string(containsString("Docker")));
	}

	@Test
	void notePageRendersMarkdown() throws Exception {
		notes.create("Docker", "deploy", "배포", "|a|b|\n|-|-|\n|1|2|");

		mvc.perform(get("/notes/docker"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("<table>")));
	}

	@Test
	void koreanSlugWorks() throws Exception {
		notes.create("멀티스테이지 빌드", "deploy", "배포", "본문");

		mvc.perform(get("/notes/{slug}", "멀티스테이지-빌드")).andExpect(status().isOk());
	}

	@Test
	void missingNoteIs404() throws Exception {
		mvc.perform(get("/notes/nope")).andExpect(status().isNotFound());
	}

	@Test
	void notePageHasDescriptionAndCanonical() throws Exception {
		notes.create("멀티스테이지 빌드", "deploy", "배포", "# 제목\n\n**빌드**와 실행 단계를 나눈다.");

		mvc.perform(get("/notes/{slug}", "멀티스테이지-빌드"))
			.andExpect(content().string(containsString("<meta name=\"description\" content=\"제목 빌드와 실행 단계를 나눈다.\">")))
			.andExpect(content().string(containsString("<meta property=\"og:title\" content=\"멀티스테이지 빌드\">")))
			.andExpect(content().string(containsString(
					"<link rel=\"canonical\" href=\"http://localhost/notes/%EB%A9%80%ED%8B%B0%EC%8A%A4%ED%85%8C%EC%9D%B4%EC%A7%80-%EB%B9%8C%EB%93%9C\">")));
	}

	@Test
	void sitemapListsNotes() throws Exception {
		notes.create("멀티스테이지 빌드", "deploy", "배포", "본문");

		mvc.perform(get("/sitemap.xml"))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith("application/xml"))
			.andExpect(content().string(containsString(
					"<loc>http://localhost/notes/%EB%A9%80%ED%8B%B0%EC%8A%A4%ED%85%8C%EC%9D%B4%EC%A7%80-%EB%B9%8C%EB%93%9C</loc>")))
			.andExpect(content().string(containsString("<lastmod>")));
	}

	@Test
	void robotsPointsToSitemap() throws Exception {
		mvc.perform(get("/robots.txt"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Sitemap: https://saip.nexus/sitemap.xml")));
	}

	@Test
	void searchFinds() throws Exception {
		notes.create("Docker Compose", "deploy", "배포", "compose 사용법");

		mvc.perform(get("/search").param("q", "compose"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Docker Compose")));
	}

}
