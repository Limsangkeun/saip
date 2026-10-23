// 관리자 편집기: 서버 미리보기(공개 화면과 같은 렌더러), 삭제 확인
import { enhance } from './site.js';

const meta = (name) => document.querySelector(`meta[name="${name}"]`)?.content;
const body = document.getElementById('body');
const preview = document.getElementById('preview');

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
  body.addEventListener('input', () => {
    clearTimeout(timer);
    timer = setTimeout(render, 400);
  });
  render();
}

for (const form of document.querySelectorAll('form[data-confirm]')) {
  form.addEventListener('submit', (e) => {
    if (!confirm(form.dataset.confirm)) e.preventDefault();
  });
}
