package com.saip.blog.web;

import com.saip.blog.comment.CommentController;
import com.saip.blog.comment.CommentService;
import com.saip.blog.markdown.MarkdownRenderer;
import com.saip.blog.note.Category;
import com.saip.blog.note.Note;
import com.saip.blog.note.NoteNotFoundException;
import com.saip.blog.note.NoteService;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Controller
class PageController {

	private final NoteService notes;

	private final MarkdownRenderer markdown;

	private final CommentService comments;

	PageController(NoteService notes, MarkdownRenderer markdown, CommentService comments) {
		this.notes = notes;
		this.markdown = markdown;
		this.comments = comments;
	}

	@ModelAttribute("tree")
	List<Category> tree() {
		return notes.tree();
	}

	@GetMapping("/")
	String home(Model model) {
		model.addAttribute("notes", notes.recent(20));
		return "home";
	}

	@GetMapping("/notes/{slug}")
	String note(@PathVariable String slug, Model model, Authentication auth) {
		Note note = notes.get(slug);
		model.addAttribute("note", note);
		model.addAttribute("html", markdown.render(note.getBodyMd()));
		model.addAttribute("description", markdown.summary(note.getBodyMd(), 150));
		model.addAttribute("canonical", noteUrl(note));
		model.addAttribute("comments", comments.forNote(note));
		// 템플릿이 수정·삭제 버튼을 보여줄지 정할 때 쓴다. 실제 권한 검사는 CommentService가 한다.
		model.addAttribute("viewerSub", auth != null && auth.getPrincipal() instanceof OidcUser u ? u.getSubject() : null);
		model.addAttribute("viewerIsAdmin", CommentController.isAdmin(auth));
		return "note";
	}

	@GetMapping("/search")
	String search(@RequestParam(defaultValue = "") String q, Model model) {
		model.addAttribute("q", q);
		model.addAttribute("notes", q.isBlank() ? List.of() : notes.search(q));
		return "search";
	}

	/** 검색 로봇용 노트 목록. 앱이 Caddy 뒤에 있어도 forward 헤더 덕분에 https://saip.nexus 주소가 나온다. */
	@GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
	@ResponseBody
	String sitemap() {
		String urls = notes.tree()
			.stream()
			.flatMap(c -> c.getNotes().stream())
			.map(n -> "<url><loc>" + noteUrl(n) + "</loc><lastmod>" + n.getUpdatedAt() + "</lastmod></url>\n")
			.collect(Collectors.joining());
		return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
				+ "<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n" + urls + "</urlset>\n";
	}

	/** 한글 슬러그를 퍼센트 인코딩한 절대 주소. 인코딩 결과에는 &amp;·&lt; 가 없어 XML에 그대로 넣어도 된다. */
	private static String noteUrl(Note note) {
		return ServletUriComponentsBuilder.fromCurrentContextPath()
			.path("/notes/{slug}")
			.buildAndExpand(note.getSlug())
			.encode()
			.toUriString();
	}

	@ExceptionHandler(NoteNotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	String notFound() {
		return "error/404";
	}

}
