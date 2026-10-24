package com.saip.blog.admin;

import com.saip.blog.comment.CommentService;
import com.saip.blog.markdown.MarkdownRenderer;
import com.saip.blog.note.Category;
import com.saip.blog.note.DuplicateSlugException;
import com.saip.blog.note.LineDiff;
import com.saip.blog.note.Note;
import com.saip.blog.note.NoteNotFoundException;
import com.saip.blog.note.NoteRevision;
import com.saip.blog.note.NoteService;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.util.UriUtils;

/** 관리자 화면. 접근 권한(ROLE_ADMIN)은 SecurityConfig가 /admin/**에 건다. 웹 편집은 사람이 쓰는 글이라 비밀값 검사를 하지 않는다. */
@Controller
@RequestMapping("/admin")
class AdminController {

	private final NoteService notes;

	private final MarkdownRenderer markdown;

	private final CommentService comments;

	AdminController(NoteService notes, MarkdownRenderer markdown, CommentService comments) {
		this.notes = notes;
		this.markdown = markdown;
		this.comments = comments;
	}

	record NoteForm(String title, String categorySlug, String categoryName, String body) {
	}

	@ModelAttribute("tree")
	List<Category> tree() {
		return notes.tree();
	}

	@GetMapping
	String index() {
		return "admin/index";
	}

	@GetMapping("/notes/new")
	String newNote(Model model) {
		model.addAttribute("form", new NoteForm("", "", "", ""));
		return "admin/edit";
	}

	@GetMapping("/notes/{slug}/edit")
	String edit(@PathVariable String slug, Model model) {
		Note n = notes.get(slug);
		model.addAttribute("slug", slug);
		model.addAttribute("form",
				new NoteForm(n.getTitle(), n.getCategory().getSlug(), n.getCategory().getName(), n.getBodyMd()));
		return "admin/edit";
	}

	@PostMapping("/notes")
	String create(NoteForm form, Model model, HttpServletResponse response) {
		try {
			return redirectToNote(notes.create(form.title(), form.categorySlug(), form.categoryName(), form.body()));
		}
		catch (IllegalArgumentException | DuplicateSlugException e) {
			return formError(null, form, e, model, response);
		}
	}

	@PostMapping("/notes/{slug}")
	String update(@PathVariable String slug, NoteForm form, Model model, HttpServletResponse response) {
		try {
			return redirectToNote(notes.update(slug, form.title(), form.categorySlug(), form.categoryName(), form.body()));
		}
		catch (IllegalArgumentException e) {
			return formError(slug, form, e, model, response);
		}
	}

	@PostMapping("/notes/{slug}/delete")
	String delete(@PathVariable String slug) {
		notes.delete(slug);
		return "redirect:/admin";
	}

	@GetMapping("/notes/{slug}/revisions")
	String revisions(@PathVariable String slug, Model model) {
		model.addAttribute("note", notes.get(slug));
		model.addAttribute("revisions", notes.revisions(slug));
		return "admin/revisions";
	}

	@GetMapping("/revisions/{id}")
	String revision(@PathVariable long id, Model model) {
		NoteRevision revision = notes.revision(id);
		Note current = revision.getNote();
		model.addAttribute("revision", revision);
		model.addAttribute("note", current);
		model.addAttribute("diff", LineDiff.of(revision.getBodyMd(), current.getBodyMd()));
		return "admin/revision";
	}

	@PostMapping("/revisions/{id}/restore")
	String restore(@PathVariable long id) {
		return redirectToNote(notes.restore(id));
	}

	@PostMapping("/revisions/{id}/delete")
	String deleteRevision(@PathVariable long id) {
		String slug = notes.revision(id).getNote().getSlug();
		notes.deleteRevision(id);
		return "redirect:/admin/notes/" + UriUtils.encodePathSegment(slug, StandardCharsets.UTF_8) + "/revisions";
	}

	@GetMapping("/comments")
	String comments(Model model) {
		model.addAttribute("comments", comments.recent(100));
		return "admin/comments";
	}

	@PostMapping("/comments/{id}/delete")
	String deleteComment(@PathVariable long id) {
		comments.delete(id, null, true);
		return "redirect:/admin/comments";
	}

	/** 편집기 미리보기. 공개 화면과 같은 렌더러를 써서 결과가 같다. */
	@PostMapping(path = "/preview", consumes = MediaType.TEXT_PLAIN_VALUE, produces = "text/html;charset=UTF-8")
	@ResponseBody
	String preview(@RequestBody(required = false) String md) {
		return markdown.render(md == null ? "" : md);
	}

	@ExceptionHandler(NoteNotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	String notFound() {
		return "error/404";
	}

	private static String redirectToNote(Note note) {
		return "redirect:/notes/" + UriUtils.encodePathSegment(note.getSlug(), StandardCharsets.UTF_8);
	}

	/**
	 * 입력 오류는 리다이렉트하지 않고 같은 폼을 400으로 다시 보여준다. 쓰던 본문은 그대로 둔다. 서비스 트랜잭션이 롤백되면서 영속성 컨텍스트가 비워져 앞서 읽은 사이드바
	 * 목록을 더 읽을 수 없으므로 다시 조회한다.
	 */
	private String formError(String slug, NoteForm form, RuntimeException e, Model model,
			HttpServletResponse response) {
		response.setStatus(HttpStatus.BAD_REQUEST.value());
		model.addAttribute("tree", notes.tree());
		model.addAttribute("slug", slug);
		model.addAttribute("form", form);
		model.addAttribute("error", e.getMessage());
		return "admin/edit";
	}

}
