package com.saip.blog.sensitive;

/** 걸린 패턴 이름과 줄 번호(1부터). 비밀값 자체는 담지 않는다. */
public record Finding(String pattern, int line) {
}
