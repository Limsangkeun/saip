# 학습 노트 블로그 — 계획 3: Docker, Lightsail, 도메인과 HTTPS, 자동 배포, 백업

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 블로그를 Docker 이미지로 포장해 AWS Lightsail 1GB 서버에 올리고, 내 도메인에서 HTTPS로 운영하며, push하면 자동 배포되고, 매일 S3에 백업되게 한다(설계 문서 6장 5~9단계).

**Architecture:** 앱은 멀티스테이지 Dockerfile로 이미지를 만들어 GitHub Container Registry(ghcr, 공개)에 올린다. 서버에서는 `deploy/compose.yaml` 하나로 앱, PostgreSQL, Caddy(리버스 프록시와 HTTPS 인증서 자동 발급)를 띄운다. DB와 이미지 폴더는 Docker 볼륨에 두어 컨테이너를 바꿔도 남는다. GitHub Actions가 테스트, 이미지 빌드, SSH 배포를 하고, 서버의 cron이 매일 pg_dump와 이미지 폴더를 S3로 보낸다.

**Tech Stack:** 계획 2 그대로 + Docker(멀티스테이지, `eclipse-temurin:21`), docker compose, Caddy 2, GitHub Actions, ghcr.io, AWS Lightsail(서울, Ubuntu 24.04 LTS, x86_64), AWS S3와 IAM, AWS CLI v2. 앱에 새 라이브러리는 없다.

**Spec:** `docs/superpowers/specs/2026-10-23-learning-blog-design.md`

## Global Constraints

- 계획 1·2의 Global Constraints를 그대로 따른다. 특히 **모든 Task는 "개념 설명" 단계로 시작하고 사용자의 진행 동의를 받는다.** 각 Task 끝에 그 단계에서 배운 것을 `/blog`로 노트에 남긴다(설계 문서 6장).
- 예산: Lightsail 1GB(공인 IPv4 포함 월 약 $7) + 도메인(연 1~2만 원) + S3(월 백 원 안쪽). 그 밖에 유료 서비스를 쓰지 않는다.
- 서버 메모리 1GB: 스왑 2GB, JVM 힙 `-Xmx320m`, PostgreSQL `shared_buffers=64MB`. 컨테이너마다 `mem_limit`.
- 비밀값은 서버의 `~/saip/.env`(권한 600)와 GitHub Secrets에만 둔다. 저장소에는 `deploy/.env.example`만 둔다. 비밀값을 화면, 로그, 대화에 출력하지 않는다.
- 계정 가입, 결제, 도메인 구매, Google Cloud Console, AWS 콘솔 작업은 **사용자가 직접** 한다. Claude는 순서를 안내하고 결과를 확인만 한다(자격 증명을 입력하지 않는다).
- 외부로 나가는 포트는 22, 80, 443뿐이다. 앱(8080)과 DB(5432)는 compose 내부 네트워크에만 열린다.
- 저장소는 GitHub 공개 저장소, 이미지는 `ghcr.io/<github-user>/saip`(공개 패키지)다. 이미지 태그는 커밋 SHA와 `latest`.
- Dockerfile, compose, 워크플로에서 쓰는 이미지와 Action은 버전을 고정한다(`latest` 태그 금지, 우리 앱 이미지 제외).

## Review Focus

1. **프록시 뒤 HTTPS 인식:** Caddy가 https로 받고 앱에는 http로 넘기므로, 앱이 자기 주소를 `http://`로 알면 Google 로그인 `redirect_uri`가 `http://도메인/...`이 되어 Google이 거절한다. `X-Forwarded-Proto: https`가 오면 `redirect_uri`가 `https://`여야 한다(Task 1 테스트).
2. **CSP가 내 기능을 막음:** CSP 헤더를 켠 뒤에도 메뉴 버튼, 코드 하이라이트, 복사 버튼, Mermaid, 편집기 미리보기·업로드가 콘솔의 CSP 위반 없이 동작해야 한다(Task 1 Playwright 확인).
3. **재배포로 데이터가 사라짐:** 새 이미지로 컨테이너를 다시 만들어도 노트(DB)와 업로드 이미지가 남아 있어야 한다(Task 3 로컬 확인, Task 6 배포 후 확인).
4. **이미지 정리가 필요한 파일을 지움:** 예전 버전 본문에서만 쓰는 이미지, 방금 올리고 아직 저장하지 않은 이미지(하루 이내)는 지우면 안 된다(Task 2 테스트).
5. **복원할 수 없는 백업:** 백업 파일이 실제로 복원되는지 확인하지 않으면 백업이 없는 것과 같다. 복원 리허설을 하고, 백업이 실패하면 cron 로그에 남아야 한다(Task 7).

---

### Task 1: 프록시 뒤에서 돌 준비 (전달 헤더, CSP)

**Files:**
- Modify: `src/main/resources/application.yml`, `src/main/java/com/saip/blog/SecurityConfig.java`, `src/main/resources/templates/layout.html`, `src/main/resources/static/js/site.js`
- Test: `src/test/java/com/saip/blog/ProxyHeadersTest.java`

**Interfaces:**
- Consumes: 계획 2의 페이지 체인(`SecurityConfig`), `layout.html`의 메뉴 버튼
- Produces: 모든 페이지 응답의 `Content-Security-Policy` 헤더. `server.forward-headers-strategy: framework`

- [x] **Step 1: 개념 설명**: 리버스 프록시가 무엇이고 왜 앞에 두는지(HTTPS 종료, 인증서), `X-Forwarded-Proto/Host` 헤더와 앱이 자기 주소를 아는 방법, 이게 없으면 OAuth 리디렉션이 깨지는 이유, CSP(어떤 출처의 스크립트만 실행할지 브라우저에 알려 주는 헤더)와 인라인 `onclick`이 막히는 이유
- [x] **Step 2: 실패 테스트 작성** (`ProxyHeadersTest`, `@SpringBootTest @AutoConfigureMockMvc @Import(TestcontainersConfiguration.class)`):

```java
@Test void oauthRedirectUsesForwardedHttps()
// GET /oauth2/authorization/google, 헤더 X-Forwarded-Proto: https, X-Forwarded-Host: blog.example.com
// → Location 헤더에 "redirect_uri=https%3A%2F%2Fblog.example.com%2Flogin%2Foauth2%2Fcode%2Fgoogle" 포함
@Test void pagesSendContentSecurityPolicy()
// GET / → Content-Security-Policy 헤더에 "default-src 'self'", "script-src 'self' https://cdnjs.cloudflare.com https://cdn.jsdelivr.net",
//   "object-src 'none'", "frame-ancestors 'none'" 포함, "'unsafe-inline'"이 script-src에 없음
@Test void layoutHasNoInlineHandlers()
// GET / → 본문에 "onclick=" 없음
```
- [x] **Step 3: 실행해서 실패 확인**: `./gradlew test --tests '*ProxyHeadersTest'` → FAIL 3개
- [x] **Step 4: 구현**
  - `application.yml`: `server.forward-headers-strategy: framework`. 앱 포트는 compose 내부에서만 열리므로 전달 헤더를 믿어도 된다(Caddy가 덮어쓴다).
  - 페이지 체인 `headers(h -> h.contentSecurityPolicy(c -> c.policyDirectives(...)))`. 값:
    `default-src 'self'; script-src 'self' https://cdnjs.cloudflare.com https://cdn.jsdelivr.net; style-src 'self' 'unsafe-inline' https://cdnjs.cloudflare.com; img-src 'self' data:; connect-src 'self' https://cdn.jsdelivr.net; font-src 'self' data:; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'`
    (`style-src 'unsafe-inline'`은 Mermaid가 SVG 안에 `<style>`을 넣기 때문이다. 스크립트에는 허용하지 않는다.) API 체인에는 넣지 않는다.
  - `layout.html`의 `onclick`을 지우고 `site.js` 맨 위(모든 페이지에서 실행되는 곳)에서 `.menu` 클릭 시 `document.body.classList.toggle('side-open')`.
- [x] **Step 5: 실행해서 통과 확인**: 위 명령 → PASS, `./gradlew test` 전체 PASS
- [x] **Step 6: Playwright 확인**(Review Focus 2): `bootRun` 후 Mermaid·코드 블록이 있는 노트, 좁은 화면 메뉴 버튼, 관리자 편집기 미리보기와 이미지 붙여넣기, 크게 보기를 열고 `browser_console_messages(level: error)`에 `Content Security Policy` 위반이 0개
- [x] **Step 7: 커밋**: `git commit -m "feat: 프록시 전달 헤더와 CSP"`

---

### Task 2: 안 쓰는 이미지 정리

**Files:**
- Create: `src/main/java/com/saip/blog/image/ImageCleanup.java`
- Modify: `src/main/java/com/saip/blog/BlogApplication.java` (`@EnableScheduling`)
- Test: `src/test/java/com/saip/blog/image/ImageCleanupTest.java`

**Interfaces:**
- Consumes: `ImageStore.dir()`, 테이블 `note.body_md`, `note_revision.body_md`
- Produces: `ImageCleanup` (`@Component`): `public int deleteUnused(Instant now)` 지운 파일 수를 돌려준다. `@Scheduled(cron = "0 30 4 * * *", zone = "Asia/Seoul") void daily()`가 `deleteUnused(Instant.now())`를 부른다.

- [x] **Step 1: 개념 설명**: 내용 해시 파일명이라 여러 노트가 한 파일을 함께 쓸 수 있다는 점, 그래서 노트 삭제 때 바로 지우지 않고 "아무 데서도 안 쓰는 파일"을 따로 찾는 이유, 유예 기간(업로드 직후 저장 전), `@Scheduled`와 cron 표현식
- [x] **Step 2: 실패 테스트 작성** (`ImageCleanupTest`, `@SpringBootTest @Import(TestcontainersConfiguration.class) @Transactional`, `@TestPropertySource(properties = "blog.image-dir=build/test-cleanup")`, 각 테스트 전에 폴더를 비운다. 파일 시각은 `Files.setLastModifiedTime`으로 조절한다):

```java
@Test void deletesOldUnreferencedImage()        // 2일 전 파일, 어디서도 참조 없음 → 삭제, 반환 1
@Test void keepsImageUsedInNote()               // 노트 본문에 "![](/images/<name>)" → 남음
@Test void keepsImageUsedOnlyInOldRevision()    // 본문을 바꿔 버전에만 참조가 남음 → 남음
@Test void keepsFreshUpload()                   // 1시간 전 파일, 참조 없음 → 남음
@Test void ignoresFilesNotNamedByHash()         // "notes.txt", 2일 전 → 남음 (우리가 만든 파일만 지운다)
```
- [x] **Step 3: 실행해서 실패 확인**: `./gradlew test --tests '*ImageCleanupTest'` → 컴파일 실패
  테스트에서 노트는 `NoteService`로 만들고 `EntityManager.flush()`한 뒤 정리를 부른다(같은 트랜잭션의 `JdbcTemplate`이 보도록).
- [x] **Step 4: 구현**: 폴더에서 `^[0-9a-f]{32}\.(png|jpg|gif)$`이고 수정 시각이 `now - 1일`보다 이전인 파일만 후보로 삼는다. 후보마다 `JdbcTemplate`으로 `select exists(select 1 from note where body_md like ?) or exists(select 1 from note_revision where body_md like ?)`(`'%/images/' + name + '%'`)를 묻고, 참조가 없으면 지운다. 지운 파일 이름을 INFO 로그로 남긴다.
- [x] **Step 5: 실행해서 통과 확인**: 위 명령 → PASS, `./gradlew test` 전체 PASS
- [x] **Step 6: 커밋**: `git commit -m "feat: 안 쓰는 이미지 매일 정리"`

---

### Task 3: Docker로 포장하고 로컬에서 운영 구성 리허설

**Files:**
- Create: `Dockerfile`, `.dockerignore`, `deploy/compose.yaml`, `deploy/Caddyfile`, `deploy/.env.example`
- Modify: `.gitignore`(`deploy/.env`), `README.md`(배포 구성 그림과 로컬 리허설 명령)

**Interfaces:**
- Consumes: Task 1의 전달 헤더 설정, Task 2까지 포함한 앱
- Produces (Task 4~7이 그대로 쓴다):
  - 이미지 이름 `ghcr.io/${GITHUB_USER}/saip:${APP_TAG:-latest}`
  - compose 서비스 `db`(볼륨 `pgdata`), `app`(볼륨 `images` → `/data/images`), `caddy`(볼륨 `caddy_data`, `caddy_config`, 포트 80·443)
  - `.env` 키: `DOMAIN`, `GITHUB_USER`, `APP_TAG`(기본 `latest`), `BACKUP_BUCKET`(Task 7), `POSTGRES_PASSWORD`, `BLOG_API_TOKEN`, `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `BLOG_ADMIN_EMAIL`

- [x] **Step 1: 개념 설명**: 이미지와 컨테이너, 레이어와 캐시, 멀티스테이지 빌드(빌드 도구는 최종 이미지에 넣지 않는다), 볼륨(컨테이너는 버려도 데이터는 남는다), compose 내부 네트워크와 서비스 이름으로 접속하기, Caddy가 인증서를 자동으로 받는 원리, ARM과 x86(Lightsail은 x86_64, 이 PC도 amd64라 따로 할 일이 없다)
- [x] **Step 2: `Dockerfile` 작성**
  - 1단계 `eclipse-temurin:21-jdk`(태그 고정)에서 `./gradlew bootJar -x test`(테스트는 CI가 따로 돌린다). Gradle 캐시가 잘 먹도록 `gradlew`, `gradle/`, `build.gradle`, `settings.gradle`를 먼저 복사하고 `./gradlew dependencies`, 그다음 `src/` 복사.
  - 2단계 `eclipse-temurin:21-jre`(태그 고정), root가 아닌 사용자로 실행, `ENTRYPOINT ["java", "-jar", "/app/app.jar"]`. 힙 옵션은 compose의 `JAVA_TOOL_OPTIONS`로 준다.
  - `.dockerignore`: `.git`, `build`, `.gradle`, `data`, `.superpowers`, `.playwright-mcp`, `deploy/.env`
- [x] **Step 3: `deploy/compose.yaml` 작성**
  - `db`: `postgres:17`(마이너 태그 고정), `POSTGRES_DB/USER=blog`, `POSTGRES_PASSWORD=${POSTGRES_PASSWORD}`, `command: postgres -c shared_buffers=64MB`, `healthcheck: pg_isready -U blog`, `mem_limit: 256m`, 포트 공개 없음
  - `app`: 위 이미지, `depends_on: db (service_healthy)`, 환경 `SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/blog`, `SPRING_DATASOURCE_USERNAME=blog`, `SPRING_DATASOURCE_PASSWORD`, `BLOG_IMAGE_DIR=/data/images`, `BLOG_BASE_URL=https://${DOMAIN}`, `JAVA_TOOL_OPTIONS=-Xmx320m -XX:+UseSerialGC`, `TZ=Asia/Seoul`, 그 밖의 `.env` 비밀값, `mem_limit: 512m`, `restart: unless-stopped`, 포트 공개 없음
  - `caddy`: `caddy:2`(마이너 태그 고정), `ports: 80:80, 443:443`, `./Caddyfile:/etc/caddy/Caddyfile:ro`, `DOMAIN` 전달, `mem_limit: 64m`
  - 모든 서비스 `logging: json-file, max-size 10m, max-file 3`(디스크가 로그로 차지 않게)
- [x] **Step 4: `deploy/Caddyfile` 작성**: `{$DOMAIN} { encode gzip; reverse_proxy app:8080 }`. `DOMAIN=localhost`면 Caddy가 자체 인증서를 쓰고, IP만 있을 때는 `DOMAIN=:80`으로 http만 연다(Task 4).
- [x] **Step 5: 이미지 빌드 확인**: `docker build -t ghcr.io/<user>/saip:latest .` → 성공, 이미지 크기가 300MB 안팎(JRE 기반). compose는 같은 이름의 로컬 이미지가 있으면 그것을 쓴다.
- [x] **Step 6: 로컬 리허설**: `deploy/.env`를 `.env.example`에서 만든다(`DOMAIN=localhost`, 임의 비밀번호와 토큰, Google 값은 비워 둠). `docker compose -f deploy/compose.yaml --env-file deploy/.env up -d` → `curl -k https://localhost/` 200, `curl -k -H "Authorization: Bearer $t" https://localhost/api/notes` 200(토큰은 변수로만 쓰고 출력하지 않는다. `blog.py`는 자체 인증서를 거절하므로 여기서는 curl)
- [x] **Step 7: 재생성해도 데이터가 남는지 확인**(Review Focus 3): API로 노트 1개와 이미지 1개를 올린 뒤 `docker compose ... up -d --force-recreate app db` → 노트와 `/images/...`가 그대로 200. `docker compose ... down`(볼륨 유지) 후 다시 `up` → 그대로. 확인 뒤 `down -v`로 리허설 데이터를 지운다.
- [x] **Step 8: 메모리 확인**: `docker stats --no-stream` → 세 컨테이너 합계가 800MB 아래
- [x] **Step 9: 커밋과 노트**: `git commit -m "feat: Docker 이미지와 운영용 compose"`, `/blog`로 Docker·compose·Caddy 노트

---

### Task 4: GitHub 저장소와 Lightsail 서버

**Files:**
- Create: `deploy/server-setup.md`(서버를 처음 만들 때 실행한 명령 기록, 비밀값 없음)
- Modify: `README.md`(배포 절차 요약)

**Interfaces:**
- Consumes: Task 3의 이미지 이름, compose, `.env` 키
- Produces: 서버 `ubuntu@<고정 IP>`의 `~/saip/{compose.yaml,Caddyfile,.env}`, 공개 패키지 `ghcr.io/<user>/saip:latest`, GitHub 공개 저장소 `<user>/saip`

- [x] **Step 1: 개념 설명**: VPS와 Lightsail(EC2와의 차이), 리전 선택(서울), SSH 키 방식 로그인, 고정 IP(인스턴스를 다시 만들어도 주소 유지), 방화벽, 스왑(메모리가 모자랄 때 디스크를 빌림), 컨테이너 레지스트리와 공개 패키지
- [x] **Step 2: GitHub 저장소**: 사용자가 공개 저장소 `saip`을 만든다(`gh repo create saip --public --source . --push`). 올리기 전에 `git ls-files`에 `.env`, `data/`, 토큰이 없는지 확인한다.
- [x] **Step 3: 첫 이미지 올리기**: `gh auth refresh -s write:packages` → `gh auth token | docker login ghcr.io -u <user> --password-stdin`(토큰은 출력하지 않는다) → `docker push ghcr.io/<user>/saip:latest`. 사용자가 GitHub 패키지 설정에서 공개로 바꾸고 저장소와 연결한다. 확인: 로그아웃 상태 `docker pull` 성공.
- [x] **Step 4: Lightsail 인스턴스**(사용자가 콘솔에서): 서울, Linux/Unix, Ubuntu 24.04 LTS, 1GB 플랜, 새 SSH 키 쌍 다운로드(`~/.ssh/saip-lightsail.pem`). 고정 IP 생성·연결. 네트워킹 탭 IPv4 방화벽: SSH 22, HTTP 80, HTTPS 443만.
- [x] **Step 5: 서버 기본 설정**(SSH로 접속해 함께 실행, `deploy/server-setup.md`에 기록): `apt update && apt upgrade`, 스왑 2GB(`fallocate -l 2G /swapfile`, `mkswap`, `swapon`, `/etc/fstab`), `vm.swappiness=10`, 시간대 `Asia/Seoul`, Docker 공식 저장소로 Docker Engine과 compose 플러그인 설치, `ubuntu`를 `docker` 그룹에 추가, 무인 보안 업데이트(`unattended-upgrades`) 켜기
- [x] **Step 6: 서버에 배포 파일**: `scp deploy/compose.yaml deploy/Caddyfile` → `~/saip/`. `.env`는 서버에서 직접 만든다(`chmod 600`). 아직 도메인이 없으므로 `DOMAIN=:80`. `POSTGRES_PASSWORD`는 서버에서 `openssl rand -base64 32`로 만들고 출력하지 않는다. 운영 API 토큰은 로컬 개발용과 분리한다: 사용자 PC에서 README의 방법으로 새 값을 만들어 사용자 환경변수 `BLOG_TOKEN_PROD`에 저장하고, 화면에 출력하지 않고 `"BLOG_API_TOKEN=$t" | ssh ... "cat >> ~/saip/.env"`로 서버에 보낸다(PC의 `BLOG_TOKEN`은 Task 5에서 바꾼다).
- [x] **Step 7: 실행과 확인**: `docker compose up -d` → `http://<고정 IP>/` 200, `free -m`에서 사용 가능 메모리가 남아 있고 `docker compose logs app`에 `Started BlogApplication`
- [x] **Step 8: 로컬 노트 옮기기**: 로컬(저장소 루트 `compose.yaml`의 `postgres` 서비스)에서 `pg_dump -U blog --data-only --exclude-table=flyway_schema_history -Fc blog` → `scp` → 서버 `docker compose exec -T db pg_restore -U blog -d blog --data-only`(스키마는 앱이 시작할 때 Flyway가 이미 만들었다. 데이터 덤프에 시퀀스 값도 들어 있어 새 노트 id가 겹치지 않는다). 로컬 `data/images/*` → `scp` → `docker compose cp`로 `app:/data/images/`. 확인: 서버 첫 화면의 노트 수가 로컬과 같고 이미지가 보인다.
- [x] **Step 9: 커밋과 노트**: `git commit -m "docs: 서버 준비 기록"`, `/blog`로 Lightsail·SSH·스왑 노트

---

### Task 5: 도메인과 HTTPS

**Files:**
- Modify: `README.md`(도메인, Google 리디렉션 URI, `BLOG_URL` 전환)

**Interfaces:**
- Consumes: Task 4 서버와 고정 IP, Task 1 전달 헤더
- Produces: `https://<도메인>` 운영, 사용자 PC `BLOG_URL=https://<도메인>`

- [x] **Step 1: 개념 설명**: 도메인과 등록처, DNS와 A 레코드, TTL, HTTPS 인증서와 Let's Encrypt, Caddy가 80 포트로 소유를 증명하고 인증서를 자동 갱신하는 방식, http→https 리디렉션, HSTS. 등록처 비교(Cloudflare Registrar: 원가 판매·DNS 무료, 가비아: 한국어·국내 결제, Route 53: 호스팅 영역 월 $0.5 추가)를 보여 주고 사용자가 고른다.
- [x] **Step 2: 도메인 구매와 DNS**(사용자): A 레코드 `@` → 고정 IP. Cloudflare를 쓰면 프록시(주황 구름)를 끄고 "DNS only". 확인: `nslookup <도메인>`이 고정 IP
- [x] **Step 3: Caddy 전환**: 서버 `.env`의 `DOMAIN=<도메인>` → `docker compose up -d caddy app` → `docker compose logs caddy`에 `certificate obtained successfully`. 확인: `curl -I http://<도메인>`이 308로 https, `curl -I https://<도메인>` 200
- [x] **Step 4: Google 로그인 운영 설정**(사용자): OAuth 클라이언트에 승인된 리디렉션 URI `https://<도메인>/login/oauth2/code/google` 추가, 동의 화면을 "프로덕션"으로 게시(기본 범위 openid·email·profile만 쓰므로 심사 없음). 확인(Review Focus 1): 운영 사이트에서 Google 로그인 → 관리 화면 열림
- [x] **Step 5: `/blog`를 운영으로**: 사용자 PC에서 `BLOG_URL`을 `https://<도메인>`으로, `BLOG_TOKEN`을 `BLOG_TOKEN_PROD` 값으로 바꾸고(출력 없이 환경변수끼리 복사) `BLOG_TOKEN_PROD`를 지운 뒤 터미널을 다시 켠다. 로컬 개발 서버는 이제 `BLOG_API_TOKEN`(옛 값)을 쓰므로, 로컬에 올릴 때는 그 창에서만 `$env:BLOG_URL`·`$env:BLOG_TOKEN`을 바꾼다(README에 기록). `blog.py list`가 운영 노트 목록을 돌려준다.
- [x] **Step 6: 커밋과 노트**: `git commit -m "docs: 도메인과 HTTPS"`, `/blog`로 DNS·HTTPS·Caddy 노트(첫 운영 노트)

---

### Task 6: GitHub Actions 자동 배포

**Files:**
- Create: `.github/workflows/deploy.yml`
- Modify: `README.md`(Secrets 목록, 배포 흐름 그림, 되돌리는 방법)

**Interfaces:**
- Consumes: Task 3 이미지 이름과 compose, Task 4 서버, Task 5 도메인
- Produces: `main`에 push하면 테스트 → 이미지(`:<sha>`, `:latest`) → 서버 반영 → 상태 확인

- [x] **Step 1: 개념 설명**: CI와 CD, 워크플로·잡·스텝, 러너, `GITHUB_TOKEN`과 권한(`packages: write`), GitHub Secrets, 배포 전용 SSH 키, `known_hosts`로 서버를 확인하는 이유, 이미지 태그를 SHA로 남기면 되돌리기 쉬운 이유
- [x] **Step 2: 배포 전용 SSH 키**: 사용자 PC에서 `ssh-keygen -t ed25519 -f ~/.ssh/saip-deploy -N ""` → 공개키를 서버 `~/.ssh/authorized_keys`에 추가. Secrets(사용자가 `gh secret set`으로 입력, 값 출력 금지): `SSH_HOST`(도메인), `SSH_USER`(`ubuntu`), `SSH_KEY`(개인키), `SSH_KNOWN_HOSTS`(`ssh-keyscan <도메인>` 결과)
- [x] **Step 3: 워크플로 작성** (`on: push: branches: [main]`, `workflow_dispatch`, `concurrency: deploy`):
  - `test`: `ubuntu-24.04`, `actions/checkout`, `actions/setup-java`(temurin 21, gradle 캐시), `./gradlew test`(러너에 Docker가 있어 Testcontainers가 돈다), `python claude-skill/blog/blog.py selftest`
  - `image`(`needs: test`, `permissions: packages: write`): `docker/login-action`(GITHUB_TOKEN), `docker/build-push-action`, 태그 `ghcr.io/${{ github.repository_owner }}/saip:${{ github.sha }}`와 `:latest`, GitHub Actions 캐시
  - `deploy`(`needs: image`): Secrets로 `~/.ssh/id_ed25519`와 `known_hosts`를 쓰고, `scp deploy/compose.yaml deploy/Caddyfile` → `ssh ... "cd ~/saip && sed -i 's/^APP_TAG=.*/APP_TAG=<sha>/' .env && docker compose pull app && docker compose up -d"`(`.env`에 `APP_TAG=latest` 줄이 처음부터 있어야 한다. 버전을 `.env`에 남겨서 수동 재시작도 같은 버전을 쓴다). 마지막에 `curl -fsS --retry 10 --retry-delay 6 --retry-all-errors https://<도메인>/` 실패면 잡 실패
  - Action은 메이저 버전이 아니라 태그 고정(예: `actions/checkout@v5.x.y`). 작성 시점의 최신 안정 버전을 확인해 넣는다.
- [x] **Step 4: 첫 실행 확인**: README 한 줄 수정 push → 세 잡 모두 초록, 서버 `docker compose ps`의 이미지 태그가 그 커밋 SHA, 사이트 정상, 노트와 이미지 그대로(Review Focus 3)
- [x] **Step 5: 실패가 막히는지 확인**: 실패하는 테스트를 넣은 브랜치에서 `workflow_dispatch` → `test` 실패, `image`·`deploy` 건너뜀, 사이트 그대로. 브랜치는 지운다.
- [x] **Step 6: 되돌리기 연습**: 서버에서 이전 SHA로 `APP_TAG=<이전 sha> docker compose up -d app` → 정상, 다시 최신으로. README에 방법 기록
- [x] **Step 7: 커밋과 노트**: `git commit -m "ci: push하면 테스트, 이미지, 배포"`, `/blog`로 GitHub Actions 노트

---

### Task 7: 백업과 운영 기본기

**Files:**
- Create: `deploy/backup.sh`, `deploy/restore.md`
- Modify: `deploy/server-setup.md`(cron, AWS CLI), `README.md`(운영: 로그 보기, 백업·복원)

**Interfaces:**
- Consumes: Task 3 compose 서비스 `db`, `app`과 볼륨 `images`, Task 4 서버
- Produces: 매일 04:00 KST `s3://<버킷>/daily/saip-YYYYMMDD.tar.gz`(pg_dump `-Fc` + 이미지 폴더), 14일 뒤 자동 삭제

- [x] **Step 1: 개념 설명**: 백업과 복원(복원해 보기 전에는 백업이 아니다), pg_dump 형식, S3와 수명 주기 규칙(오래된 파일 자동 삭제), IAM 최소 권한(서버가 털려도 백업을 지울 수 없게 `PutObject`만), cron, 로그 확인(`docker compose logs -f --since`), 디스크 확인(`df -h`, `docker system df`)
- [x] **Step 2: AWS 준비**(사용자가 콘솔에서, Claude는 정책 JSON을 제공): S3 버킷(서울, 퍼블릭 차단 유지, 기본 암호화), 수명 주기 규칙 `daily/` 14일 후 만료, IAM 사용자 `saip-backup`에 `s3:PutObject` on `arn:aws:s3:::<버킷>/daily/*`만. 액세스 키는 서버에서 `aws configure --profile saip-backup`로 사용자가 직접 입력한다.
- [x] **Step 3: `deploy/backup.sh` 작성** (`set -euo pipefail`, 인자 없음, 임시 폴더는 `trap`으로 지움):
  1. `docker compose -f ~/saip/compose.yaml exec -T db pg_dump -U blog -Fc blog > db.dump`
  2. `docker compose ... cp app:/data/images ./images`
  3. `tar czf saip-$(date +%Y%m%d).tar.gz db.dump images`
  4. `aws s3 cp ... s3://$BACKUP_BUCKET/daily/ --profile saip-backup`
  5. 성공하면 `backup ok <파일> <크기>` 한 줄 출력. 실패하면 0이 아닌 종료 코드(cron 로그에 남는다)
  - `BACKUP_BUCKET`은 서버 `.env`에서 읽는다.
- [x] **Step 4: 수동 실행 확인**: 서버에서 `bash ~/saip/backup.sh` → `backup ok`, S3 콘솔에 파일. 일부러 버킷 이름을 틀리게 해서 실행 → 0이 아닌 종료 코드와 오류 메시지(Review Focus 5)
- [x] **Step 5: 복원 리허설**(Review Focus 5): 백업 파일을 사용자 PC로 받아 로컬 `docker compose`의 빈 DB에 `pg_restore --clean --if-exists`, 이미지 폴더를 `data/images`로 → `bootRun`에서 운영과 같은 노트와 이미지가 보인다. 순서를 `deploy/restore.md`에 기록(서버 전체가 사라졌을 때: 새 인스턴스 → Task 4 Step 5~6 → 복원)
- [x] **Step 6: cron 등록**: `crontab -e`에 `0 4 * * * bash ~/saip/backup.sh >> ~/saip/backup.log 2>&1`. 다음 날 `tail ~/saip/backup.log`로 확인(사용자에게 알림 시점을 말해 둔다)
- [x] **Step 7: 운영 점검표**를 README에 기록하고 한 번 실행: `docker compose ps`, `docker compose logs --since 1h app`, `free -m`, `df -h`, `docker system df`, 인증서 만료일(`curl -vI https://<도메인> 2>&1 | grep expire`)
- [x] **Step 8: 커밋과 노트**: `git commit -m "ops: 매일 S3 백업과 운영 점검표"`, `/blog`로 백업·운영 노트

---

### Task 8: 마무리

- [ ] **Step 1: 전체 확인**: `./gradlew test`, `python claude-skill/blog/blog.py selftest` → PASS. 운영 사이트에서 노트 보기, 검색, 로그인, 댓글, 편집기 미리보기·이미지 붙여넣기, Mermaid를 Playwright로 한 바퀴 확인(콘솔 오류 0)
- [ ] **Step 2: 비용 확인**: AWS 결제 대시보드에서 이번 달 예상 비용이 Global Constraints 예산 안인지 사용자와 함께 본다.
- [ ] **Step 3: 노트**: `/blog`로 "이 블로그의 배포 전체 그림" 노트(로컬 → Docker → Lightsail → 도메인 → CI → 백업을 한 장의 Mermaid로)
- [ ] **Step 4: 커밋**: `git commit -m "docs: 계획 3 마무리"`
