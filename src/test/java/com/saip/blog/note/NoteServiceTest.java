package com.saip.blog.note;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.saip.blog.TestcontainersConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class NoteServiceTest {

	@Autowired
	NoteService notes;

	@Test
	void createMakesCategoryWhenMissing() {
		notes.create("Docker", "deploy", "배포", "# hi");

		Category deploy = notes.tree().stream().filter(c -> c.getSlug().equals("deploy")).findFirst().orElseThrow();
		assertEquals("배포", deploy.getName());
		assertEquals("Docker", deploy.getNotes().get(0).getTitle());
	}

	@Test
	void createWithoutCategoryNameForNewCategoryFails() {
		assertThrows(IllegalArgumentException.class, () -> notes.create("Docker", "deploy", null, "# hi"));
	}

	@Test
	void createRejectsDuplicateSlug() {
		notes.create("Docker", "deploy", "배포", "# hi");
		assertThrows(DuplicateSlugException.class, () -> notes.create("Docker", "deploy", "배포", "# again"));
	}

	@Test
	void updateKeepsRevision() {
		notes.create("Docker", "deploy", "배포", "# hi");

		notes.update("docker", "Docker", "deploy", null, "v2");

		assertEquals("v2", notes.get("docker").getBodyMd());
		List<NoteRevision> revisions = notes.revisions("docker");
		assertEquals(1, revisions.size());
		assertEquals("# hi", revisions.get(0).getBodyMd());
	}

	@Test
	void updateMissingThrows() {
		assertThrows(NoteNotFoundException.class, () -> notes.update("nope", "x", "deploy", "배포", "b"));
	}

	@Test
	void searchTreatsPercentLiterally() {
		notes.create("진행률", "misc", "기타", "100% 완료");
		notes.create("다른 노트", "misc", "기타", "100 완료");

		List<Note> found = notes.search("100%");

		assertEquals(1, found.size());
		assertEquals("진행률", found.get(0).getTitle());
	}

	@Test
	void searchIgnoresCase() {
		notes.create("Docker Compose", "deploy", "배포", "본문");
		assertEquals(1, notes.search("compose").size());
	}

	@Test
	void recentOrdersByUpdatedAt() {
		notes.create("A", "misc", "기타", "a");
		notes.create("B", "misc", "기타", "b");
		notes.update("a", "A", "misc", null, "a2");

		assertEquals("A", notes.recent(10).get(0).getTitle());
	}

}
