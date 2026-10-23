package com.saip.blog.sensitive;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** sensitive-patterns.tsv의 패턴으로 텍스트를 줄 단위 검사한다. */
@Component
public class SensitiveScanner {

	private final Map<String, Pattern> patterns = load();

	public List<Finding> scan(String text) {
		List<Finding> findings = new ArrayList<>();
		String[] lines = text.split("\\R", -1);
		for (int i = 0; i < lines.length; i++) {
			for (Map.Entry<String, Pattern> p : patterns.entrySet()) {
				if (p.getValue().matcher(lines[i]).find()) {
					findings.add(new Finding(p.getKey(), i + 1));
				}
			}
		}
		return findings;
	}

	private static Map<String, Pattern> load() {
		Map<String, Pattern> patterns = new LinkedHashMap<>();
		try (var in = SensitiveScanner.class.getResourceAsStream("/sensitive-patterns.tsv");
				var reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
			reader.lines().filter(l -> !l.isBlank() && !l.startsWith("#")).forEach(l -> {
				String[] parts = l.split("\t", 2);
				patterns.put(parts[0], Pattern.compile(parts[1]));
			});
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return patterns;
	}

}
