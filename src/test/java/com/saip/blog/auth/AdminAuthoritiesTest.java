package com.saip.blog.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;

class AdminAuthoritiesTest {

	@Test
	void verifiedAdminEmailGetsAdmin() {
		assertThat(roles("admin@gmail.com", "admin@gmail.com", true)).contains("ROLE_ADMIN", "ROLE_USER");
	}

	@Test
	void emailIsCaseInsensitive() {
		assertThat(roles("admin@gmail.com", "Admin@Gmail.com", true)).contains("ROLE_ADMIN");
	}

	@Test
	void unverifiedEmailIsNotAdmin() {
		assertThat(roles("admin@gmail.com", "admin@gmail.com", false)).contains("ROLE_USER")
			.doesNotContain("ROLE_ADMIN");
	}

	@Test
	void otherEmailIsUser() {
		assertThat(roles("admin@gmail.com", "other@gmail.com", true)).contains("ROLE_USER")
			.doesNotContain("ROLE_ADMIN");
	}

	@Test
	void blankAdminEmailMeansNoAdmin() {
		assertThat(roles("", "", true)).doesNotContain("ROLE_ADMIN");
	}

	private static List<String> roles(String adminEmail, String email, boolean verified) {
		OidcIdToken token = new OidcIdToken("t", Instant.now(), Instant.now().plusSeconds(60),
				Map.of("sub", "u1", "email", email, "email_verified", verified));
		Collection<? extends GrantedAuthority> mapped = new AdminAuthorities(adminEmail)
			.mapAuthorities(List.of(new OidcUserAuthority(token)));
		return mapped.stream().map(GrantedAuthority::getAuthority).toList();
	}

}
