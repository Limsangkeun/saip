"""블로그 API 클라이언트. Claude Code /blog 스킬이 호출한다. 표준 라이브러리만 사용.

python blog.py list | get <slug> | create <file.json> | update <slug> <file.json> | selftest
환경변수: BLOG_URL, BLOG_TOKEN
종료 코드: 0 성공, 1 오류, 2 민감 정보 걸림(로컬 검사 또는 서버 422)
"""
import json
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


def call(method, path, body=None):
    req = urllib.request.Request(
        os.environ["BLOG_URL"].rstrip("/") + path,
        method=method,
        data=json.dumps(body).encode("utf-8") if body is not None else None,
        headers={"Authorization": "Bearer " + os.environ["BLOG_TOKEN"], "Content-Type": "application/json"},
    )
    try:
        with urllib.request.urlopen(req) as res:
            print(res.read().decode("utf-8"))
            return 0
    except urllib.error.HTTPError as e:
        print(json.dumps({"status": e.code, "response": e.read().decode("utf-8")}, ensure_ascii=False))
        return 2 if e.code == 422 else 1


def write(method, path, file):
    note = json.loads(Path(file).read_text(encoding="utf-8"))
    # 1번 줄은 제목, 본문은 2번 줄부터 (서버 422 응답과 같은 번호)
    findings = scan(note["title"] + "\n" + note["body"], [w.strip() for w in load_lines(REDACT_FILE)])
    if findings:
        print(json.dumps({"findings": findings}))
        return 2
    return call(method, path, note)


def selftest():
    words = ["acme"]
    assert [f["pattern"] for f in scan("t\nkey AKIAABCDEFGHIJKLMNOP", words)] == ["aws_access_key"]
    assert scan("t\nkey AKIAABCDEFGHIJKLMNOP", words)[0]["line"] == 2
    assert scan("t\npassword=[PASSWORD]", words) == []
    assert scan("t\nACME 사내 시스템", words) == [{"pattern": "redact_word", "line": 2}]
    assert scan("도커 정리\n한글 본문 ✅", words) == []
    assert note_path("왕복") == "/api/notes/%EC%99%95%EB%B3%B5"
    print("selftest ok")
    return 0


def main(args):
    sys.stdout.reconfigure(encoding="utf-8")
    match args:
        case ["list"]:
            return call("GET", "/api/notes")
        case ["get", slug]:
            return call("GET", note_path(slug))
        case ["create", file]:
            return write("POST", "/api/notes", file)
        case ["update", slug, file]:
            return write("PUT", note_path(slug), file)
        case ["selftest"]:
            return selftest()
    print(__doc__)
    return 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
