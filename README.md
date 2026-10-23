# saip — learning notes

Claude Code와 대화하며 배운 내용을 `/blog`로 올리는 개인 학습 노트 블로그.

## 로컬 실행

필요한 것: Java 21, Docker Desktop

```powershell
.\gradlew bootRun   # compose.yaml의 PostgreSQL이 자동으로 뜬다
.\gradlew test      # Testcontainers로 테스트용 DB를 띄운다
```

http://localhost:8080

## `/blog` 스킬 설치 (한 번만)

```powershell
# 스킬 폴더를 저장소에 연결(정션). 스킬을 고치면 바로 반영된다.
New-Item -ItemType Junction -Path "$HOME\.claude\skills\blog" -Target "$PWD\claude-skill\blog"

# API 토큰: 같은 값을 서버(BLOG_API_TOKEN)와 스킬(BLOG_TOKEN)에 넣는다. 값은 화면에 출력하지 않는다.
$b = New-Object byte[] 32; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b)
$t = [Convert]::ToBase64String($b)
[Environment]::SetEnvironmentVariable("BLOG_API_TOKEN", $t, "User")
[Environment]::SetEnvironmentVariable("BLOG_TOKEN", $t, "User")
[Environment]::SetEnvironmentVariable("BLOG_URL", "http://localhost:8080", "User")
```

설정한 뒤에는 앱과 Claude Code를 다시 시작한다(실행 중인 프로그램은 새 환경변수를 모른다).

회사명처럼 정규식으로 못 잡는 금지어는 `~/.claude/blog-redact.txt`에 한 줄에 하나씩 적는다(저장소에 올리지 않는다).

확인: `python claude-skill/blog/blog.py selftest`
