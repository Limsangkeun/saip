package com.saip.blog.admin;

import static com.saip.blog.admin.AdminAccessTest.admin;
import static com.saip.blog.admin.AdminAccessTest.user;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saip.blog.TestcontainersConfiguration;
import com.saip.blog.note.NoteService;
import org.junit.jupiter.api.BeforeEach;
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
class AdminRevisionTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	NoteService notes;

	long v1;

	@BeforeEach
	void twoVersions() {
		notes.create("Docker", "deploy", "배포", "a\n옛 줄\nc");
		notes.update("docker", "Docker", "deploy", null, "a\n새 줄\nc");
		v1 = notes.revisions("docker").get(0).getId();
	}

	@Test
	void revisionListShowsVersions() throws Exception {
		mvc.perform(get("/admin/notes/docker/revisions").with(admin()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("/admin/revisions/" + v1)));
	}

	@Test
	void revisionPageShowsDiff() throws Exception {
		mvc.perform(get("/admin/revisions/" + v1).with(admin()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("class=\"diff-removed\"")))
			.andExpect(content().string(containsString("class=\"diff-added\"")))
			.andExpect(content().string(containsString("옛 줄")))
			.andExpect(content().string(containsString("새 줄")));
	}

	@Test
	void diffEscapesHtml() throws Exception {
		notes.update("docker", "Docker", "deploy", null, "<script>alert(1)</script>");
		long latest = notes.revisions("docker").get(0).getId();

		mvc.perform(get("/admin/revisions/" + latest).with(admin()))
			.andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")));
	}

	@Test
	void restoreRedirectsToNote() throws Exception {
		mvc.perform(post("/admin/revisions/" + v1 + "/restore").with(admin()).with(csrf()))
			.andExpect(redirectedUrl("/notes/docker"));

		assertEquals("a\n옛 줄\nc", notes.get("docker").getBodyMd());
	}

	@Test
	void deleteRevisionRedirectsToList() throws Exception {
		mvc.perform(post("/admin/revisions/" + v1 + "/delete").with(admin()).with(csrf()))
			.andExpect(redirectedUrl("/admin/notes/docker/revisions"));

		assertEquals(0, notes.revisions("docker").size());
	}

	@Test
	void missingRevisionIs404() throws Exception {
		mvc.perform(get("/admin/revisions/999999").with(admin())).andExpect(status().isNotFound());
	}

	@Test
	void userCannotSeeRevisions() throws Exception {
		mvc.perform(get("/admin/revisions/" + v1).with(user())).andExpect(status().isForbidden());
		mvc.perform(post("/admin/revisions/" + v1 + "/restore").with(user()).with(csrf()))
			.andExpect(status().isForbidden());
	}

}
