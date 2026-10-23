package com.saip.blog.auth;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.stereotype.Component;

/**
 * Google 로그인 사용자에게 역할을 준다. 모두 ROLE_USER, 설정된 관리자 이메일이면 ROLE_ADMIN.
 * Google이 확인한 이메일(email_verified)만 인정한다. 확인 안 된 이메일로 관리자를 사칭하지 못하게.
 */
@Component
public class AdminAuthorities implements GrantedAuthoritiesMapper {

	private final String adminEmail;

	public AdminAuthorities(@Value("${blog.admin-email:}") String adminEmail) {
		this.adminEmail = adminEmail.strip().toLowerCase(Locale.ROOT);
	}

	@Override
	public Collection<? extends GrantedAuthority> mapAuthorities(Collection<? extends GrantedAuthority> authorities) {
		Set<GrantedAuthority> mapped = new HashSet<>(authorities);
		mapped.add(new SimpleGrantedAuthority("ROLE_USER"));
		if (authorities.stream().anyMatch(this::isAdmin)) {
			mapped.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
		}
		return mapped;
	}

	private boolean isAdmin(GrantedAuthority authority) {
		if (adminEmail.isEmpty() || !(authority instanceof OidcUserAuthority oidc)) {
			return false;
		}
		OidcIdToken token = oidc.getIdToken();
		return Boolean.TRUE.equals(token.getEmailVerified()) && token.getEmail() != null
				&& adminEmail.equals(token.getEmail().toLowerCase(Locale.ROOT));
	}

}
