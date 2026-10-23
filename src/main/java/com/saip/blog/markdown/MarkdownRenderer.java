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
import org.springframework.stereotype.Component;

/** 노트 마크다운을 HTML로 바꾼다. 원본 HTML은 실행되지 않도록 이스케이프한다. */
@Component
public class MarkdownRenderer {

	private static final List<Extension> EXTENSIONS = List.of(TablesExtension.create(),
			StrikethroughExtension.create(), TaskListItemsExtension.create(), AutolinkExtension.create(),
			HeadingAnchorExtension.create(), AlertsExtension.create());

	private final Parser parser = Parser.builder().extensions(EXTENSIONS).build();

	private final HtmlRenderer renderer = HtmlRenderer.builder().extensions(EXTENSIONS).escapeHtml(true).build();

	public String render(String md) {
		return renderer.render(parser.parse(md));
	}

}
