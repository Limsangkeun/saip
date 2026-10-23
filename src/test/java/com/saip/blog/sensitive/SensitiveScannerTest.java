package com.saip.blog.sensitive;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SensitiveScannerTest {

	SensitiveScanner scanner = new SensitiveScanner();

	@Test
	void findsAwsKeyWithLine() {
		assertThat(scanner.scan("a\nkey AKIAABCDEFGHIJKLMNOP"))
			.containsExactly(new Finding("aws_access_key", 2));
	}

	@Test
	void findsAnthropicKey() {
		assertThat(patterns(scanner.scan("ANTHROPIC_API_KEY=sk-ant-api03-abcdefghijklmnopqrstuvwxyz")))
			.contains("anthropic_key");
	}

	@Test
	void findsGithubToken() {
		assertThat(patterns(scanner.scan("ghp_abcdefghijklmnopqrstuvwxyz0123456789")))
			.containsExactly("github_token");
	}

	@Test
	void findsPrivateKeyHeader() {
		assertThat(patterns(scanner.scan("-----BEGIN RSA PRIVATE KEY-----"))).containsExactly("private_key");
	}

	@Test
	void findsPasswordAssign() {
		assertThat(patterns(scanner.scan("password=hunter22"))).containsExactly("password_assign");
	}

	@Test
	void findsPasswordAssignIgnoringCase() {
		assertThat(patterns(scanner.scan("DB_PASSWORD: \"s3cr3t!\""))).containsExactly("password_assign");
	}

	@Test
	void ignoresPlaceholder() {
		assertThat(scanner.scan("password=[PASSWORD]")).isEmpty();
	}

	@Test
	void ignoresPublicIp() {
		assertThat(scanner.scan("DNS는 8.8.8.8을 쓴다")).isEmpty();
	}

	@Test
	void findsPrivateIp() {
		assertThat(patterns(scanner.scan("서버 주소 192.168.0.10"))).containsExactly("private_ip");
	}

	@Test
	void cleanTextPasses() {
		assertThat(scanner.scan("Docker 멀티스테이지 빌드 설명\n```java\nint a = 1;\n```")).isEmpty();
	}

	@Test
	void findsCurrentKeyFormats() {
		assertThat(patterns(scanner.scan("sk-proj-Ab1_cd2-EFghIJklMNopQRstUVwx"))).contains("openai_key");
		assertThat(patterns(scanner.scan("github_pat_11ABCDEFG0abcdefghijklmnop"))).containsExactly("github_pat");
		assertThat(patterns(scanner.scan("AIzaSyA1234567890abcdefghijklmnopqrstuv"))).containsExactly("google_api_key");
		assertThat(patterns(scanner.scan("xoxb-1234567890-abcdefghij"))).containsExactly("slack_token");
		assertThat(patterns(scanner.scan("sk_live_" + "abcdefghijklmnopqrstuvwx"))).containsExactly("stripe_key");
	}

	@Test
	void findsApiKeyAssign() {
		assertThat(patterns(scanner.scan("MY_SERVICE_API_KEY=abcd1234efgh"))).containsExactly("password_assign");
	}

	@Test
	void ignoresHyphenatedWordsLikeSk() {
		assertThat(scanner.scan("task-management-system-design-notes")).isEmpty();
	}

	@Test
	void ignoresTeachingCode() {
		assertThat(scanner.scan("password: ${DB_PASSWORD}")).isEmpty();
		assertThat(scanner.scan("secret: ${JWT_SECRET}")).isEmpty();
		assertThat(scanner.scan("String token = request.getHeader(\"Authorization\");")).isEmpty();
		assertThat(scanner.scan("password=<비밀번호>")).isEmpty();
	}

	private static List<String> patterns(List<Finding> findings) {
		return findings.stream().map(Finding::pattern).toList();
	}

}
