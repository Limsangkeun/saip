package com.saip.blog.admin;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saip.blog.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OidcLoginRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public class AdminAccessTest {

	@Autowired
	MockMvc mvc;

	public static OidcLoginRequestPostProcessor user() {
		return oidcLogin().idToken(t -> t.subject("u1").claim("name", "유저"));
	}

	public static OidcLoginRequestPostProcessor admin() {
		return user().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_USER"));
	}

	@Test
	void anonymousAdminRedirectsToGoogle() throws Exception {
		mvc.perform(get("/admin"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/oauth2/authorization/google"));
	}

	@Test
	void userGets403OnAdmin() throws Exception {
		mvc.perform(get("/admin").with(user())).andExpect(status().isForbidden());
	}

	@Test
	void adminSeesAdminPage() throws Exception {
		mvc.perform(get("/admin").with(admin()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("노트 관리")));
	}

	@Test
	void topbarShowsLoginForAnonymous() throws Exception {
		mvc.perform(get("/"))
			.andExpect(content().string(containsString("로그인")))
			.andExpect(content().string(not(containsString("로그아웃"))));
	}

	@Test
	void topbarShowsAdminLinkForAdmin() throws Exception {
		mvc.perform(get("/").with(admin()))
			.andExpect(content().string(containsString("href=\"/admin\"")))
			.andExpect(content().string(containsString("로그아웃")));
	}

	@Test
	void topbarHidesAdminLinkForUser() throws Exception {
		mvc.perform(get("/").with(user()))
			.andExpect(content().string(containsString("유저")))
			.andExpect(content().string(not(containsString("href=\"/admin\""))));
	}

	@Test
	void apiTokenStillWorks() throws Exception {
		mvc.perform(get("/api/notes")).andExpect(status().isUnauthorized());
	}

}
