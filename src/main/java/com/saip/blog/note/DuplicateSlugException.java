package com.saip.blog.note;

public class DuplicateSlugException extends RuntimeException {

	public DuplicateSlugException(String slug) {
		super("이미 있는 노트입니다: " + slug);
	}

}
