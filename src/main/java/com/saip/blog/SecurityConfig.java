package com.saip.blog;

import com.saip.blog.api.ApiTokenFilter;
import com.saip.blog.auth.AdminAuthorities;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
class SecurityConfig {

	/**
	 * 스크립트는 우리 사이트와 두 CDN(highlight.js, Mermaid)만. 스타일 인라인은 Mermaid가 SVG에 &lt;style&gt;을 넣어서 허용한다. connect-src의 jsdelivr는
	 * Mermaid ESM이 나머지 조각을 불러올 때 쓴다.
	 */
	static final String CSP = "default-src 'self'; "
			+ "script-src 'self' https://cdnjs.cloudflare.com https://cdn.jsdelivr.net; "
			+ "style-src 'self' 'unsafe-inline' https://cdnjs.cloudflare.com; img-src 'self' data:; "
			+ "connect-src 'self' https://cdn.jsdelivr.net; font-src 'self' data:; object-src 'none'; base-uri 'self'; "
			+ "form-action 'self'; frame-ancestors 'none'";

	/** Claude Code용 API: Bearer 토큰 필수, 쿠키·세션을 쓰지 않으므로 CSRF는 끈다. */
	@Bean
	@Order(1)
	SecurityFilterChain apiChain(HttpSecurity http, @Value("${blog.api-token:}") String token) throws Exception {
		return http.securityMatcher("/api/**")
			.authorizeHttpRequests(auth -> auth.anyRequest().hasRole("API"))
			.addFilterBefore(new ApiTokenFilter(token), UsernamePasswordAuthenticationFilter.class)
			.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.csrf(csrf -> csrf.disable())
			.exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
			.build();
	}

	/** 화면: 읽기는 누구나, 댓글은 로그인, 관리는 관리자. 로그인은 Google, 쿠키 세션이므로 CSRF를 켠 채로 둔다. */
	@Bean
	@Order(2)
	SecurityFilterChain pageChain(HttpSecurity http, AdminAuthorities adminAuthorities) throws Exception {
		return http
			.authorizeHttpRequests(auth -> auth.requestMatchers("/admin/**")
				.hasRole("ADMIN")
				.requestMatchers(HttpMethod.POST, "/notes/*/comments/**")
				.authenticated()
				.requestMatchers("/notes/*/comments/login")
				.authenticated()
				.anyRequest()
				.permitAll())
			.oauth2Login(o -> o.userInfoEndpoint(u -> u.userAuthoritiesMapper(adminAuthorities)))
			.logout(l -> l.logoutSuccessUrl("/"))
			.headers(h -> h.contentSecurityPolicy(c -> c.policyDirectives(CSP)))
			.build();
	}

}
