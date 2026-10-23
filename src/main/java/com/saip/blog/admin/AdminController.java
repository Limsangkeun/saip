package com.saip.blog.admin;

import com.saip.blog.note.Category;
import com.saip.blog.note.NoteService;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;

/** 관리자 화면. 접근 권한(ROLE_ADMIN)은 SecurityConfig가 /admin/**에 건다. */
@Controller
@RequestMapping("/admin")
class AdminController {

	private final NoteService notes;

	AdminController(NoteService notes) {
		this.notes = notes;
	}

	@ModelAttribute("tree")
	List<Category> tree() {
		return notes.tree();
	}

	@GetMapping
	String index() {
		return "admin/index";
	}

}
