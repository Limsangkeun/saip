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

	@Autowired
	NoteRevisionRepository revisionRepository;

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
	void searchTreatsUnderscoreLiterally() {
		notes.create("밑줄", "misc", "기타", "a_b");
		notes.create("다른 노트", "misc", "기타", "axb");

		List<Note> found = notes.search("a_b");

		assertEquals(1, found.size());
		assertEquals("밑줄", found.get(0).getTitle());
	}

	@Test
	void deleteRemovesNoteAndRevisions() {
		notes.create("Docker", "deploy", "배포", "# hi");
		notes.update("docker", "Docker", "deploy", null, "v2");

		notes.delete("docker");

		assertThrows(NoteNotFoundException.class, () -> notes.get("docker"));
		assertEquals(0, revisionRepository.count());
	}

	@Test
	void restoreRecordsCurrentAsRevision() {
		notes.create("Docker", "deploy", "배포", "v1");
		notes.update("docker", "Docker 2", "deploy", null, "v2");
		long v1 = notes.revisions("docker").get(0).getId();

		notes.restore(v1);

		Note note = notes.get("docker");
		assertEquals("v1", note.getBodyMd());
		assertEquals("Docker", note.getTitle());
		List<NoteRevision> revisions = notes.revisions("docker");
		assertEquals(2, revisions.size());
		assertEquals("v2", revisions.get(0).getBodyMd());
	}

	@Test
	void restoreKeepsCategory() {
		notes.create("Docker", "deploy", "배포", "v1");
		notes.update("docker", "Docker", "ops", "운영", "v2");
		long v1 = notes.revisions("docker").get(0).getId();

		notes.restore(v1);

		assertEquals("ops", notes.get("docker").getCategory().getSlug());
	}

	@Test
	void deleteRevision() {
		notes.create("Docker", "deploy", "배포", "v1");
		notes.update("docker", "Docker", "deploy", null, "v2");
		long v1 = notes.revisions("docker").get(0).getId();

		notes.deleteRevision(v1);

		assertEquals(0, notes.revisions("docker").size());
		assertEquals("v2", notes.get("docker").getBodyMd());
	}

	@Test
	void missingRevisionThrows() {
		assertThrows(NoteNotFoundException.class, () -> notes.revision(999_999L));
		assertThrows(NoteNotFoundException.class, () -> notes.restore(999_999L));
		assertThrows(NoteNotFoundException.class, () -> notes.deleteRevision(999_999L));
	}

	@Test
	void deleteMissingThrows() {
		assertThrows(NoteNotFoundException.class, () -> notes.delete("nope"));
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
