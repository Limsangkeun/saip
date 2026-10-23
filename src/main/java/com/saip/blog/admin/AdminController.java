package com.saip.blog.admin;

import com.saip.blog.markdown.MarkdownRenderer;
import com.saip.blog.note.Category;
import com.saip.blog.note.DuplicateSlugException;
import com.saip.blog.note.Note;
import com.saip.blog.note.NoteNotFoundException;
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

	AdminController(NoteService notes, MarkdownRenderer markdown) {
		this.notes = notes;
		this.markdown = markdown;
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

	/** 입력 오류는 리다이렉트하지 않고 같은 폼을 400으로 다시 보여준다. 쓰던 본문은 그대로 둔다. */
	private static String formError(String slug, NoteForm form, RuntimeException e, Model model,
			HttpServletResponse response) {
		response.setStatus(HttpStatus.BAD_REQUEST.value());
		model.addAttribute("slug", slug);
		model.addAttribute("form", form);
		model.addAttribute("error", e.getMessage());
		return "admin/edit";
	}

}
