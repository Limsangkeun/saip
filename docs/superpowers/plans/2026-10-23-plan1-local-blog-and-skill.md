# 학습 노트 블로그 — 계획 1: 로컬 블로그와 `/blog` 스킬

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 내 PC에서 돌아가는 주제별 노트 블로그를 만들고, Claude Code의 `/blog` 요청으로 노트가 생성되거나 보강되게 한다(설계 문서 6장의 1~3단계).

**Architecture:** Spring Boot 단일 앱이 Thymeleaf로 공개 화면을 그리고, `/api/**`에서 Bearer 토큰으로 보호된 노트 API를 제공한다. 마크다운은 서버에서 commonmark-java로 HTML로 바꾸고, 코드 색상, Mermaid, 복사 버튼, 목차는 브라우저 JS가 처리한다. Claude Code 스킬은 Python 스크립트 하나(`blog.py`)로 API를 호출하며, 보내기 전에 로컬에서 민감 정보 검사를 한다.

**Tech Stack:** Java 21, Spring Boot 4.1.2 (Gradle), Thymeleaf, Spring Data JPA, PostgreSQL 17, Flyway, Spring Security, spring-boot-docker-compose, Testcontainers, commonmark-java 0.31.0, highlight.js·mermaid(CDN), Python 3.12(표준 라이브러리만)

**Spec:** `docs/superpowers/specs/2026-10-23-learning-blog-design.md`

**후속 계획(이 문서 범위 밖):** 계획 2: Google 로그인, 관리자 편집, 댓글, 버전 UI, 이미지(6장 4단계). 계획 3: Docker, Lightsail, 도메인과 HTTPS, GitHub Actions, 백업(6장 5~9단계). 각 계획은 앞 계획이 끝난 뒤 작성한다.

## Global Constraints

- 프로젝트 루트: `C:\workspace\saip`. 패키지: `com.saip.blog`. 기능별 패키지로 나눈다(`note`, `markdown`, `web`, `api`, `sensitive`).
- 진행 방식: **모든 Task는 "개념 설명" 단계로 시작한다.** 이 Task에서 처음 등장하는 도구와 개념을 사용자에게 한국어로 쉽게 설명하고(무엇인지, 왜 필요한지, 대안은 무엇인지), 사용자가 이해했는지 확인한 뒤 구현한다. 설명은 짧게 하고 비유를 하나 곁들인다.
- 비밀값(API 토큰 등)은 저장소에 커밋하지 않는다. 환경변수로만 받는다.
- 노트 본문의 원본 HTML은 이스케이프한다(XSS 방지).
- 모든 텍스트 입출력은 UTF-8이다. Windows 기본 인코딩(cp949)에 의존하지 않는다.
- `.superpowers/`, `build/`, `.gradle/`, `.env`는 `.gitignore`에 넣는다.
- 노트 API 토큰 권한: 조회, 생성, 수정만. 삭제 엔드포인트는 없다.
- 노트가 수정될 때마다 이전 제목과 본문을 `note_revision`에 남긴다.

## Review Focus

1. **한글 제목의 slug:** `"Docker 멀티스테이지 빌드"`의 slug는 `"docker-멀티스테이지-빌드"`가 되어야 하고, URL로 열려야 한다(Task 2, Task 4 테스트).
2. **본문 속 `<script>`:** 그대로 실행되면 안 되고 글자로 보여야 한다(Task 3 테스트).
3. **Windows 한글 인코딩:** `blog.py`로 올린 한글 노트가 깨지지 않고 왕복해야 한다(Task 7 수동 확인 + self-check).
4. **제목 속 비밀값:** 본문뿐 아니라 제목에 들어간 키도 검사에 걸려야 한다(Task 6 테스트).
5. **검색어 특수문자:** `%`나 `_`가 들어간 검색어는 와일드카드가 아니라 글자 그대로 검색되어야 한다(Task 2 테스트).

---

### Task 1: 프로젝트 뼈대와 로컬 DB

**Files:**
- Create: `.gitignore`, `build.gradle`, `settings.gradle`, `gradlew*`, `gradle/`, `compose.yaml`, `src/main/java/com/saip/blog/BlogApplication.java`, `src/main/resources/application.yml`, `src/test/java/com/saip/blog/TestcontainersConfiguration.java`, `src/test/java/com/saip/blog/BlogApplicationTests.java`

**Interfaces:**
- Produces: 실행 가능한 Spring Boot 앱. `./gradlew bootRun`을 실행하면 `compose.yaml`의 PostgreSQL이 자동으로 뜬다. 테스트는 `@Import(TestcontainersConfiguration.class)`로 PostgreSQL 컨테이너를 쓴다.

- [x] **Step 1: 개념 설명**: git과 저장소, Gradle과 Gradle Wrapper, Spring Boot 스타터, Docker와 컨테이너(이번에는 DB만), docker compose, Spring Boot의 docker compose 자동 실행, Testcontainers, Flyway(DB 변경 이력 관리)
- [x] **Step 2: git 저장소 생성**: `git init -b main` 후 `.gitignore` 작성(Global Constraints 목록 + start.spring.io 기본 항목)
- [x] **Step 3: start.spring.io로 프로젝트 생성**

```bash
curl -s https://start.spring.io/starter.zip -d type=gradle-project -d language=java \
  -d bootVersion=4.1.2 -d javaVersion=21 -d groupId=com.saip -d artifactId=blog \
  -d packageName=com.saip.blog -d name=blog \
  -d dependencies=web,thymeleaf,data-jpa,postgresql,flyway,validation,security,docker-compose,testcontainers \
  -o "$TEMP/blog.zip"
```
압축을 루트에 풀고, 생성된 `compose.yaml`의 이미지를 `postgres:17`로 고정한다(DB 이름, 사용자, 비밀번호는 `blog`). 생성된 `.gitignore`는 Step 2 내용과 합친다.
- [x] **Step 4: 보안 임시 설정**: 이 시점에는 Spring Security가 모든 페이지를 막는다. Task 6 전까지 `SecurityConfig`에서 모두 허용(`permitAll`)하고, `// Task 6에서 /api/** 토큰 보호 추가` 주석을 남긴다. 파일: `src/main/java/com/saip/blog/SecurityConfig.java`
- [x] **Step 5: 확인**: `./gradlew test`는 PASS(contextLoads), `./gradlew bootRun` 실행 후 `docker ps`에 postgres 컨테이너가 보여야 한다.
- [x] **Step 6: 커밋**: 이미 있는 `docs/` 폴더(설계 문서와 이 계획)도 함께 커밋한다.

```bash
git add -A && git commit -m "chore: Spring Boot 프로젝트와 로컬 PostgreSQL 구성"
```

### Task 2: 노트 저장 구조와 서비스

**Files:**
- Create: `src/main/resources/db/migration/V1__init.sql`
- Create: `src/main/java/com/saip/blog/note/{Category,Note,NoteRevision}.java`, `{CategoryRepository,NoteRepository,NoteRevisionRepository}.java`, `NoteService.java`, `Slugs.java`, `NoteNotFoundException.java`, `DuplicateSlugException.java`
- Test: `src/test/java/com/saip/blog/note/NoteServiceTest.java`, `SlugsTest.java`

**Interfaces:**
- Produces:
  - 테이블: `category(id bigserial pk, slug text unique not null, name text not null, created_at timestamptz)`, `note(id bigserial pk, category_id fk not null, slug text unique not null, title text not null, body_md text not null, created_at, updated_at timestamptz)`, `note_revision(id bigserial pk, note_id fk not null, title text not null, body_md text not null, created_at timestamptz)`
  - `Slugs.of(String text) -> String`: 소문자로 바꾸고, 글자(한글 포함)와 숫자가 아닌 연속 문자는 `-` 하나로 바꾸며, 앞뒤 `-`는 제거한다.
  - `NoteService.create(String title, String categorySlug, String categoryName, String bodyMd) -> Note`: slug는 `Slugs.of(title)`. 같은 slug가 있으면 `DuplicateSlugException`. 카테고리가 없으면 `categoryName`으로 새로 만들고, `categoryName`이 null이면 `IllegalArgumentException`.
  - `NoteService.update(String slug, String title, String categorySlug, String bodyMd) -> Note`: 변경 전 제목과 본문을 `note_revision`에 저장한 뒤 갱신한다. 노트가 없으면 `NoteNotFoundException`. slug는 바뀌지 않는다(링크 유지).
  - `NoteService.get(String slug) -> Note` (없으면 `NoteNotFoundException`)
  - `NoteService.tree() -> List<Category>`: 카테고리는 이름순, 각 카테고리의 노트는 제목순(`Category.getNotes()`)
  - `NoteService.recent(int limit) -> List<Note>`: `updated_at` 내림차순
  - `NoteService.search(String q) -> List<Note>`: 제목이나 본문에 `q`를 포함(대소문자 무시). `%`, `_`, `\`는 이스케이프한다.
  - `NoteService.revisions(String slug) -> List<NoteRevision>`: 최신순(계획 2의 UI에서 사용)

- [x] **Step 1: 개념 설명**: JPA와 엔티티, Flyway 마이그레이션 파일 규칙, slug가 무엇이고 왜 쓰는지, 트랜잭션
- [x] **Step 2: 실패하는 테스트 작성**

```java
// SlugsTest
assertEquals("docker-멀티스테이지-빌드", Slugs.of("Docker 멀티스테이지 빌드"));
assertEquals("spring-boot-4", Slugs.of("  Spring Boot 4! "));
// NoteServiceTest (@SpringBootTest + @Import(TestcontainersConfiguration.class) + @Transactional)
@Test void createMakesCategoryWhenMissing()   // create("Docker", "deploy", "배포", "# hi") → tree()에 "배포" 아래 "Docker"
@Test void createRejectsDuplicateSlug()       // 같은 제목으로 두 번 → DuplicateSlugException
@Test void updateKeepsRevision()              // create 후 update(body "v2") → get().getBodyMd()=="v2", revisions(slug).get(0).getBodyMd()=="# hi"
@Test void updateMissingThrows()              // update("nope", ...) → NoteNotFoundException
@Test void searchTreatsPercentLiterally()     // 본문 "100% 완료" 노트와 "100 완료" 노트 → search("100%") 결과 1건
@Test void recentOrdersByUpdatedAt()          // A 생성, B 생성, A 수정 → recent(10).get(0)이 A
```
- [x] **Step 3: 실행해서 실패 확인**: `./gradlew test --tests "com.saip.blog.note.*"` → 컴파일 실패
- [x] **Step 4: 구현**: `V1__init.sql`, 엔티티, 리포지토리, `Slugs`, `NoteService`. 검색은 JPQL `lower(...) like lower(:p) escape '\'`로 한다.
- [x] **Step 5: 실행해서 통과 확인**: 같은 명령 → PASS
- [x] **Step 6: 커밋**: `git commit -m "feat: 노트/카테고리/버전 저장 구조와 서비스"`

### Task 3: 마크다운 렌더러

**Files:**
- Modify: `build.gradle`: `org.commonmark:commonmark`, `commonmark-ext-gfm-tables`, `commonmark-ext-gfm-strikethrough`, `commonmark-ext-task-list-items`, `commonmark-ext-autolink`, `commonmark-ext-heading-anchor`, `commonmark-ext-gfm-alerts`, 모두 `0.31.0`
- Create: `src/main/java/com/saip/blog/markdown/MarkdownRenderer.java`
- Test: `src/test/java/com/saip/blog/markdown/MarkdownRendererTest.java`

**Interfaces:**
- Produces: `@Component MarkdownRenderer.render(String md) -> String` (HTML). 계획 2의 편집기 미리보기도 이 메서드를 쓴다(공개 화면과 결과가 같아야 하므로).

- [x] **Step 1: 개념 설명**: 마크다운이 HTML로 바뀌는 과정, 서버 렌더링과 클라이언트 렌더링의 차이, XSS가 무엇인지
- [x] **Step 2: 실패하는 테스트 작성**

```java
@Test void table()      // "|a|b|\n|-|-|\n|1|2|" → contains "<table>"
@Test void taskList()   // "- [x] done" → contains "type=\"checkbox\"" and "checked"
@Test void alert()      // "> [!TIP]\n> 팁" → contains "markdown-alert-tip"
@Test void headingId()  // "## 설치 방법" → contains "id=\"설치-방법\""
@Test void codeLang()   // "```java\nint a;\n```" → contains "class=\"language-java\""
@Test void mermaidKeptAsCode() // "```mermaid\ngraph TD\n```" → contains "class=\"language-mermaid\""
@Test void escapesRawHtml()    // "<script>alert(1)</script>" → not contains "<script>", contains "&lt;script&gt;"
```
- [x] **Step 3: 실행해서 실패 확인**: `./gradlew test --tests "*MarkdownRendererTest"` → FAIL
- [x] **Step 4: 구현**: Parser/HtmlRenderer에 위 확장을 모두 등록하고, `HtmlRenderer.builder().escapeHtml(true)`로 설정한다. `alert` 테스트가 기대하는 클래스명이 실제 확장 출력과 다르면 테스트를 실제 출력에 맞춘다(확장의 클래스명이 기준이다).
- [x] **Step 5: 실행해서 통과 확인** → PASS
- [x] **Step 6: 커밋**: `git commit -m "feat: GFM 마크다운 렌더러"`

### Task 4: 공개 화면 (B안 주제별 노트)

**Files:**
- Create: `src/main/java/com/saip/blog/web/PageController.java`
- Create: `src/main/resources/templates/{layout.html,home.html,note.html,search.html,error/404.html}`
- Create: `src/main/resources/static/css/site.css`, `src/main/resources/static/js/site.js`
- Test: `src/test/java/com/saip/blog/web/PageControllerTest.java`

**Interfaces:**
- Consumes: `NoteService.tree/recent/get/search`, `MarkdownRenderer.render`, `NoteNotFoundException`
- Produces: 경로 `GET /`(최근 수정 노트 20개), `GET /notes/{slug}`, `GET /search?q=`. 노트 URL 형식은 `/notes/{slug}`이며, Task 6 API 응답과 스킬이 이 형식을 쓴다.

- [ ] **Step 1: 개념 설명**: MVC와 컨트롤러, Thymeleaf 레이아웃, 정적 파일(CSS, JS)과 CDN, URL 인코딩(한글 주소가 `%EB%..`로 바뀌는 이유)
- [ ] **Step 2: 실패하는 테스트 작성** (`@SpringBootTest @AutoConfigureMockMvc`, Testcontainers, 테스트 안에서 `NoteService.create`로 데이터 준비)

```java
@Test void homeShowsTreeAndRecent()   // GET / → 200, body contains 카테고리명 "배포" and 노트 제목 "Docker"
@Test void notePageRendersMarkdown()  // GET /notes/docker → 200, contains "<table>" (본문에 표)
@Test void koreanSlugWorks()          // create("멀티스테이지 빌드",...) → GET "/notes/멀티스테이지-빌드" → 200
@Test void missingNoteIs404()         // GET /notes/nope → 404
@Test void searchFinds()              // GET /search?q=compose → contains 해당 노트 제목
```
- [ ] **Step 3: 실행해서 실패 확인** → FAIL
- [ ] **Step 4: 구현**
  - 레이아웃: 상단 바(사이트명 "saip", 검색창), 왼쪽 주제 트리(현재 노트 강조), 본문, 노트 화면 오른쪽 목차(`<nav id="toc">`). 폭 768px 이하에서는 사이드바를 버튼으로 접는다. 시스템 설정을 따르는 다크 모드를 지원한다.
  - 노트 화면: 제목, "마지막 수정 yyyy-MM-dd", `th:utext`로 렌더링된 본문
  - `site.js`(라이브러리 없이 직접 작성): ① `pre > code`에 복사 버튼 추가 ② `language-mermaid` 블록을 `<div class="mermaid">`로 바꾼 뒤 mermaid 실행 ③ 나머지 코드 블록에 highlight.js 적용 ④ 본문의 h2·h3로 `#toc` 생성
  - highlight.js와 mermaid는 cdnjs 또는 jsdelivr에서 버전을 고정해 불러온다.
  - 강조 박스(`markdown-alert-*`)에 색과 아이콘을 입힌다(팁, 주의, 정보, 중요, 경고).
  - `NoteNotFoundException`이면 404 화면을 보여준다(`@ResponseStatus` 또는 `@ExceptionHandler`).
- [ ] **Step 5: 실행해서 통과 확인** → PASS
- [ ] **Step 6: 눈으로 확인**: `./gradlew bootRun` 후 표, 체크리스트, 강조 박스, Java 코드, Mermaid가 들어간 샘플 노트를 `NoteService`로 하나 넣고(임시 `CommandLineRunner` 또는 psql), 브라우저에서 사용자와 함께 확인한다. 샘플 넣기 코드는 커밋하지 않는다.
- [ ] **Step 7: 커밋**: `git commit -m "feat: 주제별 노트 공개 화면"`

### Task 5: 민감 정보 검사기

**Files:**
- Create: `src/main/resources/sensitive-patterns.tsv`
- Create: `src/main/java/com/saip/blog/sensitive/{SensitiveScanner,Finding}.java`
- Test: `src/test/java/com/saip/blog/sensitive/SensitiveScannerTest.java`

**Interfaces:**
- Produces:
  - `sensitive-patterns.tsv`: 한 줄에 `이름<TAB>정규식`, `#`으로 시작하면 주석. **Java와 Python에서 똑같이 동작하는 문법만** 쓴다(lookbehind, 소유 수량자, `(?<name>)` 금지). Task 7의 `blog.py`가 이 파일 사본을 쓴다.
  - 최소 패턴: `aws_access_key` `AKIA[0-9A-Z]{16}`, `anthropic_key` `sk-ant-[A-Za-z0-9_-]{20,}`, `openai_key` `sk-[A-Za-z0-9]{20,}`, `github_token` `gh[pousr]_[A-Za-z0-9]{36,}`, `jwt` `eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}`, `private_key` `-----BEGIN [A-Z ]*PRIVATE KEY-----`, `password_assign` `(?i)(password|passwd|pwd|secret|token)\s*[:=]\s*["']?[^\s"'\[]{4,}`, `private_ip` `\b(10\.\d{1,3}|192\.168|172\.(1[6-9]|2\d|3[01]))\.\d{1,3}\.\d{1,3}\b`
  - `record Finding(String pattern, int line)`: 비밀값 자체는 담지 않는다.
  - `@Component SensitiveScanner.scan(String text) -> List<Finding>` (line은 1부터)

- [ ] **Step 1: 개념 설명**: 정규식 기초, "이중 방어"가 왜 필요한지, 오탐(false positive)과 미탐
- [ ] **Step 2: 실패하는 테스트 작성**

```java
@Test void findsAwsKeyWithLine()    // "a\nkey AKIAABCDEFGHIJKLMNOP" → [Finding("aws_access_key", 2)]
@Test void findsPasswordAssign()    // "password=hunter22" → pattern "password_assign"
@Test void ignoresPlaceholder()     // "password=[PASSWORD]" → empty   (치환된 값은 통과)
@Test void ignoresPublicIp()        // "8.8.8.8" → empty
@Test void findsPrivateIp()         // "192.168.0.10" → pattern "private_ip"
@Test void cleanTextPasses()        // "Docker 멀티스테이지 빌드 설명" → empty
```
- [ ] **Step 3: 실행해서 실패 확인** → FAIL
- [ ] **Step 4: 구현**: 시작할 때 TSV를 읽어(UTF-8) `Pattern`으로 컴파일하고, 줄 단위로 검사한다.
- [ ] **Step 5: 실행해서 통과 확인** → PASS
- [ ] **Step 6: 커밋**: `git commit -m "feat: 민감 정보 패턴 검사기"`

### Task 6: 토큰으로 보호되는 노트 API

**Files:**
- Modify: `src/main/java/com/saip/blog/SecurityConfig.java`
- Modify: `src/main/resources/application.yml`: `blog.api-token: ${BLOG_API_TOKEN:}`, `blog.base-url: ${BLOG_BASE_URL:http://localhost:8080}`
- Create: `src/main/java/com/saip/blog/api/{NoteApiController,ApiTokenFilter,NoteRequest,NoteResponse,NoteSummary}.java`
- Test: `src/test/java/com/saip/blog/api/NoteApiControllerTest.java`

**Interfaces:**
- Consumes: `NoteService`, `SensitiveScanner`, `DuplicateSlugException`, `NoteNotFoundException`
- Produces (Task 7의 `blog.py`가 이 형식에 맞춘다):
  - 인증: 헤더 `Authorization: Bearer <BLOG_API_TOKEN>`. 없거나 틀리면 401. 설정된 토큰이 비어 있으면 모든 API 요청을 401로 거부한다.
  - `GET /api/notes` → `200 [{slug, title, categorySlug, categoryName, updatedAt}]`
  - `GET /api/notes/{slug}` → `200 {slug, title, categorySlug, categoryName, body, updatedAt, url}` / 404
  - `POST /api/notes` body `{title, categorySlug, categoryName?, body}` → `201 {slug, url, action:"created"}` / slug 중복 409 / 검증 실패 400
  - `PUT /api/notes/{slug}` body `{title, categorySlug, categoryName?, body}` → `200 {slug, url, action:"updated"}` / 404
  - 민감 정보: POST와 PUT에서 `title + "\n" + body`를 검사해 걸리면 저장하지 않고 `422 {findings:[{pattern, line}]}`를 돌려준다.
  - `url`은 `blog.base-url + "/notes/" + slug`
  - `/api/**`를 제외한 경로는 계속 `permitAll`이다. `/api/**`는 CSRF를 끈다.

- [ ] **Step 1: 개념 설명**: REST API와 HTTP 상태 코드(200/201/400/401/404/409/422), Bearer 토큰, Spring Security 필터 체인, CSRF가 무엇이고 API에서는 왜 끄는지, 환경변수
- [ ] **Step 2: 실패하는 테스트 작성** (`@TestPropertySource(properties="blog.api-token=test-token")`)

```java
@Test void rejectsWithoutToken()        // GET /api/notes (헤더 없음) → 401
@Test void rejectsWrongToken()          // Bearer nope → 401
@Test void createThenGet()              // POST {title:"Docker", categorySlug:"deploy", categoryName:"배포", body:"# hi"} → 201, action "created"; GET /api/notes/docker → body "# hi"
@Test void duplicateIs409()             // 같은 POST 두 번 → 409
@Test void updateWorks()                // PUT /api/notes/docker body "v2" → 200, action "updated"
@Test void secretInBodyIs422()          // body "key AKIAABCDEFGHIJKLMNOP" → 422, findings[0].pattern "aws_access_key", 응답 문자열에 "AKIA" 없음
@Test void secretInTitleIs422()         // title "password=hunter22" → 422
@Test void publicPagesStillOpen()       // GET / (토큰 없음) → 200
```
- [ ] **Step 3: 실행해서 실패 확인** → FAIL
- [ ] **Step 4: 구현**: 토큰 비교는 `MessageDigest.isEqual`로 상수 시간에 한다.
- [ ] **Step 5: 실행해서 통과 확인**: `./gradlew test` 전체 → PASS
- [ ] **Step 6: 커밋**: `git commit -m "feat: 토큰 보호 노트 API와 민감 정보 차단"`

### Task 7: `/blog` 스킬과 첫 노트

**Files:**
- Create: `claude-skill/blog/SKILL.md`, `claude-skill/blog/blog.py`, `claude-skill/install.ps1`
- Modify: `README.md` (없으면 생성): 실행 방법과 스킬 설치 방법

**Interfaces:**
- Consumes: Task 6 API 형식, `src/main/resources/sensitive-patterns.tsv`
- Produces:
  - `install.ps1`: `claude-skill/blog`를 `~/.claude/skills/blog`로 복사하고 `sensitive-patterns.tsv`도 그 안에 복사한다. 다시 실행하면 덮어쓴다. `~/.claude/blog-redact.txt`가 없으면 빈 파일과 사용법 주석을 만든다.
  - `blog.py` 명령(환경변수 `BLOG_URL`, `BLOG_TOKEN`, 표준 라이브러리만 사용, stdin/stdout UTF-8 강제):
    - `python blog.py list` → 노트 목록 JSON 출력
    - `python blog.py get <slug>` → 노트 JSON 출력
    - `python blog.py create <file.json>` / `python blog.py update <slug> <file.json>`: 파일은 `{title, categorySlug, categoryName, body}`(UTF-8). 보내기 전에 `sensitive-patterns.tsv`와 `~/.claude/blog-redact.txt`(한 줄에 단어 하나, 대소문자 무시)로 제목과 본문을 검사한다. 걸리면 보내지 않고 `{findings:[{pattern, line}]}`를 출력하고 종료 코드 2로 끝난다. 성공하면 서버 응답(`slug, url, action`)을 출력한다.
    - `python blog.py selftest`: 검사 로직의 assert 기반 자체 점검(AWS 키 검출, `[PASSWORD]` 통과, redact 단어 검출, 한글 통과)
  - `SKILL.md`(frontmatter `name: blog`, `description`: 사용자가 대화 내용을 블로그·노트에 올려 달라고 할 때 사용). 본문은 설계 문서 5장 "Claude Code 연동"의 7단계를 지시로 옮긴 것이다. 추가로 다음을 담는다.
    - 기존 노트를 보강할 때는 기존 내용을 지우지 않고 알맞은 위치에 통합한다.
    - 노트는 "처음 보는 미래의 나"가 읽어도 이해되게 쓴다: 개념 → 왜 필요한지 → 실제 명령이나 코드 → 겪은 문제와 해결 순서.
    - 마크다운 기능을 적극 활용한다: 강조 박스 `> [!TIP]`, `> [!WARNING]`, 표, 체크리스트, ```` ```mermaid ````.
    - 치환 규칙: `[API_KEY]`, `[PASSWORD]`, `[TOKEN]`, `[PRIVATE_IP]`, `[COMPANY]`, `[INTERNAL_CODE]`. 회사 내부 코드는 개념만 남기고 일반화한 예제로 바꾼다.
    - JSON은 임시 파일(UTF-8)에 써서 `blog.py`에 넘긴다.
    - 종료 코드 2를 받으면 걸린 줄을 치환해서 다시 시도하고, 서버가 422를 주면 같은 방식으로 처리한다.
    - 끝나면 "새로 만듦 / 기존 노트 수정함: 제목 (url)" 한 줄로 보고한다.

- [ ] **Step 1: 개념 설명**: Claude Code 스킬이 무엇인지, 환경변수 설정(Windows 사용자 환경변수), 이 스킬이 대화를 노트로 바꾸는 흐름(Mermaid 다이어그램으로 보여주기)
- [ ] **Step 2: `blog.py` selftest 먼저 작성** → `python claude-skill/blog/blog.py selftest` 실패 확인
- [ ] **Step 3: `blog.py` 구현** → selftest PASS
- [ ] **Step 4: `SKILL.md`, `install.ps1` 작성 후 설치**: `powershell -File claude-skill/install.ps1`
- [ ] **Step 5: 사용자와 함께 환경변수 설정**: 토큰은 `[Convert]::ToBase64String((1..32|%{Get-Random -Max 256}) -as [byte[]])`로 만들어 사용자 환경변수 `BLOG_TOKEN`과 `BLOG_API_TOKEN`에 같은 값을 넣는다. `BLOG_URL=http://localhost:8080`. 토큰 값은 대화나 파일에 남기지 않는다.
- [ ] **Step 6: 왕복 확인**: `bootRun` 상태에서 `create` → `get`을 실행해 한글 제목과 본문이 깨지지 않는지 확인하고, 비밀값이 든 파일은 종료 코드 2로 막히는지 확인한다.
- [ ] **Step 7: 첫 노트 작성(실사용 검증)**: 새 Claude Code 세션에서 사용자가 `/blog`로 계획 1에서 배운 내용(Gradle, Spring Boot, docker compose, Flyway, Spring Security 토큰 인증 등)을 올린다. 한 번은 새 노트가 생성되고, 비슷한 주제로 다시 요청했을 때는 기존 노트가 보강되는지 확인한다. 화면에서 강조 박스와 Mermaid가 보이는지도 확인한다.
- [ ] **Step 8: 커밋**: `git commit -m "feat: Claude Code /blog 스킬"`
