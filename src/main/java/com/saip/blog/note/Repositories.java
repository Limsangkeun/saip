package com.saip.blog.note;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface CategoryRepository extends JpaRepository<Category, Long> {

	Optional<Category> findBySlug(String slug);

	List<Category> findAllByOrderByName();

}

interface NoteRepository extends JpaRepository<Note, Long> {

	Optional<Note> findBySlug(String slug);

	boolean existsBySlug(String slug);

	List<Note> findAllByOrderByUpdatedAtDesc(Limit limit);

	@Query("""
			select n from Note n
			where lower(n.title) like :pattern escape '\\' or lower(n.bodyMd) like :pattern escape '\\'
			order by n.updatedAt desc""")
	List<Note> search(String pattern);

}

interface NoteRevisionRepository extends JpaRepository<NoteRevision, Long> {

	List<NoteRevision> findByNoteOrderByIdDesc(Note note);

}
