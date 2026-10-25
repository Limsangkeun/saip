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
	void boldBeforeKoreanParticle() {
		assertThat(renderer.render("**머신러닝(기계 학습)**이다. **\"서울\"**은"))
			.contains("<strong>머신러닝(기계 학습)</strong>이다.")
			.contains("<strong>&quot;서울&quot;</strong>은")
			.doesNotContain("**");
	}

	@Test
	void boldFixLeavesCodeAlone() {
		assertThat(renderer.render("`**a(b)**c`\n\n```\n**a(b)**c\n```")).doesNotContain("<strong>");
	}

	@Test
	void boldFixInSummary() {
		assertThat(renderer.summary("**어텐션(Attention)**으로", 100)).isEqualTo("어텐션(Attention)으로");
	}

	@Test
	void blocksJavascriptLinks() {
		assertThat(renderer.render("[x](javascript:alert(1))")).doesNotContain("javascript:");
	}

	@Test
	void escapesRawHtml() {
		assertThat(renderer.render("<script>alert(1)</script>"))
			.doesNotContain("<script>")
			.contains("&lt;script&gt;");
	}

}
