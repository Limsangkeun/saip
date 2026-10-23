package com.saip.blog.api;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saip.blog.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "blog.api-token=test-token")
@Transactional
class NoteApiControllerTest {

	static final String DOCKER = """
			{"title":"Docker","categorySlug":"deploy","categoryName":"배포","body":"# hi"}""";

	@Autowired
	MockMvc mvc;

	@Test
	void rejectsWithoutToken() throws Exception {
		mvc.perform(get("/api/notes")).andExpect(status().isUnauthorized());
	}

	@Test
	void rejectsWrongToken() throws Exception {
		mvc.perform(get("/api/notes").header("Authorization", "Bearer nope")).andExpect(status().isUnauthorized());
	}

	@Test
	void createThenGet() throws Exception {
		create(DOCKER).andExpect(status().isCreated())
			.andExpect(jsonPath("$.action").value("created"))
			.andExpect(jsonPath("$.slug").value("docker"))
			.andExpect(jsonPath("$.url").value("http://localhost:8080/notes/docker"));

		mvc.perform(get("/api/notes/docker").header("Authorization", "Bearer test-token"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.body").value("# hi"))
			.andExpect(jsonPath("$.categoryName").value("배포"));
	}

	@Test
	void listsNotes() throws Exception {
		create(DOCKER);

		mvc.perform(get("/api/notes").header("Authorization", "Bearer test-token"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].slug").value("docker"))
			.andExpect(jsonPath("$[0].categorySlug").value("deploy"));
	}

	@Test
	void duplicateIs409() throws Exception {
		create(DOCKER);
		create(DOCKER).andExpect(status().isConflict());
	}

	@Test
	void missingFieldIs400() throws Exception {
		create("""
				{"title":"Docker","categorySlug":"deploy"}""").andExpect(status().isBadRequest());
	}

	@Test
	void newCategoryWithoutNameIs400() throws Exception {
		create("""
				{"title":"Docker","categorySlug":"deploy","body":"x"}""").andExpect(status().isBadRequest());
	}

	@Test
	void updateWorks() throws Exception {
		create(DOCKER);

		update("docker", """
				{"title":"Docker","categorySlug":"deploy","body":"v2"}""")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.action").value("updated"));
	}

	@Test
	void updateMissingIs404() throws Exception {
		update("nope", DOCKER).andExpect(status().isNotFound());
	}

	@Test
	void secretInBodyIs422() throws Exception {
		create("""
				{"title":"Docker","categorySlug":"deploy","categoryName":"배포","body":"a\\nkey AKIAABCDEFGHIJKLMNOP"}""")
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.findings[0].pattern").value("aws_access_key"))
			.andExpect(jsonPath("$.findings[0].line").value(3))
			.andExpect(content().string(not(containsString("AKIA"))));
	}

	@Test
	void secretInTitleIs422() throws Exception {
		create("""
				{"title":"password=hunter22","categorySlug":"deploy","categoryName":"배포","body":"x"}""")
			.andExpect(status().isUnprocessableContent());
	}

	@Test
	void secretOnUpdateIs422() throws Exception {
		create(DOCKER);
		update("docker", """
				{"title":"Docker","categorySlug":"deploy","body":"password=hunter22"}""")
			.andExpect(status().isUnprocessableContent());
	}

	@Test
	void secretInCategoryIs422OnLineZero() throws Exception {
		create("""
				{"title":"Docker","categorySlug":"deploy","categoryName":"password=hunter22","body":"x"}""")
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.findings[0].line").value(0));
	}

	@Test
	void publicPagesStillOpen() throws Exception {
		mvc.perform(get("/")).andExpect(status().isOk());
	}

	private ResultActions create(String json) throws Exception {
		return mvc.perform(post("/api/notes").header("Authorization", "Bearer test-token")
			.contentType(MediaType.APPLICATION_JSON)
			.content(json));
	}

	private ResultActions update(String slug, String json) throws Exception {
		return mvc.perform(put("/api/notes/{slug}", slug).header("Authorization", "Bearer test-token")
			.contentType(MediaType.APPLICATION_JSON)
			.content(json));
	}

}
