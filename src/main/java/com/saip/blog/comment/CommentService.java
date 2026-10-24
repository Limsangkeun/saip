package com.saip.blog.comment;

import com.saip.blog.note.Note;
import com.saip.blog.note.NoteNotFoundException;
import com.saip.blog.note.NoteService;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 댓글 규칙: 로그인 사용자는 작성, 본인만 수정, 본인과 관리자만 삭제. 본인 여부는 Google sub로 판단한다. */
@Service
@Transactional(readOnly = true)
public class CommentService {

	static final int MAX_LENGTH = 2000;

	private final CommentRepository comments;

	private final NoteService notes;

	CommentService(CommentRepository comments, NoteService notes) {
		this.comments = comments;
		this.notes = notes;
	}

	@Transactional
	public Comment add(String noteSlug, String authorSub, String authorName, String body) {
		return comments.save(new Comment(notes.get(noteSlug), authorSub, authorName, clean(body)));
	}

	@Transactional
	public Comment edit(long id, String actorSub, String body) {
		Comment comment = get(id);
		if (!comment.getAuthorSub().equals(actorSub)) {
			throw new AccessDeniedException("본인 댓글만 수정할 수 있습니다");
		}
		comment.edit(clean(body));
		return comment;
	}

	@Transactional
	public void delete(long id, String actorSub, boolean admin) {
		Comment comment = get(id);
		if (!admin && !comment.getAuthorSub().equals(actorSub)) {
			throw new AccessDeniedException("본인 댓글만 삭제할 수 있습니다");
		}
		comments.delete(comment);
	}

	public List<Comment> forNote(Note note) {
		return comments.findByNoteOrderById(note);
	}

	public List<Comment> recent(int limit) {
		return comments.findAllByOrderByIdDesc(Limit.of(limit));
	}

	private Comment get(long id) {
		return comments.findById(id).orElseThrow(() -> new NoteNotFoundException("comment " + id));
	}

	private static String clean(String body) {
		String text = body == null ? "" : body.strip();
		if (text.isEmpty() || text.length() > MAX_LENGTH) {
			throw new IllegalArgumentException("댓글은 1~2000자로 써 주세요");
		}
		return text;
	}

}
