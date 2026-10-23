package com.saip.blog.admin;

import static com.saip.blog.admin.AdminAccessTest.admin;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saip.blog.TestcontainersConfiguration;
import com.saip.blog.note.NoteService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 테스트 전체를 트랜잭션으로 감싸지 않는다. 실제 요청처럼 서비스의 트랜잭션이 롤백되어야 재현되는 문제(롤백 뒤 사이드바 지연 로딩 실패)를 잡기 위해서다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AdminFormErrorTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	NoteService notes;

	@AfterEach
	void cleanUp() {
		notes.delete("form-error-existing");
	}

	@Test
	void formErrorAfterRollbackStillRendersSidebar() throws Exception {
		notes.create("Form Error Existing", "form-error-cat", "폼오류주제", "본문");

		mvc.perform(post("/admin/notes").with(admin())
			.with(csrf())
			.param("title", "New One")
			.param("categorySlug", "brand-new-cat")
			.param("categoryName", "")
			.param("body", "쓰던 본문"))
			.andExpect(status().isBadRequest())
			.andExpect(content().string(containsString("새 카테고리에는 이름이 필요합니다")))
			.andExpect(content().string(containsString("Form Error Existing")))
			.andExpect(content().string(containsString("쓰던 본문")));
	}

}
