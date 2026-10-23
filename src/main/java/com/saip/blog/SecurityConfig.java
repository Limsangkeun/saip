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
				.anyRequest()
				.permitAll())
			.oauth2Login(o -> o.userInfoEndpoint(u -> u.userAuthoritiesMapper(adminAuthorities)))
			.logout(l -> l.logoutSuccessUrl("/"))
			.build();
	}

}
