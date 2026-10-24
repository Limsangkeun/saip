package com.saip.blog.comment;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saip.blog.TestcontainersConfiguration;
import com.saip.blog.note.Note;
import com.saip.blog.note.NoteService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OidcLoginRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class CommentTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	NoteService notes;

	@Autowired
	CommentService comments;

	@Autowired
	JdbcTemplate jdbc;

	Note note;

	@BeforeEach
	void note() {
		note = notes.create("Docker", "deploy", "배포", "본문");
	}

	static OidcLoginRequestPostProcessor as(String sub, String name) {
		return oidcLogin().idToken(t -> t.subject(sub).claim("name", name).claim("email", sub + "@gmail.com"))
			.authorities(new SimpleGrantedAuthority("ROLE_USER"));
	}

	static OidcLoginRequestPostProcessor admin() {
		return oidcLogin().idToken(t -> t.subject("admin").claim("name", "관리자"))
			.authorities(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_ADMIN"));
	}

	@Test
	void anonymousCannotComment() throws Exception {
		mvc.perform(post("/notes/docker/comments").with(csrf()).param("body", "안녕"))
			.andExpect(redirectedUrl("/oauth2/authorization/google"));
		assertEquals(0, list().size());
	}

	@Test
	void userComments() throws Exception {
		write("u1", "유저", "첫 댓글").andExpect(redirectedUrlPattern("/notes/docker#comment-*"));

		mvc.perform(get("/notes/docker"))
			.andExpect(content().string(containsString("첫 댓글")))
			.andExpect(content().string(containsString("유저")));
	}

	@Test
	void loginLinkReturnsToNoteComments() throws Exception {
		mvc.perform(get("/notes/docker/comments/login")).andExpect(redirectedUrl("/oauth2/authorization/google"));
		mvc.perform(get("/notes/docker/comments/login").with(as("u1", "유저")))
			.andExpect(redirectedUrl("/notes/docker#comments"));
	}

	@Test
	void anonymousSeesLoginLinkNotForm() throws Exception {
		mvc.perform(get("/notes/docker"))
			.andExpect(content().string(containsString("href=\"/notes/docker/comments/login\"")))
			.andExpect(content().string(containsString("로그인하고 댓글 달기")))
			.andExpect(content().string(not(containsString("name=\"body\""))));
	}

	@Test
	void scriptIsEscaped() throws Exception {
		write("u1", "유저", "<script>alert(1)</script>");

		mvc.perform(get("/notes/docker"))
			.andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")))
			.andExpect(content().string(not(containsString("<script>alert(1)"))));
	}

	@Test
	void blankOrTooLongIsRejected() throws Exception {
		write("u1", "유저", "   ").andExpect(flash().attribute("commentError", "댓글은 1~2000자로 써 주세요"));
		write("u1", "유저", "가".repeat(2001)).andExpect(flash().attributeExists("commentError"));

		assertEquals(0, list().size());
	}

	@Test
	void exactly2000IsAccepted() throws Exception {
		write("u1", "유저", "가".repeat(2000));
		assertEquals(1, list().size());
	}

	@Test
	void otherUserCannotEditOrDelete() throws Exception {
		long id = comments.add("docker", "u1", "유저", "원래").getId();

		mvc.perform(post("/notes/docker/comments/" + id).with(as("u2", "남")).with(csrf()).param("body", "바꿈"))
			.andExpect(status().isForbidden());
		mvc.perform(post("/notes/docker/comments/" + id + "/delete").with(as("u2", "남")).with(csrf()))
			.andExpect(status().isForbidden());

		assertEquals("원래", list().get(0).getBody());
	}

	@Test
	void ownerEditsAndDeletes() throws Exception {
		long id = comments.add("docker", "u1", "유저", "원래").getId();

		mvc.perform(post("/notes/docker/comments/" + id).with(as("u1", "유저")).with(csrf()).param("body", "고침"))
			.andExpect(redirectedUrl("/notes/docker#comment-" + id));
		assertEquals("고침", list().get(0).getBody());

		mvc.perform(post("/notes/docker/comments/" + id + "/delete").with(as("u1", "유저")).with(csrf()))
			.andExpect(redirectedUrl("/notes/docker#comments"));
		assertEquals(0, list().size());
	}

	@Test
	void ownerSeesEditButOtherDoesNot() throws Exception {
		long id = comments.add("docker", "u1", "유저", "원래").getId();
		String editAction = "action=\"/notes/docker/comments/" + id + "\"";

		mvc.perform(get("/notes/docker").with(as("u1", "유저"))).andExpect(content().string(containsString(editAction)));
		mvc.perform(get("/notes/docker").with(as("u2", "남")))
			.andExpect(content().string(not(containsString(editAction))));
	}

	@Test
	void adminDeletesAnyComment() throws Exception {
		long id = comments.add("docker", "u1", "유저", "스팸").getId();

		mvc.perform(post("/notes/docker/comments/" + id + "/delete").with(admin()).with(csrf()))
			.andExpect(redirectedUrl("/notes/docker#comments"));
		assertEquals(0, list().size());
	}

	@Test
	void adminCannotEditOthersComment() throws Exception {
		long id = comments.add("docker", "u1", "유저", "원래").getId();

		mvc.perform(post("/notes/docker/comments/" + id).with(admin()).with(csrf()).param("body", "바꿈"))
			.andExpect(status().isForbidden());
	}

	@Test
	void adminCommentListAndDelete() throws Exception {
		long id = comments.add("docker", "u1", "유저", "스팸 댓글").getId();

		mvc.perform(get("/admin/comments").with(admin())).andExpect(content().string(containsString("스팸 댓글")));
		mvc.perform(post("/admin/comments/" + id + "/delete").with(admin()).with(csrf()))
			.andExpect(redirectedUrl("/admin/comments"));
		assertEquals(0, list().size());
	}

	@Test
	void commentWithoutCsrfIs403() throws Exception {
		mvc.perform(post("/notes/docker/comments").with(as("u1", "유저")).param("body", "안녕"))
			.andExpect(status().isForbidden());
		assertEquals(0, list().size());
	}

	@Test
	void deletingNoteDeletesComments() {
		comments.add("docker", "u1", "유저", "댓글");

		notes.delete("docker");

		assertEquals(0, comments.recent(10).size());
	}

	@Test
	void adminDeletesNoteWithRevisionsAndComments() throws Exception {
		notes.update("docker", "Docker", "deploy", null, "v2");
		comments.add("docker", "u1", "유저", "댓글");

		mvc.perform(post("/admin/notes/docker/delete").with(admin()).with(csrf())).andExpect(redirectedUrl("/admin"));

		assertEquals(0, comments.recent(10).size());
		assertEquals(0, jdbc.queryForObject("select count(*) from note_revision", Integer.class));
	}

	@Test
	void emailIsNeverShown() throws Exception {
		write("u1", "유저", "안녕");

		mvc.perform(get("/notes/docker").with(as("u1", "유저")))
			.andExpect(content().string(not(containsString("u1@gmail.com"))));
	}

	private ResultActions write(String sub, String name, String body) throws Exception {
		return mvc.perform(post("/notes/docker/comments").with(as(sub, name)).with(csrf()).param("body", body));
	}

	private List<Comment> list() {
		return comments.forNote(notes.get("docker"));
	}

}
