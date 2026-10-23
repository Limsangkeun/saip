package com.saip.blog.note;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
public class Category {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String slug;

	private String name;

	private Instant createdAt;

	@OneToMany(mappedBy = "category")
	@OrderBy("title")
	private List<Note> notes = new ArrayList<>();

	protected Category() {
	}

	Category(String slug, String name) {
		this.slug = slug;
		this.name = name;
		this.createdAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public String getSlug() {
		return slug;
	}

	public String getName() {
		return name;
	}

	public List<Note> getNotes() {
		return notes;
	}

}
