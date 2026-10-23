package com.saip.blog.api;

import com.saip.blog.note.DuplicateSlugException;
import com.saip.blog.note.Note;
import com.saip.blog.note.NoteNotFoundException;
import com.saip.blog.note.NoteService;
import com.saip.blog.sensitive.Finding;
import com.saip.blog.sensitive.SensitiveScanner;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Claude Code가 노트를 조회·생성·수정하는 API. 삭제는 일부러 없다. */
@RestController
@RequestMapping("/api/notes")
class NoteApiController {

	private final NoteService notes;

	private final SensitiveScanner scanner;

	private final String baseUrl;

	NoteApiController(NoteService notes, SensitiveScanner scanner,
			@Value("${blog.base-url:http://localhost:8080}") String baseUrl) {
		this.notes = notes;
		this.scanner = scanner;
		this.baseUrl = baseUrl;
	}

	record NoteRequest(@NotBlank String title, @NotBlank String categorySlug, String categoryName,
			@NotBlank String body) {
	}

	record NoteSummary(String slug, String title, String categorySlug, String categoryName, Instant updatedAt) {
	}

	record NoteResponse(String slug, String title, String categorySlug, String categoryName, String body,
			Instant updatedAt, String url) {
	}

	record WriteResult(String slug, String url, String action) {
	}

	@GetMapping
	List<NoteSummary> list() {
		return notes.tree()
			.stream()
			.flatMap(c -> c.getNotes().stream())
			.map(n -> new NoteSummary(n.getSlug(), n.getTitle(), n.getCategory().getSlug(), n.getCategory().getName(),
					n.getUpdatedAt()))
			.toList();
	}

	@GetMapping("/{slug}")
	NoteResponse get(@PathVariable String slug) {
		Note n = notes.get(slug);
		return new NoteResponse(n.getSlug(), n.getTitle(), n.getCategory().getSlug(), n.getCategory().getName(),
				n.getBodyMd(), n.getUpdatedAt(), url(n));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	WriteResult create(@Valid @RequestBody NoteRequest req) {
		rejectSecrets(req);
		Note n = notes.create(req.title(), req.categorySlug(), req.categoryName(), req.body());
		return new WriteResult(n.getSlug(), url(n), "created");
	}

	@PutMapping("/{slug}")
	WriteResult update(@PathVariable String slug, @Valid @RequestBody NoteRequest req) {
		rejectSecrets(req);
		Note n = notes.update(slug, req.title(), req.categorySlug(), req.categoryName(), req.body());
		return new WriteResult(n.getSlug(), url(n), "updated");
	}

	/** 0번 줄은 카테고리(사이드바에 공개된다), 1번 줄은 제목, 본문은 2번 줄부터로 센다. */
	private void rejectSecrets(NoteRequest req) {
		String category = req.categorySlug() + " " + Objects.requireNonNullElse(req.categoryName(), "");
		List<Finding> findings = new ArrayList<>();
		scanner.scan(category).forEach(f -> findings.add(new Finding(f.pattern(), 0)));
		findings.addAll(scanner.scan(req.title() + "\n" + req.body()));
		if (!findings.isEmpty()) {
			throw new SensitiveContentException(findings);
		}
	}

	private String url(Note n) {
		return baseUrl + "/notes/" + n.getSlug();
	}

	static class SensitiveContentException extends RuntimeException {

		final List<Finding> findings;

		SensitiveContentException(List<Finding> findings) {
			this.findings = findings;
		}

	}

	@ExceptionHandler(SensitiveContentException.class)
	ResponseEntity<Map<String, List<Finding>>> sensitive(SensitiveContentException e) {
		return ResponseEntity.unprocessableContent().body(Map.of("findings", e.findings));
	}

	@ExceptionHandler(NoteNotFoundException.class)
	ResponseEntity<Map<String, String>> notFound(NoteNotFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
	}

	@ExceptionHandler(DuplicateSlugException.class)
	ResponseEntity<Map<String, String>> duplicate(DuplicateSlugException e) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
		return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
	}

}
