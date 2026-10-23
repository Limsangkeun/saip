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
	void searchFinds() throws Exception {
		notes.create("Docker Compose", "deploy", "배포", "compose 사용법");

		mvc.perform(get("/search").param("q", "compose"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Docker Compose")));
	}

}
