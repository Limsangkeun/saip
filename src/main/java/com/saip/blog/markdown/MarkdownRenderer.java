package com.saip.blog.markdown;

import java.util.List;
import org.commonmark.Extension;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.gfm.alerts.AlertsExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.heading.anchor.HeadingAnchorExtension;
import org.commonmark.ext.task.list.items.TaskListItemsExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.renderer.text.TextContentRenderer;
import org.springframework.stereotype.Component;

/** 노트 마크다운을 HTML로 바꾼다. 원본 HTML은 이스케이프하고, 링크는 http/https/mailto 등만 허용한다. */
@Component
public class MarkdownRenderer {

	private static final List<Extension> EXTENSIONS = List.of(TablesExtension.create(),
			StrikethroughExtension.create(), TaskListItemsExtension.create(), AutolinkExtension.create(),
			HeadingAnchorExtension.create(), AlertsExtension.create());

	private final Parser parser = Parser.builder().extensions(EXTENSIONS).build();

	private final HtmlRenderer renderer = HtmlRenderer.builder().extensions(EXTENSIONS).escapeHtml(true)
		.sanitizeUrls(true)
		.build();

	private final TextContentRenderer text = TextContentRenderer.builder().extensions(EXTENSIONS).build();

	public String render(String md) {
		return renderer.render(parser.parse(md));
	}

	/** 검색 결과·공유 미리보기에 쓸 요약. 마크다운 문법을 벗긴 평문을 한 줄로 이어 max자까지 자른다. */
	public String summary(String md, int max) {
		String plain = text.render(parser.parse(md)).replaceAll("\\s+", " ").strip();
		return plain.length() <= max ? plain : plain.substring(0, max - 1) + "…";
	}

}
