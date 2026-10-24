package com.saip.blog.image;

import static org.assertj.core.api.Assertions.assertThat;

import com.saip.blog.TestcontainersConfiguration;
import com.saip.blog.note.NoteService;
import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class ImageCleanupTest {

	static final String NAME = "0123456789abcdef0123456789abcdef.png";

	@TempDir
	Path dir;

	@Autowired
	NoteService notes;

	@Autowired
	EntityManager em;

	@Autowired
	JdbcTemplate jdbc;

	ImageCleanup cleanup;

	Instant now = Instant.parse("2026-10-24T00:00:00Z");

	@BeforeEach
	void setUp() {
		cleanup = new ImageCleanup(new ImageStore(dir.toString()), jdbc);
	}

	@Test
	void deletesOldUnreferencedImage() throws IOException {
		Path file = image(NAME, Duration.ofDays(2));

		assertThat(cleanup.deleteUnused(now)).isEqualTo(1);
		assertThat(file).doesNotExist();
	}

	@Test
	void keepsImageUsedInNote() throws IOException {
		Path file = image(NAME, Duration.ofDays(2));
		notes.create("Docker", "deploy", "배포", "![](/images/" + NAME + ")");
		em.flush();

		assertThat(cleanup.deleteUnused(now)).isZero();
		assertThat(file).exists();
	}

	@Test
	void keepsImageUsedOnlyInOldRevision() throws IOException {
		Path file = image(NAME, Duration.ofDays(2));
		notes.create("Docker", "deploy", "배포", "![](/images/" + NAME + ")");
		notes.update("docker", "Docker", "deploy", null, "이미지를 뺀 본문");
		em.flush();

		assertThat(cleanup.deleteUnused(now)).isZero();
		assertThat(file).exists();
	}

	@Test
	void keepsFreshUpload() throws IOException {
		Path file = image(NAME, Duration.ofHours(1));

		assertThat(cleanup.deleteUnused(now)).isZero();
		assertThat(file).exists();
	}

	@Test
	void ignoresFilesNotNamedByHash() throws IOException {
		Path file = image("notes.txt", Duration.ofDays(2));

		assertThat(cleanup.deleteUnused(now)).isZero();
		assertThat(file).exists();
	}

	private Path image(String name, Duration age) throws IOException {
		Path file = Files.write(dir.resolve(name), new byte[] { 1 });
		Files.setLastModifiedTime(file, FileTime.from(now.minus(age)));
		return file;
	}

}
