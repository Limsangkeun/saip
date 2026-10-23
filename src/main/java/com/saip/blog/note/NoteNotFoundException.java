package com.saip.blog.note;

public class NoteNotFoundException extends RuntimeException {

	public NoteNotFoundException(String slug) {
		super("노트가 없습니다: " + slug);
	}

}
