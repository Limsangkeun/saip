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

운영: **https://saip.nexus** (Cloudflare Registrar, A 레코드 `@` → 고정 IP, DNS only. 인증서는 Caddy가 자동 발급·갱신)

서버: AWS Lightsail 1GB(시드니), Ubuntu 24.04, `ubuntu@<고정 IP>`. 처음 만드는 순서는 [`deploy/server-setup.md`](deploy/server-setup.md).

```powershell
# 서버 접속
ssh -i ~/.ssh/saip-lightsail.pem ubuntu@<고정 IP>
# 서버에서: 상태, 로그, 재시작
cd ~/saip; docker compose ps; docker compose logs --since 1h app; docker compose up -d
```

### 자동 배포 (`.github/workflows/deploy.yml`)

`main`에 push하면 `test`(gradle test, blog.py selftest) → `image`(ghcr에 `:<커밋 SHA>`, `:latest`) → `deploy`(서버 `.env`의 `APP_TAG`를 SHA로 바꾸고 `compose pull`·`up -d`, `https://saip.nexus` 확인). 앞 잡이 실패하면 배포하지 않는다. 다른 브랜치에서 수동 실행(`workflow_dispatch`)하면 테스트만 한다.

| Secret | 값 |
|---|---|
| `SSH_HOST` | `saip.nexus` |
| `SSH_USER` | `ubuntu` |
| `SSH_KEY` | 배포 전용 개인키 `~/.ssh/saip-deploy` (공개키는 서버 `~/.ssh/authorized_keys`. 유출되면 그 줄만 지운다) |
| `SSH_KNOWN_HOSTS` | `ssh-keyscan -t ed25519,ecdsa,rsa saip.nexus` 결과 (서버를 다시 만들면 갱신) |

되돌리기(서버에서): 이전 SHA는 GitHub Actions 실행 목록이나 `git log`에서 찾는다.

```bash
cd ~/saip && sed -i 's/^APP_TAG=.*/APP_TAG=<이전 SHA>/' .env && docker compose up -d app
```

다음 push가 다시 최신 SHA로 바꾼다.

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

운영 서버로 올릴 때(현재 설정): `BLOG_URL=https://saip.nexus`, `BLOG_TOKEN`은 서버 `~/saip/.env`의 `BLOG_API_TOKEN`과 같은 값(로컬 개발용 `BLOG_API_TOKEN`과 다르다). 로컬 개발 서버에 올려 보려면 그 창에서만 바꾼다.

```powershell
$env:BLOG_URL = "http://localhost:8080"
$env:BLOG_TOKEN = [Environment]::GetEnvironmentVariable("BLOG_API_TOKEN", "User")
```

## Google 로그인과 관리자 (한 번만)

1. [Google Cloud Console](https://console.cloud.google.com/apis/credentials) → OAuth 클라이언트 ID(웹 애플리케이션) 만들기
2. 승인된 리디렉션 URI: `http://localhost:8080/login/oauth2/code/google`, `https://saip.nexus/login/oauth2/code/google`. 동의 화면은 "프로덕션"으로 게시(방문자도 로그인).
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
