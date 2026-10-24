package com.saip.blog.image;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 아무 노트도, 어떤 버전 기록도 쓰지 않는 이미지를 지운다. 파일 이름이 내용 해시라 여러 노트가 한 파일을 함께 쓸 수 있어서 노트를 지울 때 바로 지우지 않고 여기서
 * 따로 찾는다. 붙여넣고 아직 저장하지 않은 이미지를 지우지 않도록 하루를 기다린다.
 */
@Component
public class ImageCleanup {

	private static final Logger log = LoggerFactory.getLogger(ImageCleanup.class);

	static final Duration GRACE = Duration.ofDays(1);

	/** ImageStore가 만든 파일만. 이름에 LIKE 특수문자(%, _)가 없어서 아래 쿼리에 그대로 넣어도 된다. */
	private static final Pattern OURS = Pattern.compile("[0-9a-f]{32}\\.(png|jpg|gif)");

	private final ImageStore store;

	private final JdbcTemplate jdbc;

	public ImageCleanup(ImageStore store, JdbcTemplate jdbc) {
		this.store = store;
		this.jdbc = jdbc;
	}

	@Scheduled(cron = "0 30 4 * * *", zone = "Asia/Seoul")
	void daily() {
		deleteUnused(Instant.now());
	}

	/** 지운 파일 수를 돌려준다. */
	public int deleteUnused(Instant now) {
		if (!Files.isDirectory(store.dir())) {
			return 0;
		}
		int deleted = 0;
		for (Path file : candidates(now.minus(GRACE))) {
			if (!referenced(file.getFileName().toString())) {
				try {
					Files.deleteIfExists(file);
					deleted++;
					log.info("안 쓰는 이미지 삭제: {}", file.getFileName());
				}
				catch (IOException e) {
					log.warn("이미지 삭제 실패: {}", file.getFileName(), e);
				}
			}
		}
		return deleted;
	}

	private List<Path> candidates(Instant olderThan) {
		try (Stream<Path> files = Files.list(store.dir())) {
			return files.filter(f -> OURS.matcher(f.getFileName().toString()).matches())
				.filter(f -> modified(f).isBefore(olderThan))
				.toList();
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static Instant modified(Path file) {
		try {
			return Files.getLastModifiedTime(file).toInstant();
		}
		catch (IOException e) {
			return Instant.MAX; // 시각을 모르면 지우지 않는다
		}
	}

	private boolean referenced(String name) {
		String pattern = "%/images/" + name + "%";
		return Boolean.TRUE.equals(jdbc.queryForObject(
				"select exists(select 1 from note where body_md like ?) or exists(select 1 from note_revision where body_md like ?)",
				Boolean.class, pattern, pattern));
	}

}
