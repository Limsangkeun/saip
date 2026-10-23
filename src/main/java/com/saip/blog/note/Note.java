package com.saip.blog.note;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import java.time.Instant;

@Entity
public class Note {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(optional = false)
	private Category category;

	private String slug;

	private String title;

	private String bodyMd;

	private Instant createdAt;

	private Instant updatedAt;

	protected Note() {
	}

	Note(Category category, String slug, String title, String bodyMd) {
		this.category = category;
		this.slug = slug;
		this.title = title;
		this.bodyMd = bodyMd;
		this.createdAt = Instant.now();
		this.updatedAt = this.createdAt;
	}

	void change(Category category, String title, String bodyMd) {
		this.category = category;
		this.title = title;
		this.bodyMd = bodyMd;
		this.updatedAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public Category getCategory() {
		return category;
	}

	public String getSlug() {
		return slug;
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

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
