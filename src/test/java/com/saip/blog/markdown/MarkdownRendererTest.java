package com.saip.blog.markdown;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MarkdownRendererTest {

	MarkdownRenderer renderer = new MarkdownRenderer();

	@Test
	void table() {
		assertThat(renderer.render("|a|b|\n|-|-|\n|1|2|")).contains("<table>");
	}

	@Test
	void taskList() {
		assertThat(renderer.render("- [x] done")).contains("type=\"checkbox\"").contains("checked");
	}

	@Test
	void alert() {
		assertThat(renderer.render("> [!TIP]\n> 팁")).contains("markdown-alert-tip");
	}

	@Test
	void headingId() {
		assertThat(renderer.render("## 설치 방법")).contains("id=\"설치-방법\"");
	}

	@Test
	void codeLang() {
		assertThat(renderer.render("```java\nint a;\n```")).contains("class=\"language-java\"");
	}

	@Test
	void mermaidKeptAsCode() {
		assertThat(renderer.render("```mermaid\ngraph TD\n```")).contains("class=\"language-mermaid\"");
	}

	@Test
	void escapesRawHtml() {
		assertThat(renderer.render("<script>alert(1)</script>"))
			.doesNotContain("<script>")
			.contains("&lt;script&gt;");
	}

}
