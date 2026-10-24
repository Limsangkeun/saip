"""블로그 API 클라이언트. Claude Code /blog 스킬이 호출한다. 표준 라이브러리만 사용.

python blog.py list | get <slug> | create <file.json> | update <slug> <file.json> | image <file> | selftest
환경변수: BLOG_URL, BLOG_TOKEN
종료 코드: 0 성공, 1 오류, 2 민감 정보 걸림(로컬 검사 또는 서버 422)
"""
import json
import mimetypes
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

# 스킬 폴더는 저장소를 가리키는 정션이므로, resolve()로 실제 저장소 경로를 찾아 서버와 같은 패턴 파일을 읽는다.
PATTERNS_FILE = Path(__file__).resolve().parents[2] / "src" / "main" / "resources" / "sensitive-patterns.tsv"
REDACT_FILE = Path.home() / ".claude" / "blog-redact.txt"


def load_lines(path):
    if not path.exists():
        return []
    lines = path.read_text(encoding="utf-8").splitlines()
    return [l for l in lines if l.strip() and not l.startswith("#")]


PATTERNS = [(name, re.compile(rx)) for name, rx in (l.split("\t", 1) for l in load_lines(PATTERNS_FILE))]


def scan(text, redact_words):
    """서버와 같은 규칙: 줄마다 패턴별로 하나씩. 회사 금지어는 대소문자 무시 부분 일치."""
    findings = []
    for i, line in enumerate(text.splitlines(), 1):
        findings += [{"pattern": name, "line": i} for name, rx in PATTERNS if rx.search(line)]
        if any(w.lower() in line.lower() for w in redact_words):
            findings.append({"pattern": "redact_word", "line": i})
    return findings


def note_path(slug):
    # 슬러그에 한글이 들어가므로 퍼센트 인코딩한다(HTTP 요청 줄은 ASCII만 허용)
    return "/api/notes/" + urllib.parse.quote(slug, safe="")


def base_url(url):
    """토큰이 평문으로 나가지 않게 https만 허용한다. 로컬 개발용 http://localhost만 예외."""
    u = urllib.parse.urlsplit(url)
    if u.scheme == "https" or (u.scheme == "http" and u.hostname in ("localhost", "127.0.0.1")):
        return url.rstrip("/")
    raise ValueError("BLOG_URL은 https:// 여야 합니다(로컬만 http://localhost 허용): " + url)


class NoRedirect(urllib.request.HTTPRedirectHandler):
    # urllib은 리다이렉트할 때 Authorization 헤더를 다른 호스트로도 넘긴다. API는 리다이렉트하지 않으므로 거부한다.
    def redirect_request(self, *args):
        return None


OPENER = urllib.request.build_opener(NoRedirect)


def missing_env(environ):
    return [n for n in ("BLOG_URL", "BLOG_TOKEN") if not environ.get(n)]


def multipart(data, filename):
    """표준 라이브러리에 multipart 도구가 없어서 직접 만든다. 파일 하나(file 필드)만 보낸다."""
    boundary = "----blogpy" + os.urandom(12).hex()
    kind = mimetypes.guess_type(filename)[0] or "application/octet-stream"
    head = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{Path(filename).name}"\r\n'
            f"Content-Type: {kind}\r\n\r\n").encode()
    return head + data + f"\r\n--{boundary}--\r\n".encode(), "multipart/form-data; boundary=" + boundary


def call(method, path, data=None, content_type="application/json"):
    req = urllib.request.Request(
        base_url(os.environ["BLOG_URL"]) + path,
        method=method,
        data=data,
        headers={"Authorization": "Bearer " + os.environ["BLOG_TOKEN"], "Content-Type": content_type},
    )
    try:
        with OPENER.open(req) as res:
            print(res.read().decode("utf-8"))
            return 0
    except urllib.error.HTTPError as e:
        print(json.dumps({"status": e.code, "response": e.read().decode("utf-8")}, ensure_ascii=False))
        return 2 if e.code == 422 else 1


def note_findings(note, redact_words):
    """서버 422와 같은 번호: 0번 줄은 카테고리(사이드바에 공개), 1번 줄은 제목, 본문은 2번 줄부터."""
    category = note["categorySlug"] + " " + (note.get("categoryName") or "")
    return ([{**f, "line": 0} for f in scan(category, redact_words)]
            + scan(note["title"] + "\n" + note["body"], redact_words))


def write(method, path, file):
    note = json.loads(Path(file).read_text(encoding="utf-8"))
    findings = note_findings(note, [w.strip() for w in load_lines(REDACT_FILE)])
    if findings:
        print(json.dumps({"findings": findings}))
        return 2
    return call(method, path, json.dumps(note).encode("utf-8"))


def image(file):
    body, content_type = multipart(Path(file).read_bytes(), file)
    return call("POST", "/api/images", body, content_type)


def selftest():
    words = ["acme"]
    assert [f["pattern"] for f in scan("t\nkey AKIAABCDEFGHIJKLMNOP", words)] == ["aws_access_key"]
    assert scan("t\nkey AKIAABCDEFGHIJKLMNOP", words)[0]["line"] == 2
    assert scan("t\npassword=[PASSWORD]", words) == []
    assert scan("t\nACME 사내 시스템", words) == [{"pattern": "redact_word", "line": 2}]
    assert scan("도커 정리\n한글 본문 ✅", words) == []
    assert [f["pattern"] for f in scan("t\nsk-proj-Ab1_cd2-EFghIJklMNopQRstUVwx", words)] == ["openai_key"]
    assert scan("t\npassword: ${DB_PASSWORD}\nString token = request.getHeader(x);", words) == []
    assert note_findings({"title": "t", "categorySlug": "acme", "categoryName": "사내", "body": "b"}, words) == [
        {"pattern": "redact_word", "line": 0}]
    body, content_type = multipart(b"PNGDATA", "a.png")
    boundary = content_type.split("boundary=")[1]
    assert content_type.startswith("multipart/form-data; boundary=")
    assert b'name="file"; filename="a.png"' in body and b"Content-Type: image/png" in body
    assert body.endswith(("--" + boundary + "--\r\n").encode()) and b"PNGDATA" in body
    assert missing_env({}) == ["BLOG_URL", "BLOG_TOKEN"] and missing_env({"BLOG_URL": "u", "BLOG_TOKEN": "t"}) == []
    assert note_path("왕복") == "/api/notes/%EC%99%95%EB%B3%B5"
    assert base_url("http://localhost:8080/") == "http://localhost:8080"
    assert base_url("https://blog.example.com") == "https://blog.example.com"
    for bad in ("http://blog.example.com", "http://localhost.evil.com", "ftp://localhost"):
        try:
            base_url(bad)
            raise AssertionError(bad)
        except ValueError:
            pass
    print("selftest ok")
    return 0


def main(args):
    sys.stdout.reconfigure(encoding="utf-8")
    missing = missing_env(os.environ)
    if args and args[0] != "selftest" and missing:
        print("환경변수가 없습니다: " + ", ".join(missing) + " (README의 '/blog 스킬 설치' 참고, 설정 후 터미널을 다시 켜야 합니다)")
        return 1
    match args:
        case ["list"]:
            return call("GET", "/api/notes")
        case ["get", slug]:
            return call("GET", note_path(slug))
        case ["create", file]:
            return write("POST", "/api/notes", file)
        case ["update", slug, file]:
            return write("PUT", note_path(slug), file)
        case ["image", file]:
            return image(file)
        case ["selftest"]:
            return selftest()
    print(__doc__)
    return 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
