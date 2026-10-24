package com.saip.blog.image;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 이미지 업로드. 편집기(/admin/images, 관리자 + CSRF)와 /blog 스킬(/api/images, API 토큰) 두 경로가 같은 저장소를 쓴다. 저장된 파일은 /images/**로 영구 캐시와 함께
 * 제공한다(파일 이름이 내용 해시라 내용이 바뀌지 않는다).
 */
@RestController
class ImageController implements WebMvcConfigurer {

	private final ImageStore store;

	ImageController(ImageStore store) {
		this.store = store;
	}

	record Uploaded(String path, String markdown) {
	}

	@PostMapping({ "/admin/images", "/api/images" })
	ResponseEntity<Uploaded> upload(@RequestParam MultipartFile file) throws IOException {
		String path = store.store(file.getBytes());
		return ResponseEntity.status(HttpStatus.CREATED).body(new Uploaded(path, "![](" + path + ")"));
	}

	@ExceptionHandler(InvalidImageException.class)
	ResponseEntity<Map<String, String>> invalid(InvalidImageException e) {
		return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
	}

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler("/images/**")
			.addResourceLocations(store.dir().toUri().toString())
			.setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
	}

}
