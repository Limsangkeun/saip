package com.saip.blog.web;

import com.saip.blog.markdown.MarkdownRenderer;
import com.saip.blog.note.Category;
import com.saip.blog.note.Note;
import com.saip.blog.note.NoteNotFoundException;
import com.saip.blog.note.NoteService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

@Controller
class PageController {

	private final NoteService notes;

	private final MarkdownRenderer markdown;

	PageController(NoteService notes, MarkdownRenderer markdown) {
		this.notes = notes;
		this.markdown = markdown;
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
	String note(@PathVariable String slug, Model model) {
		Note note = notes.get(slug);
		model.addAttribute("note", note);
		model.addAttribute("html", markdown.render(note.getBodyMd()));
		return "note";
	}

	@GetMapping("/search")
	String search(@RequestParam(defaultValue = "") String q, Model model) {
		model.addAttribute("q", q);
		model.addAttribute("notes", q.isBlank() ? List.of() : notes.search(q));
		return "search";
	}

	@ExceptionHandler(NoteNotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	String notFound() {
		return "error/404";
	}

}
