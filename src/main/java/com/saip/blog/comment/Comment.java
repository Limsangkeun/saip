package com.saip.blog.comment;

import com.saip.blog.note.Note;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import java.time.Instant;

/** 노트에 달린 평문 댓글. 화면에는 th:text로만 출력한다(저장형 XSS 방지). */
@Entity
public class Comment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(optional = false)
	private Note note;

	private String authorSub;

	private String authorName;

	private String body;

	private Instant createdAt;

	private Instant updatedAt;

	protected Comment() {
	}

	Comment(Note note, String authorSub, String authorName, String body) {
		this.note = note;
		this.authorSub = authorSub;
		this.authorName = authorName;
		this.body = body;
		this.createdAt = Instant.now();
		this.updatedAt = this.createdAt;
	}

	void edit(String body) {
		this.body = body;
		this.updatedAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public Note getNote() {
		return note;
	}

	public String getAuthorSub() {
		return authorSub;
	}

	public String getAuthorName() {
		return authorName;
	}

	public String getBody() {
		return body;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
