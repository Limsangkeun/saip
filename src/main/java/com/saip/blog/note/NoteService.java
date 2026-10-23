package com.saip.blog.note;

import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class NoteService {

	private final CategoryRepository categories;

	private final NoteRepository notes;

	private final NoteRevisionRepository revisions;

	NoteService(CategoryRepository categories, NoteRepository notes, NoteRevisionRepository revisions) {
		this.categories = categories;
		this.notes = notes;
		this.revisions = revisions;
	}

	@Transactional
	public Note create(String title, String categorySlug, String categoryName, String bodyMd) {
		String slug = Slugs.of(title);
		if (notes.existsBySlug(slug)) {
			throw new DuplicateSlugException(slug);
		}
		Category category = categoryFor(categorySlug, categoryName);
		Note note = notes.save(new Note(category, slug, title, bodyMd));
		category.getNotes().add(note);
		return note;
	}

	/** 수정 전 제목과 본문을 버전으로 남긴 뒤 갱신한다. slug는 바뀌지 않는다. */
	@Transactional
	public Note update(String slug, String title, String categorySlug, String categoryName, String bodyMd) {
		Note note = get(slug);
		revisions.save(new NoteRevision(note));
		note.change(categoryFor(categorySlug, categoryName), title, bodyMd);
		return note;
	}

	public Note get(String slug) {
		return notes.findBySlug(slug).orElseThrow(() -> new NoteNotFoundException(slug));
	}

	public List<Category> tree() {
		return categories.findAllByOrderByName();
	}

	public List<Note> recent(int limit) {
		return notes.findAllByOrderByUpdatedAtDesc(Limit.of(limit));
	}

	public List<Note> search(String q) {
		String escaped = q.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
		return notes.search("%" + escaped + "%");
	}

	public List<NoteRevision> revisions(String slug) {
		return revisions.findByNoteOrderByIdDesc(get(slug));
	}

	private Category categoryFor(String slug, String name) {
		return categories.findBySlug(slug).orElseGet(() -> {
			if (name == null || name.isBlank()) {
				throw new IllegalArgumentException("새 카테고리에는 이름이 필요합니다: " + slug);
			}
			return categories.save(new Category(slug, name));
		});
	}

}
