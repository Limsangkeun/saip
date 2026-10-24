// 관리자 편집기: 서버 미리보기(공개 화면과 같은 렌더러), 삭제 확인
import { enhance } from './site.js';

const meta = (name) => document.querySelector(`meta[name="${name}"]`)?.content;
const body = document.getElementById('body');
const preview = document.getElementById('preview');
const status = document.getElementById('upload-status');

if (body && preview) {
  let timer;
  let latest = 0;

  const render = async () => {
    const mine = ++latest;
    const res = await fetch('/admin/preview', {
      method: 'POST',
      headers: { 'Content-Type': 'text/plain;charset=UTF-8', [meta('_csrf_header')]: meta('_csrf') },
      body: body.value,
    });
    // 늦게 도착한 예전 응답이 최신 미리보기를 덮어쓰지 않게
    if (!res.ok || mine !== latest) return;
    preview.innerHTML = await res.text(); // 서버가 이스케이프한 HTML만 받는다
    await enhance(preview).catch(() => {}); // 쓰는 중인 Mermaid 문법 오류는 무시
  };

  // 입력이 0.4초 멈추면 그린다(디바운스)
  const schedule = () => {
    clearTimeout(timer);
    timer = setTimeout(render, 400);
  };
  body.addEventListener('input', schedule);
  render();

  // 이미지 붙여넣기·끌어 놓기 → 업로드 → 커서 자리에 마크다운 삽입
  const upload = async (file) => {
    const marker = `![업로드 중: ${file.name || '이미지'}]()`;
    body.setRangeText(marker + '\n', body.selectionStart, body.selectionEnd, 'end');
    const form = new FormData();
    form.append('file', file);
    const res = await fetch('/admin/images', {
      method: 'POST',
      headers: { [meta('_csrf_header')]: meta('_csrf') },
      body: form,
    });
    const result = await res.json().catch(() => ({ error: `업로드 실패 (${res.status})` }));
    // 실패 사유는 본문이 아니라 상태 줄에 띄운다(본문에 넣으면 공개 화면에 글자로 남는다)
    body.value = body.value.replace(marker + '\n', res.ok ? result.markdown + '\n' : '');
    status.textContent = res.ok ? '' : `${file.name || '이미지'}: ${result.error}`;
    schedule();
  };
  const images = (list) => [...list].filter((f) => f.type.startsWith('image/'));
  body.addEventListener('paste', (e) => {
    const files = images(e.clipboardData.files);
    if (!files.length) return;
    e.preventDefault();
    files.forEach(upload);
  });
  body.addEventListener('dragover', (e) => e.preventDefault());
  body.addEventListener('drop', (e) => {
    const files = images(e.dataTransfer.files);
    if (!files.length) return;
    e.preventDefault();
    files.forEach(upload);
  });
}

for (const form of document.querySelectorAll('form[data-confirm]')) {
  form.addEventListener('submit', (e) => {
    if (!confirm(form.dataset.confirm)) e.preventDefault();
  });
}
