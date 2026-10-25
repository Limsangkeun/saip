package com.saip.blog.markdown;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.commonmark.Extension;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.gfm.alerts.AlertsExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.heading.anchor.HeadingAnchorExtension;
import org.commonmark.ext.task.list.items.TaskListItemsExtension;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Node;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
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

	private static final Pattern LEFTOVER_BOLD = Pattern.compile("\\*\\*(?=\\S)(.+?)(?<=\\S)\\*\\*");

	public String render(String md) {
		return renderer.render(parse(md));
	}

	/**
	 * CommonMark는 닫는 **의 앞이 문장부호면 뒤에 공백이나 문장부호가 와야 닫힌 것으로 본다. 그래서
	 * "**머신러닝(기계 학습)**이다"처럼 조사를 붙여 쓰면 **가 글자로 남는다. 파싱 뒤 글자로 남은 **…**를
	 * 굵은 글씨로 바꾼다. 코드는 Text 노드가 아니므로 건드리지 않는다.
	 */
	private Node parse(String md) {
		Node doc = parser.parse(md);
		List<Text> texts = new ArrayList<>();
		doc.accept(new AbstractVisitor() {
			@Override
			public void visit(Text text) {
				if (text.getLiteral().contains("**")) {
					texts.add(text);
				}
			}
		});
		texts.forEach(MarkdownRenderer::fixBold);
		return doc;
	}

	private static void fixBold(Text text) {
		String s = text.getLiteral();
		Matcher m = LEFTOVER_BOLD.matcher(s);
		int last = 0;
		while (m.find()) {
			text.insertBefore(new Text(s.substring(last, m.start())));
			StrongEmphasis strong = new StrongEmphasis("**");
			strong.appendChild(new Text(m.group(1)));
			text.insertBefore(strong);
			last = m.end();
		}
		if (last > 0) {
			text.setLiteral(s.substring(last));
		}
	}

	/** 검색 결과·공유 미리보기에 쓸 요약. 마크다운 문법을 벗긴 평문을 한 줄로 이어 max자까지 자른다. */
	public String summary(String md, int max) {
		String plain = text.render(parse(md)).replaceAll("\\s+", " ").strip();
		return plain.length() <= max ? plain : plain.substring(0, max - 1) + "…";
	}

}
