package com.saip.blog.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * {@code Authorization: Bearer <토큰>}이 설정된 토큰과 같으면 Claude Code로 인증한다. 설정된 토큰이 비어 있으면 아무도 인증하지 않는다.
 * 빈으로 등록하지 않는다(서블릿 필터로 모든 요청에 걸리는 것을 막기 위해). SecurityConfig가 /api/** 체인에만 넣는다.
 */
public class ApiTokenFilter extends OncePerRequestFilter {

	private final byte[] expected;

	public ApiTokenFilter(String token) {
		this.expected = token == null ? new byte[0] : token.getBytes(StandardCharsets.UTF_8);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String header = request.getHeader("Authorization");
		if (expected.length > 0 && header != null && header.startsWith("Bearer ")) {
			byte[] given = header.substring(7).getBytes(StandardCharsets.UTF_8);
			if (MessageDigest.isEqual(expected, given)) {
				SecurityContextHolder.getContext()
					.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("claude-code", null,
							List.of(new SimpleGrantedAuthority("ROLE_API"))));
			}
		}
		chain.doFilter(request, response);
	}

}
