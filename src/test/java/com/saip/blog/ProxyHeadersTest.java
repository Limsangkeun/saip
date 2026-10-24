package com.saip.blog;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** 배포하면 Caddy가 https를 받고 앱에는 http로 넘긴다. 앱이 원래 주소(https)를 알아야 하고, 화면에는 CSP가 붙어야 한다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ProxyHeadersTest {

	@Autowired
	MockMvc mvc;

	@Test
	void oauthRedirectUsesForwardedHttps() throws Exception {
		mvc.perform(get("/oauth2/authorization/google").header("X-Forwarded-Proto", "https")
			.header("X-Forwarded-Host", "blog.example.com"))
			.andExpect(header().string("Location",
					containsString("redirect_uri=https://blog.example.com/login/oauth2/code/google")));
	}

	@Test
	void pagesSendContentSecurityPolicy() throws Exception {
		mvc.perform(get("/"))
			.andExpect(header().string("Content-Security-Policy",
					allOf(containsString("default-src 'self'"),
							containsString("script-src 'self' https://cdnjs.cloudflare.com https://cdn.jsdelivr.net;"),
							containsString("object-src 'none'"), containsString("frame-ancestors 'none'"))));
	}

	@Test
	void layoutHasNoInlineHandlers() throws Exception {
		mvc.perform(get("/")).andExpect(content().string(not(containsString("onclick="))));
	}

}
