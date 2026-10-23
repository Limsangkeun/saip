package com.saip.blog;

import com.saip.blog.api.ApiTokenFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
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

	/** 공개 화면. 계획 2에서 Google 로그인과 관리자 경로를 붙인다. */
	@Bean
	@Order(2)
	SecurityFilterChain pageChain(HttpSecurity http) throws Exception {
		return http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll()).build();
	}

}
