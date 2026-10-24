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

	/**
	 * 노트를 지운다. 버전과 댓글은 DB의 on delete cascade가 함께 지운다. 메모리에 남은 버전·댓글 객체가 지워진 노트를 가리키지 않도록 쿼리 한 번으로 지우고 영속성
	 * 컨텍스트를 비운다(NoteRepository.deleteBySlug).
	 */
	@Transactional
	public void delete(String slug) {
		if (notes.deleteBySlug(slug) == 0) {
			throw new NoteNotFoundException(slug);
		}
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

	public NoteRevision revision(long id) {
		return revisions.findById(id).orElseThrow(() -> new NoteNotFoundException("revision " + id));
	}

	/** 예전 버전의 제목과 본문으로 되돌린다. 현재 내용은 새 버전으로 남겨서 복원도 되돌릴 수 있다. 주제는 그대로 둔다. */
	@Transactional
	public Note restore(long revisionId) {
		NoteRevision old = revision(revisionId);
		Note note = old.getNote();
		revisions.save(new NoteRevision(note));
		note.change(note.getCategory(), old.getTitle(), old.getBodyMd());
		return note;
	}

	/** 버전 하나를 지운다. 실수로 들어간 비밀값을 이력에서 없애는 용도. */
	@Transactional
	public void deleteRevision(long revisionId) {
		revisions.delete(revision(revisionId));
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
