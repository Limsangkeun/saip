package com.saip.blog.note;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import java.time.Instant;

/** 노트가 수정되기 직전의 제목과 본문. */
@Entity
public class NoteRevision {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(optional = false)
	private Note note;

	private String title;

	private String bodyMd;

	private Instant createdAt;

	protected NoteRevision() {
	}

	NoteRevision(Note note) {
		this.note = note;
		this.title = note.getTitle();
		this.bodyMd = note.getBodyMd();
		this.createdAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public Note getNote() {
		return note;
	}

	public String getTitle() {
		return title;
	}

	public String getBodyMd() {
		return bodyMd;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
