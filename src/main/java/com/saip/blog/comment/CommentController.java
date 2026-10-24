package com.saip.blog.comment;

import com.saip.blog.note.NoteNotFoundException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriUtils;

/** 노트 화면의 댓글 폼. 로그인은 SecurityConfig가 요구하고, 본인 확인은 CommentService가 한다. */
@Controller
@RequestMapping("/notes/{slug}/comments")
public class CommentController {

	private final CommentService comments;

	CommentController(CommentService comments) {
		this.comments = comments;
	}

	/**
	 * "로그인하고 댓글 달기" 링크. 로그인이 필요한 주소라서 Spring Security가 이 요청을 기억해 두고 Google 로그인 뒤 다시 보내 준다. 돌아올 곳은 서버가 정하므로
	 * 외부 사이트로 보내는 오픈 리다이렉트가 생기지 않는다.
	 */
	@GetMapping("/login")
	String loginThenBack(@PathVariable String slug) {
		return redirect(slug, "comments");
	}

	@PostMapping
	String add(@PathVariable String slug, @RequestParam String body, @AuthenticationPrincipal OidcUser user,
			RedirectAttributes flash) {
		try {
			String name = user.getFullName() == null ? "익명" : user.getFullName();
			return redirect(slug, "comment-" + comments.add(slug, user.getSubject(), name, body).getId());
		}
		catch (IllegalArgumentException e) {
			flash.addFlashAttribute("commentError", e.getMessage());
			return redirect(slug, "comments");
		}
	}

	@PostMapping("/{id}")
	String edit(@PathVariable String slug, @PathVariable long id, @RequestParam String body,
			@AuthenticationPrincipal OidcUser user, RedirectAttributes flash) {
		try {
			comments.edit(id, user.getSubject(), body);
		}
		catch (IllegalArgumentException e) {
			flash.addFlashAttribute("commentError", e.getMessage());
		}
		return redirect(slug, "comment-" + id);
	}

	@PostMapping("/{id}/delete")
	String delete(@PathVariable String slug, @PathVariable long id, @AuthenticationPrincipal OidcUser user,
			Authentication auth) {
		comments.delete(id, user.getSubject(), isAdmin(auth));
		return redirect(slug, "comments");
	}

	@ExceptionHandler(NoteNotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	String notFound() {
		return "error/404";
	}

	// 관리자 권한은 OidcUser가 아니라 Authentication에 있다(로그인 때 AdminAuthorities가 매핑한 결과).
	public static boolean isAdmin(Authentication auth) {
		return auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
	}

	private static String redirect(String slug, String anchor) {
		return "redirect:/notes/" + UriUtils.encodePathSegment(slug, StandardCharsets.UTF_8) + "#" + anchor;
	}

}
