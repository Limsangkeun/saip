package com.saip.blog.comment;

import com.saip.blog.note.Note;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

interface CommentRepository extends JpaRepository<Comment, Long> {

	List<Comment> findByNoteOrderById(Note note);

	List<Comment> findAllByOrderByIdDesc(Limit limit);

}
