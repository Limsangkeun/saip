# 학습 노트 블로그 — 계획 2: Google 로그인, 관리자 편집, 댓글, 버전, 이미지

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Google 로그인으로 관리자는 웹에서 노트를 쓰고 고치고 지우며 버전을 비교·복원하고, 방문자는 댓글을 달고, 노트에 이미지를 붙일 수 있게 한다(설계 문서 6장 4단계).

**Architecture:** 계획 1의 단일 Spring Boot 앱에 OAuth2 Client(Google, OIDC)를 붙인다. 관리자는 설정된 이메일로 로그인한 사람이며 `ROLE_ADMIN`을 받는다. 화면은 계속 Thymeleaf이고, 편집기는 라이브러리 없이 `<textarea>` + 서버 렌더링 미리보기(공개 화면과 같은 `MarkdownRenderer`)로 만든다. 이미지는 서버 디스크 폴더에 저장하고 `/images/**`로 제공한다.

**Tech Stack:** 계획 1 그대로 + `spring-boot-starter-security-oauth2-client`(Boot 4.1.2), `javax.imageio`(JDK 내장). 새 외부 라이브러리는 없다.

**Spec:** `docs/superpowers/specs/2026-10-23-learning-blog-design.md`

**후속 계획:** 계획 3: Docker, Lightsail, 도메인과 HTTPS, GitHub Actions, 백업(6장 5~9단계). 이 계획이 끝난 뒤 작성한다.

## Global Constraints

- 계획 1의 Global Constraints를 그대로 따른다. 특히 **모든 Task는 "개념 설명" 단계로 시작하고 사용자의 진행 동의를 받는다.**
- 로그인은 Google 하나뿐이다. 비밀번호를 저장하지 않는다.
- 관리자: 환경변수 `BLOG_ADMIN_EMAIL`과 Google 이메일이 같고(대소문자 무시) Google이 `email_verified=true`로 확인한 경우에만 `ROLE_ADMIN`. 값이 비어 있으면 관리자는 없다.
- Google 비밀값은 환경변수 `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`으로만 받는다. 값이 없어도 앱은 떠야 한다(로그인만 실패).
- 권한: 비로그인은 읽기만. 로그인 사용자는 댓글 작성, 자기 댓글 수정·삭제. 관리자는 노트 CRUD, 모든 댓글 삭제, 버전 관리. API 토큰은 노트 조회·생성·수정과 **이미지 업로드**만(삭제·댓글 불가).
- 웹 편집기와 댓글에는 민감 정보 검사를 하지 않는다(설계 문서: 사람이 직접 쓰는 글).
- 화면의 모든 POST는 CSRF 토큰이 필요하다. `/api/**`만 예외(토큰 인증, 계획 1).
- 댓글은 평문이다. 마크다운을 렌더링하지 않고 `th:text`로 이스케이프해서 보여준다. 대댓글은 없다.
- 노트가 수정될 때마다(웹, API, 복원 모두) 이전 제목과 본문이 `note_revision`에 남는다.
- 이미지: 디스크 폴더 `blog.image-dir`(기본 `data/images`, git 제외). PNG·JPEG·GIF만 받고, 업로드 최대 10MB, 4천만 화소 초과는 거절, 긴 변 1600px로 줄이고 다시 인코딩해서 EXIF(위치 정보 등)를 지운다. 본문에서는 `/images/<파일명>` 상대 경로로 참조한다.

## Review Focus

1. **관리자 사칭:** 이메일 대소문자가 다르거나(`Admin@Gmail.com`), Google이 확인하지 않은 이메일(`email_verified=false`)이면 관리자가 되면 안 된다(Task 1 테스트).
2. **CSRF 없는 POST:** 다른 사이트가 관리자의 브라우저로 노트 삭제나 댓글 작성을 보내면 403이어야 한다(Task 2, Task 4 테스트).
3. **댓글 속 `<script>`:** 실행되지 않고 글자로 보여야 한다(Task 4 테스트).
4. **이미지 폭탄과 가짜 이미지:** 확장자만 `.png`인 텍스트 파일이나 50000×50000처럼 거대한 이미지는 메모리를 쓰기 전에 400으로 거절해야 한다(Task 5 테스트).
5. **댓글·버전이 있는 노트 삭제:** 500 없이 노트, 버전, 댓글이 함께 지워져야 한다(Task 2, Task 4 테스트).

---

### Task 1: Google 로그인과 관리자 권한

**Files:**
- Modify: `build.gradle`, `src/main/resources/application.yml`, `src/main/java/com/saip/blog/SecurityConfig.java`, `src/main/resources/templates/layout.html`, `src/main/resources/static/css/site.css`
- Create: `src/main/java/com/saip/blog/auth/AdminAuthorities.java`, `src/main/java/com/saip/blog/admin/AdminController.java`, `src/main/resources/templates/admin/index.html`, `src/main/resources/templates/error/403.html`
- Test: `src/test/java/com/saip/blog/auth/AdminAuthoritiesTest.java`, `src/test/java/com/saip/blog/admin/AdminAccessTest.java`

**Interfaces:**
- Consumes: 계획 1의 `SecurityConfig` 두 체인(`@Order(1)` API, `@Order(2)` 페이지), `NoteService.tree()`, `NoteService.recent(int)`
- Produces:
  - `AdminAuthorities implements GrantedAuthoritiesMapper` (`@Component`): 생성자 `AdminAuthorities(@Value("${blog.admin-email:}") String adminEmail)`. 받은 권한을 유지하고 모두에게 `ROLE_USER`를, 조건을 만족하면 `ROLE_ADMIN`을 더한다.
  - 페이지 체인 규칙: `/admin/**` → `hasRole("ADMIN")`, `POST /notes/*/comments/**` → `authenticated()`, 나머지 `permitAll()`. `oauth2Login`, `logout`(POST `/logout`, 성공 시 `/`).
  - `GET /admin` → `admin/index`(노트 목록: 제목, 카테고리, 수정일, 편집 링크).
  - 레이아웃: 상단 바 오른쪽에 비로그인 시 "로그인"(`/oauth2/authorization/google`), 로그인 시 이름과 "로그아웃"(POST 폼), 관리자면 "관리" 링크. `<meta name="_csrf">`, `<meta name="_csrf_header">`(Task 2 JS가 사용).
  - 테스트 관례: 로그인 사용자는 `oidcLogin().idToken(t -> t.subject("u1").claim("name", "유저"))`, 관리자는 여기에 `.authorities(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_USER"))`.

- [x] **Step 1: 개념 설명**: OAuth2와 OpenID Connect(비밀번호 대신 Google이 "이 사람 맞다"고 보증), 클라이언트 ID·시크릿과 리디렉션 URI, 인증과 인가의 차이, 세션 쿠키, CSRF가 무엇이고 왜 폼에 토큰이 붙는지, Google Cloud Console에서 OAuth 클라이언트 만들기
- [x] **Step 2: 실패 테스트 작성** (`AdminAuthoritiesTest`): `OidcUserAuthority`를 `OidcIdToken`(claims: `email`, `email_verified`)으로 만들어 매퍼에 넣는다.

```java
@Test void verifiedAdminEmailGetsAdmin()        // email "admin@gmail.com", verified true → ROLE_ADMIN 포함
@Test void emailIsCaseInsensitive()             // "Admin@Gmail.com" vs 설정 "admin@gmail.com" → ROLE_ADMIN 포함
@Test void unverifiedEmailIsNotAdmin()          // verified false → ROLE_ADMIN 없음, ROLE_USER 있음
@Test void otherEmailIsUser()                   // "other@gmail.com" → ROLE_USER만
@Test void blankAdminEmailMeansNoAdmin()        // 설정 "" + email "" → ROLE_ADMIN 없음
```
- [x] **Step 3: 실패 테스트 작성** (`AdminAccessTest`, `@SpringBootTest @AutoConfigureMockMvc @Import(TestcontainersConfiguration.class)`):

```java
@Test void anonymousAdminRedirectsToGoogle()    // GET /admin → 3xx, redirectedUrlPattern("**/oauth2/authorization/google")
@Test void userGets403OnAdmin()                 // oidcLogin() 일반 사용자 → 403
@Test void adminSeesAdminPage()                 // 관리자 → 200, content에 "노트 관리"
@Test void topbarShowsLoginForAnonymous()       // GET / → "로그인" 포함
@Test void topbarShowsAdminLinkForAdmin()       // 관리자로 GET / → "관리" 링크와 "로그아웃" 포함
@Test void apiTokenStillWorks()                 // 계획 1 API 체인이 그대로인지: Bearer 없이 GET /api/notes → 401
```
- [x] **Step 4: 실행해서 실패 확인**: `./gradlew test --tests '*AdminAuthoritiesTest' --tests '*AdminAccessTest'` → 컴파일 실패 또는 FAIL
- [x] **Step 5: 구현**
  - `build.gradle`: `implementation 'org.springframework.boot:spring-boot-starter-security-oauth2-client'`, `testImplementation 'org.springframework.boot:spring-boot-starter-security-oauth2-client-test'`
  - `application.yml`: `spring.security.oauth2.client.registration.google`에 `client-id: ${GOOGLE_CLIENT_ID:unset}`, `client-secret: ${GOOGLE_CLIENT_SECRET:unset}`, `scope: openid,profile,email`. `blog.admin-email: ${BLOG_ADMIN_EMAIL:}`
  - 매퍼는 `OidcUserAuthority`의 `getIdToken()`에서 `getEmail()`, `getEmailVerified()`를 읽는다. `Boolean.TRUE.equals(verified)`일 때만 비교한다.
  - 페이지 체인에 `oauth2Login(o -> o.userInfoEndpoint(u -> u.userAuthoritiesMapper(adminAuthorities)))`. 403은 `error/403.html`로.
- [x] **Step 6: 실행해서 통과 확인**: 위 명령 → PASS, 이어서 `./gradlew test` 전체 PASS
- [x] **Step 7: 사용자와 함께 Google 설정**: Google Cloud Console에서 프로젝트 → OAuth 동의 화면(외부, 테스트 사용자에 본인) → OAuth 클라이언트 ID(웹 애플리케이션, 승인된 리디렉션 URI `http://localhost:8080/login/oauth2/code/google`). 사용자가 직접 `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `BLOG_ADMIN_EMAIL`을 Windows 사용자 환경변수로 설정한다(시크릿은 대화에 붙여넣지 않는다). 터미널 앱을 다시 켠다.
- [x] **Step 8: 수동 확인**: `bootRun` → 로그인 → 상단 바에 "관리" → `/admin` 열림. 시작 로그에 `Using generated security password`가 없어야 한다(OAuth2를 켜면 기본 메모리 사용자가 사라진다, 계획 1 리뷰 이월 항목).
- [x] **Step 9: 커밋**: `git commit -m "feat: Google 로그인과 관리자 권한"`

---

### Task 2: 관리자 노트 편집기

**Files:**
- Create: `src/main/resources/db/migration/V2__cascade_revisions.sql`, `src/main/resources/templates/admin/edit.html`, `src/main/resources/static/js/admin.js`
- Modify: `NoteService.java`, `Slugs.java`, `AdminController.java`, `admin/index.html`, `static/js/site.js`, `site.css`
- Test: `src/test/java/com/saip/blog/admin/AdminNoteTest.java`, `SlugsTest.java`, `NoteServiceTest.java`

**Interfaces:**
- Consumes: Task 1의 관리자 권한과 CSRF 메타 태그, `NoteService.create/update/get`, `MarkdownRenderer.render(String)`
- Produces:
  - `NoteService.delete(String slug)`: 노트와 그 버전(Task 4 이후 댓글도)을 지운다. DB `on delete cascade`로 처리한다.
  - `Slugs.of`가 빈 문자열이 되면 `IllegalArgumentException("제목에 글자나 숫자가 있어야 합니다")`(계획 1 리뷰 이월 항목).
  - 라우트(모두 관리자): `GET /admin/notes/new`, `GET /admin/notes/{slug}/edit`, `POST /admin/notes`(생성), `POST /admin/notes/{slug}`(수정), `POST /admin/notes/{slug}/delete`, `POST /admin/preview`(본문 `text/plain` 마크다운 → 렌더링된 HTML 조각)
  - 폼 필드: `title`, `categorySlug`(기존 slug를 `<datalist>`로 제안), `categoryName`(새 카테고리일 때만), `body`. 저장 성공 → `/notes/{slug}`로 리다이렉트. `IllegalArgumentException`·`DuplicateSlugException` → 같은 폼을 400과 오류 메시지로 다시 보여준다(입력값 유지).
  - `site.js`: `export function enhance(root)`(복사 버튼, Mermaid, hljs, 목차를 `root` 안에 적용). 공개 화면은 `enhance(document)`.
  - `admin.js`: 본문 입력 후 400ms 디바운스로 `/admin/preview`에 POST(헤더에 CSRF), 결과를 미리보기 영역에 넣고 `enhance(preview)`.

- [x] **Step 1: 개념 설명**: 폼 POST와 PRG(Post-Redirect-Get) 패턴, CSRF 토큰이 폼과 fetch에 붙는 방식, 미리보기를 서버에서 렌더링하는 이유(공개 화면과 똑같은 결과 보장), DB `on delete cascade`, Flyway 두 번째 마이그레이션
- [x] **Step 2: 실패 테스트 작성**

```java
// SlugsTest
@Test void symbolOnlyTitleIsRejected()          // Slugs.of("!!!") → IllegalArgumentException
// NoteServiceTest
@Test void deleteRemovesNoteAndRevisions()      // create → update(버전 1개) → delete → get은 NoteNotFoundException, 버전 테이블 0건
@Test void searchTreatsUnderscoreLiterally()    // "a_b" 노트만 있을 때 search("a_b") 1건, "axb" 노트는 search("a_b")에 안 걸림 (계획 1 리뷰 이월)
// AdminNoteTest (관리자 oidcLogin + csrf())
@Test void createsNoteFromForm()                // POST /admin/notes → 302 /notes/{slug}, 공개 화면에 제목
@Test void editKeepsRevision()                  // POST /admin/notes/{slug} → revisions(slug) 1건
@Test void deleteNote()                         // POST /admin/notes/{slug}/delete → 302 /admin, GET /notes/{slug} 404
@Test void deleteWithoutCsrfIs403()             // csrf() 없이 → 403, 노트 남아 있음
@Test void userCannotDelete()                   // 일반 사용자 + csrf → 403
@Test void previewMatchesPublicRenderer()       // POST /admin/preview "# A\n<script>x</script>" → MarkdownRenderer.render 결과와 같은 문자열
@Test void symbolTitleShowsFormError()          // 제목 "!!!" → 400, "제목에 글자나 숫자" 포함, 입력한 본문 유지
@Test void webEditorSkipsSecretScan()           // 본문 "password=hunter2222" → 저장됨(302)
```
- [x] **Step 3: 실행해서 실패 확인**: `./gradlew test --tests '*SlugsTest' --tests '*NoteServiceTest' --tests '*AdminNoteTest'` → FAIL
- [x] **Step 4: 구현**: `V2__cascade_revisions.sql`은 `note_revision_note_id_fkey`를 지우고 `on delete cascade`로 다시 만든다. 편집 화면은 왼쪽 입력, 오른쪽 미리보기 2열(768px 이하에서는 위아래). 삭제 버튼은 `confirm()`으로 한 번 묻는다.
- [x] **Step 5: 실행해서 통과 확인**: 위 명령 PASS, `./gradlew test` 전체 PASS
- [x] **Step 6: 수동 확인**: 브라우저에서 새 노트 작성 → Mermaid·코드 블록이 미리보기와 공개 화면에서 같게 보이는지, 삭제 확인 창이 뜨는지
- [x] **Step 7: 커밋**: `git commit -m "feat: 관리자 노트 편집기"`

---

### Task 3: 버전 비교·복원·삭제

**Files:**
- Create: `src/main/java/com/saip/blog/note/LineDiff.java`, `src/main/resources/templates/admin/revisions.html`, `src/main/resources/templates/admin/revision.html`
- Modify: `NoteService.java`, `Repositories.java`, `AdminController.java`, `admin/edit.html`(버전 목록 링크), `site.css`
- Test: `src/test/java/com/saip/blog/note/LineDiffTest.java`, `NoteServiceTest.java`, `src/test/java/com/saip/blog/admin/AdminRevisionTest.java`

**Interfaces:**
- Consumes: `NoteService.update`, `NoteService.revisions(String slug)`, Task 2의 편집 화면
- Produces:
  - `record LineDiff.Line(Kind kind, String text)`, `enum Kind { SAME, ADDED, REMOVED }`, `static List<Line> LineDiff.of(String before, String after)`: 줄 단위 LCS. `ponytail:` 주석으로 O(n·m) 한계(수천 줄이면 느려짐, 그때 java-diff-utils)를 남긴다.
  - `NoteService.revision(long id)` → `NoteRevision`(없으면 `NoteNotFoundException`), `NoteService.restore(long revisionId)` → `Note`(현재 내용을 새 버전으로 남기고 그 버전의 제목·본문으로 바꾼다, 카테고리는 유지), `NoteService.deleteRevision(long revisionId)`
  - 라우트(관리자): `GET /admin/notes/{slug}/revisions`(목록: 시각, 제목), `GET /admin/revisions/{id}`(그 버전 → 현재 본문 diff, 추가는 초록, 삭제는 빨강), `POST /admin/revisions/{id}/restore` → `/notes/{slug}`, `POST /admin/revisions/{id}/delete` → 목록

- [x] **Step 1: 개념 설명**: diff가 무엇인지(git diff와 같은 것), LCS로 두 글의 공통 줄을 찾는 원리(그림으로), 복원도 "수정"으로 기록하는 이유(되돌리기를 되돌릴 수 있게), 실수로 들어간 비밀값을 이력에서 지우는 용도
- [x] **Step 2: 실패 테스트 작성**

```java
// LineDiffTest
@Test void identicalTextIsAllSame()             // of("a\nb", "a\nb") → [SAME a, SAME b]
@Test void addedAndRemovedLines()               // of("a\nb\nc", "a\nx\nc") → [SAME a, REMOVED b, ADDED x, SAME c]
@Test void emptyBefore()                        // of("", "a") → [ADDED a]
// NoteServiceTest
@Test void restoreRecordsCurrentAsRevision()    // v1 → update v2 → restore(v1 버전) → 본문 v1, 버전 2건(최신이 v2)
@Test void deleteRevision()                     // 버전 삭제 후 revisions(slug) 0건, 노트는 그대로
// AdminRevisionTest (관리자)
@Test void revisionPageShowsDiff()              // GET /admin/revisions/{id} → 200, "class=\"diff-added\"" 포함
@Test void restoreRedirectsToNote()             // POST .../restore + csrf → 302 /notes/{slug}
@Test void userCannotSeeRevisions()             // 일반 사용자 → 403
```
- [x] **Step 3: 실행해서 실패 확인**: `./gradlew test --tests '*LineDiffTest' --tests '*NoteServiceTest' --tests '*AdminRevisionTest'` → FAIL
- [x] **Step 4: 구현**
- [x] **Step 5: 실행해서 통과 확인**: 위 명령 PASS, `./gradlew test` 전체 PASS
- [x] **Step 6: 커밋**: `git commit -m "feat: 노트 버전 비교·복원·삭제"`

---

### Task 4: 댓글

**Files:**
- Create: `src/main/resources/db/migration/V3__comment.sql`, `src/main/java/com/saip/blog/comment/Comment.java`, `CommentRepository.java`, `CommentService.java`, `CommentController.java`, `src/main/resources/templates/admin/comments.html`
- Modify: `note.html`, `PageController.java`(노트 화면에 댓글 목록), `AdminController.java`, `admin/index.html`(댓글 관리 링크), `site.css`
- Test: `src/test/java/com/saip/blog/comment/CommentTest.java`

**Interfaces:**
- Consumes: Task 1의 로그인(`OidcUser`: `getSubject()`, `getFullName()`), `ROLE_ADMIN`, `NoteService.get`, Task 2의 `NoteService.delete`
- Produces:
  - 테이블 `comment(id bigserial, note_id bigint not null references note(id) on delete cascade, author_sub text not null, author_name text not null, body text not null, created_at timestamptz not null, updated_at timestamptz not null)`, `note_id` 인덱스. 이메일은 저장하지 않는다.
  - `CommentService`: `add(String noteSlug, String authorSub, String authorName, String body)`, `edit(long id, String actorSub, String body)`, `delete(long id, String actorSub, boolean admin)`, `forNote(Note)`(오래된 순), `recent(int limit)`(관리용). 본인이 아니면(관리자 삭제 제외) `AccessDeniedException` → 403. 본문은 앞뒤 공백 제거 후 1~2000자, 아니면 `IllegalArgumentException` → 노트 화면으로 리다이렉트하고 오류 메시지(flash).
  - 라우트: `POST /notes/{slug}/comments`, `POST /notes/{slug}/comments/{id}`(수정), `POST /notes/{slug}/comments/{id}/delete`, 성공 시 `/notes/{slug}#comment-{id}`. `GET /admin/comments`(최근 100개, 노트 링크, 삭제 버튼).
  - 노트 화면 하단: 댓글 목록(작성자 이름, 날짜, `white-space: pre-wrap` 본문), 로그인 시 작성 폼, 본인 댓글에 수정(`<details>` 안의 폼)·삭제, 관리자는 모든 댓글에 삭제. 비로그인은 "로그인하고 댓글 달기" 링크.

- [ ] **Step 1: 개념 설명**: 소유자 확인(Google `sub`가 이메일보다 나은 이유: 바뀌지 않는 고유 번호), 이메일을 저장하지 않는 이유(공개 페이지 노출 위험, 최소 수집), XSS와 출력 이스케이프, 저장형 XSS 예시
- [ ] **Step 2: 실패 테스트 작성** (`CommentTest`, MockMvc + `oidcLogin()`)

```java
@Test void anonymousCannotComment()             // POST 비로그인 + csrf → 3xx oauth2/authorization/google
@Test void userComments()                       // 사용자 u1 → 302, 노트 화면에 본문과 "유저"
@Test void scriptIsEscaped()                    // "<script>alert(1)</script>" → 화면에 "&lt;script&gt;", "<script>alert" 없음
@Test void blankOrTooLongIsRejected()           // "  " , 2001자 → 저장 안 됨(댓글 수 0), 오류 메시지
@Test void otherUserCannotEditOrDelete()        // u2가 u1 댓글 수정·삭제 → 403
@Test void ownerEditsAndDeletes()               // u1 수정 → 새 본문, 삭제 → 0건
@Test void adminDeletesAnyComment()             // 관리자 → 삭제됨
@Test void commentWithoutCsrfIs403()            // u1, csrf 없이 → 403
@Test void deletingNoteDeletesComments()        // 댓글 있는 노트를 NoteService.delete → 예외 없음, 댓글 0건
@Test void emailIsNeverShown()                  // idToken에 email "u1@gmail.com" → 노트 화면에 이메일 없음
```
- [ ] **Step 3: 실행해서 실패 확인**: `./gradlew test --tests '*CommentTest'` → FAIL
- [ ] **Step 4: 구현**
- [ ] **Step 5: 실행해서 통과 확인**: 위 명령 PASS, `./gradlew test` 전체 PASS
- [ ] **Step 6: 수동 확인**: 본인 Google 계정으로 댓글 작성·수정·삭제, 시크릿 창(비로그인)에서 댓글이 보이고 폼은 안 보이는지
- [ ] **Step 7: 커밋**: `git commit -m "feat: Google 로그인 댓글"`

---

### Task 5: 이미지 업로드

**Files:**
- Create: `src/main/java/com/saip/blog/image/ImageStore.java`, `ImageController.java`, `InvalidImageException.java`
- Modify: `application.yml`, `SecurityConfig.java`, `admin.js`, `admin/edit.html`, `NoteApiController.java` 옆에 이미지 API(또는 `ImageController`에 두 경로), `claude-skill/blog/blog.py`, `claude-skill/blog/SKILL.md`, `.gitignore`
- Test: `src/test/java/com/saip/blog/image/ImageStoreTest.java`, `src/test/java/com/saip/blog/image/ImageUploadTest.java`

**Interfaces:**
- Consumes: Task 1 관리자 권한, 계획 1 API 토큰 체인(`ROLE_API`), Task 2 편집기
- Produces:
  - `ImageStore`(`@Component`, `@Value("${blog.image-dir:data/images}")`): `String store(byte[] data)` → `"/images/<32자 hex>.<png|jpg|gif>"`. 순서: `ImageIO.createImageInputStream` + 리더로 **형식과 크기만 먼저 읽고**(PNG·JPEG·GIF가 아니면, 또는 가로×세로 > 40,000,000이면 `InvalidImageException`), 그다음 디코딩 → 긴 변 1600px 초과면 축소 → JPEG는 품질 0.85, 그 외는 PNG로 다시 인코딩(GIF는 첫 프레임 PNG가 된다, `ponytail:` 주석) → 파일명은 결과 바이트의 SHA-256 앞 32자(같은 이미지는 한 파일).
  - `POST /admin/images`(관리자, CSRF, multipart `file`)와 `POST /api/images`(토큰, multipart `file`) → `201 {"path": "/images/...", "markdown": "![](/images/...)"}`. `InvalidImageException` → 400 `{error}`. 10MB 초과 → 413.
  - `GET /images/**`: `blog.image-dir`를 정적 리소스로 제공, `Cache-Control: max-age=31536000, immutable`(파일명이 내용 해시라 안전).
  - `admin.js`: 본문 `<textarea>`에 이미지 붙여넣기·끌어 놓기 → 업로드 → 커서 위치에 `markdown` 삽입, 업로드 중 표시.
  - `blog.py image <file>` → 서버 응답 JSON 출력. multipart 본문은 표준 라이브러리로 직접 만든다. `BLOG_URL`/`BLOG_TOKEN`이 없으면 트레이스백 대신 안내 문구와 종료 코드 1(계획 1 리뷰 이월).
  - `SKILL.md`: 사용자가 대화에 이미지 파일을 첨부했고 노트에 도움이 되면 `image`로 올리고 `markdown`을 본문에 넣는다. **올리기 전에 이미지를 직접 보고** 키, 비밀번호, 사내 화면이 보이면 올리지 않고 사용자에게 알린다(이미지는 패턴 검사가 불가능).
  - `.gitignore`: `data/`, `__pycache__/`. 이미 커밋된 `claude-skill/blog/__pycache__`는 `git rm --cached`(계획 1 리뷰 이월).

- [ ] **Step 1: 개념 설명**: multipart 업로드, 이미지 압축과 리사이즈가 비용(디스크, 트래픽)에 주는 영향, EXIF에 촬영 위치가 들어 있다는 것, 압축 폭탄(작은 파일이 거대한 그림으로 풀림)과 "크기를 먼저 읽기", 내용 해시 파일명과 영구 캐시, 나중에 Docker 볼륨으로 이 폴더를 보존한다는 예고(계획 3)
- [ ] **Step 2: 실패 테스트 작성**

```java
// ImageStoreTest (@TempDir, 테스트 안에서 BufferedImage를 ImageIO.write로 만들어 씀)
@Test void storesPngAndReturnsPath()            // 100x50 PNG → "/images/" + 32hex + ".png", 파일 존재
@Test void shrinksLongEdgeTo1600()              // 4000x1000 PNG → 저장된 이미지 1600x400
@Test void jpegStaysJpeg()                      // JPEG → ".jpg"
@Test void sameImageSamePath()                  // 같은 바이트 두 번 → 같은 경로, 파일 1개
@Test void rejectsNonImage()                    // "hello".getBytes() → InvalidImageException
@Test void rejectsHugeDimensions()              // 10000x5000 TYPE_BYTE_BINARY PNG(작은 파일) → InvalidImageException
@Test void stripsMetadataByReencoding()         // 저장된 파일 바이트가 원본과 다름(재인코딩 확인)
// ImageUploadTest (MockMvc multipart)
@Test void adminUploads()                       // 관리자 + csrf → 201, $.markdown 이 "![](/images/" 로 시작
@Test void apiTokenUploads()                    // Bearer test-token → 201
@Test void anonymousCannotUpload()              // POST /admin/images → 3xx 로그인, POST /api/images 토큰 없음 → 401
@Test void userCannotUpload()                   // 일반 사용자 → 403
@Test void fakePngIs400()                       // 이름 a.png, 내용 텍스트 → 400
@Test void uploadedImageIsServed()              // 업로드한 path로 GET → 200, Cache-Control에 immutable
```
`blog.py selftest`에 추가: `multipart(b"x", "a.png")`가 경계 문자열과 `filename="a.png"`를 포함하는지.
- [ ] **Step 3: 실행해서 실패 확인**: `./gradlew test --tests '*ImageStoreTest' --tests '*ImageUploadTest'`, `python claude-skill/blog/blog.py selftest` → FAIL
- [ ] **Step 4: 구현**: `application.yml`에 `spring.servlet.multipart.max-file-size: 10MB`, `max-request-size: 10MB`, `blog.image-dir: ${BLOG_IMAGE_DIR:data/images}`
- [ ] **Step 5: 실행해서 통과 확인**: 위 명령 PASS, `./gradlew test` 전체 PASS
- [ ] **Step 6: 수동 확인**: 편집기에 스크린샷 붙여넣기 → 미리보기에 이미지, `blog.py image`로 올린 이미지가 노트에 보이는지
- [ ] **Step 7: 커밋**: `git commit -m "feat: 노트 이미지 업로드"`

---

### Task 6: 정리와 노트

**Files:**
- Modify: `README.md`(Google 설정과 환경변수 3개 추가)

- [ ] **Step 1: README 갱신**: `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `BLOG_ADMIN_EMAIL`, `BLOG_IMAGE_DIR`, 리디렉션 URI
- [ ] **Step 2: 전체 확인**: `./gradlew test`, `python claude-skill/blog/blog.py selftest` → PASS
- [ ] **Step 3: 노트 작성**: `/blog`로 계획 2에서 배운 개념(OAuth2·OIDC, CSRF, XSS 이스케이프, 이미지 처리)을 올린다. 기존 노트와 겹치면 보강.
- [ ] **Step 4: 커밋**: `git commit -m "docs: 계획 2 실행 방법"`
