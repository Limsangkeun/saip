package com.saip.blog.image;

import static com.saip.blog.admin.AdminAccessTest.admin;
import static com.saip.blog.admin.AdminAccessTest.user;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.saip.blog.TestcontainersConfiguration;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = { "blog.api-token=test-token", "blog.image-dir=build/test-images" })
class ImageUploadTest {

	@Autowired
	MockMvc mvc;

	static MockMultipartFile png() throws Exception {
		return new MockMultipartFile("file", "a.png", "image/png", ImageStoreTest.png(40, 30));
	}

	@Test
	void adminUploads() throws Exception {
		mvc.perform(multipart("/admin/images").file(png()).with(admin()).with(csrf()))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.path", startsWith("/images/")))
			.andExpect(jsonPath("$.markdown", startsWith("![](/images/")));
	}

	@Test
	void apiTokenUploads() throws Exception {
		mvc.perform(multipart("/api/images").file(png()).header("Authorization", "Bearer test-token"))
			.andExpect(status().isCreated());
	}

	@Test
	void anonymousCannotUpload() throws Exception {
		mvc.perform(multipart("/admin/images").file(png()).with(csrf()))
			.andExpect(redirectedUrl("/oauth2/authorization/google"));
		mvc.perform(multipart("/api/images").file(png())).andExpect(status().isUnauthorized());
	}

	@Test
	void userCannotUpload() throws Exception {
		mvc.perform(multipart("/admin/images").file(png()).with(user()).with(csrf())).andExpect(status().isForbidden());
	}

	@Test
	void uploadWithoutCsrfIs403() throws Exception {
		mvc.perform(multipart("/admin/images").file(png()).with(admin())).andExpect(status().isForbidden());
	}

	@Test
	void fakePngIs400() throws Exception {
		MockMultipartFile fake = new MockMultipartFile("file", "a.png", "image/png",
				"not an image".getBytes(StandardCharsets.UTF_8));

		mvc.perform(multipart("/admin/images").file(fake).with(admin()).with(csrf()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error", containsString("이미지")));
	}

	@Test
	void uploadedImageIsServed() throws Exception {
		String body = mvc.perform(multipart("/admin/images").file(png()).with(admin()).with(csrf()))
			.andReturn()
			.getResponse()
			.getContentAsString();
		String path = JsonPath.read(body, "$.path");

		mvc.perform(get(path))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Type", "image/png"))
			.andExpect(header().string("Cache-Control", containsString("immutable")));
	}

	@Test
	void missingImageIs404() throws Exception {
		mvc.perform(get("/images/00000000000000000000000000000000.png")).andExpect(status().isNotFound());
	}

}
