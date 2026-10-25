package com.saip.blog.admin;

import static com.saip.blog.admin.AdminAccessTest.admin;
import static com.saip.blog.admin.AdminAccessTest.user;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saip.blog.TestcontainersConfiguration;
import com.saip.blog.markdown.MarkdownRenderer;
import com.saip.blog.note.NoteNotFoundException;
import com.saip.blog.note.NoteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AdminNoteTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	NoteService notes;

	@Autowired
	MarkdownRenderer markdown;

	@Test
	void createsNoteFromForm() throws Exception {
		mvc.perform(post("/admin/notes").with(admin())
			.with(csrf())
			.param("title", "Web Note")
			.param("categorySlug", "deploy")
			.param("categoryName", "배포")
			.param("body", "## 웹에서 씀"))
			.andExpect(redirectedUrl("/notes/web-note"));

		mvc.perform(get("/notes/web-note")).andExpect(content().string(containsString("웹에서 씀")));
	}

	@Test
	void editFormShowsCurrentValues() throws Exception {
		notes.create("Docker", "deploy", "배포", "기존 본문");

		mvc.perform(get("/admin/notes/docker/edit").with(admin()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("기존 본문")))
			.andExpect(content().string(containsString("value=\"Docker\"")));
	}

	@Test
	void editKeepsRevision() throws Exception {
		notes.create("Docker", "deploy", "배포", "v1");

		mvc.perform(post("/admin/notes/docker").with(admin())
			.with(csrf())
			.param("title", "Docker")
			.param("categorySlug", "deploy")
			.param("body", "v2"))
			.andExpect(redirectedUrl("/notes/docker"));

		assertEquals("v2", notes.get("docker").getBodyMd());
		assertEquals(1, notes.revisions("docker").size());
	}

	@Test
	void editorSessionOutlivesLongWriting(@Autowired Environment env) {
		// 세션이 끝나면 CSRF 토큰도 사라져 저장이 403이 되고 쓰던 글을 잃는다(기본 30분)
		assertEquals("7d", env.getProperty("server.servlet.session.timeout"));
	}

	@Test
	void deleteNote() throws Exception {
		notes.create("Docker", "deploy", "배포", "v1");

		mvc.perform(post("/admin/notes/docker/delete").with(admin()).with(csrf())).andExpect(redirectedUrl("/admin"));

		assertThrows(NoteNotFoundException.class, () -> notes.get("docker"));
	}

	@Test
	void indexHasDeleteButton() throws Exception {
		notes.create("Docker", "deploy", "배포", "v1");

		mvc.perform(get("/admin").with(admin()))
			.andExpect(content().string(containsString("action=\"/admin/notes/docker/delete\"")));
	}

	@Test
	void indexSearchFiltersNotes() throws Exception {
		notes.create("Docker", "deploy", "배포", "컨테이너 이야기");
		notes.create("Caddy", "deploy", "배포", "HTTPS 이야기");

		mvc.perform(get("/admin").param("q", "컨테이너").with(admin()))
			.andExpect(content().string(containsString("/admin/notes/docker/edit")))
			.andExpect(content().string(not(containsString("/admin/notes/caddy/edit"))));
	}

	@Test
	void deleteWithoutCsrfIs403() throws Exception {
		notes.create("Docker", "deploy", "배포", "v1");

		mvc.perform(post("/admin/notes/docker/delete").with(admin())).andExpect(status().isForbidden());

		assertEquals("v1", notes.get("docker").getBodyMd());
	}

	@Test
	void userCannotDelete() throws Exception {
		notes.create("Docker", "deploy", "배포", "v1");

		mvc.perform(post("/admin/notes/docker/delete").with(user()).with(csrf())).andExpect(status().isForbidden());

		assertEquals("v1", notes.get("docker").getBodyMd());
	}

	@Test
	void previewMatchesPublicRenderer() throws Exception {
		String md = "# 제목\n<script>alert(1)</script>\n[x](javascript:alert(1))";

		mvc.perform(post("/admin/preview").with(admin())
			.with(csrf())
			.contentType(MediaType.TEXT_PLAIN)
			.content(md))
			.andExpect(status().isOk())
			.andExpect(content().string(markdown.render(md)));
	}

	@Test
	void symbolTitleShowsFormError() throws Exception {
		mvc.perform(post("/admin/notes").with(admin())
			.with(csrf())
			.param("title", "!!!")
			.param("categorySlug", "deploy")
			.param("categoryName", "배포")
			.param("body", "본문유지123"))
			.andExpect(status().isBadRequest())
			.andExpect(content().string(containsString("제목에 글자나 숫자")))
			.andExpect(content().string(containsString("본문유지123")));
	}

	@Test
	void duplicateTitleShowsFormError() throws Exception {
		notes.create("Docker", "deploy", "배포", "v1");

		mvc.perform(post("/admin/notes").with(admin())
			.with(csrf())
			.param("title", "Docker")
			.param("categorySlug", "deploy")
			.param("body", "새 본문"))
			.andExpect(status().isBadRequest())
			.andExpect(content().string(containsString("이미 있는")))
			.andExpect(content().string(containsString("새 본문")));
	}

	@Test
	void webEditorSkipsSecretScan() throws Exception {
		mvc.perform(post("/admin/notes").with(admin())
			.with(csrf())
			.param("title", "Secret Demo")
			.param("categorySlug", "deploy")
			.param("categoryName", "배포")
			.param("body", "password=hunter2222"))
			.andExpect(redirectedUrl("/notes/secret-demo"));
	}

}
