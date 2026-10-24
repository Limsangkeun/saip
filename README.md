# saip — learning notes

Claude Code와 대화하며 배운 내용을 `/blog`로 올리는 개인 학습 노트 블로그.

## 로컬 실행

필요한 것: Java 21, Docker Desktop

```powershell
.\gradlew bootRun   # compose.yaml의 PostgreSQL이 자동으로 뜬다
.\gradlew test      # Testcontainers로 테스트용 DB를 띄운다
```

http://localhost:8080

## 운영 구성 (`deploy/`)

```
인터넷 ─80/443─▶ caddy ─app:8080─▶ app ─db:5432─▶ db
                 (HTTPS 인증서)      (이미지 볼륨)   (pgdata 볼륨)
```

- 이미지: `ghcr.io/limsangkeun/saip:<태그>` (`Dockerfile` 멀티스테이지, JRE만 포함)
- `deploy/compose.yaml`(프로젝트 이름 `saip-prod`), `deploy/Caddyfile`, 비밀값은 `deploy/.env`(`.env.example` 참고, git 제외)
- 개발용 루트 `compose.yaml`(DB만)과는 별개다.

로컬 리허설(운영과 같은 구성을 `https://localhost`로, 자체 인증서라 `curl -k`):

```powershell
docker build -t ghcr.io/limsangkeun/saip:latest .
# deploy/.env: DOMAIN=localhost, POSTGRES_PASSWORD·BLOG_API_TOKEN은 임의 값
docker compose -f deploy/compose.yaml --env-file deploy/.env up -d
docker compose -f deploy/compose.yaml --env-file deploy/.env down -v   # 리허설 데이터까지 삭제
```

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

## Google 로그인과 관리자 (한 번만)

1. [Google Cloud Console](https://console.cloud.google.com/apis/credentials) → OAuth 클라이언트 ID(웹 애플리케이션) 만들기
2. 승인된 리디렉션 URI: `http://localhost:8080/login/oauth2/code/google` (배포하면 `https://도메인/login/oauth2/code/google` 추가)
3. 일반 PowerShell 창에서 값을 넣는다(대화창이나 저장소에 붙여 넣지 않는다).

```powershell
[Environment]::SetEnvironmentVariable("GOOGLE_CLIENT_ID", (Read-Host "client id"), "User")
[Environment]::SetEnvironmentVariable("GOOGLE_CLIENT_SECRET", (Read-Host "client secret"), "User")
[Environment]::SetEnvironmentVariable("BLOG_ADMIN_EMAIL", "내-구글-주소@gmail.com", "User")
```

| 환경변수 | 뜻 | 없으면 |
|---|---|---|
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | Google 로그인 | 앱은 뜨지만 로그인이 실패한다 |
| `BLOG_ADMIN_EMAIL` | 이 이메일(Google 확인 완료)로 로그인하면 관리자(`/admin`) | 관리자 없음 |
| `BLOG_IMAGE_DIR` | 업로드 이미지 폴더 | `data/images` (git 제외) |

확인(값은 보이지 않고 True/False만): `"GOOGLE_CLIENT_ID","GOOGLE_CLIENT_SECRET","BLOG_ADMIN_EMAIL" | % { "$_ " + [bool][Environment]::GetEnvironmentVariable($_,"User") }`

## 금지어

회사명처럼 정규식으로 못 잡는 금지어는 `~/.claude/blog-redact.txt`에 한 줄에 하나씩 적는다(저장소에 올리지 않는다).

확인: `python claude-skill/blog/blog.py selftest`
