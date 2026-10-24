// 노트 본문 꾸미기: 복사 버튼, Mermaid, 코드 색상, 목차
// enhance()는 편집기 미리보기(admin.js)도 같이 쓴다.
export async function enhance(root) {
  // ① 코드 블록 복사 버튼
  for (const code of root.querySelectorAll('pre > code:not(.language-mermaid)')) {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'copy-btn';
    button.textContent = '복사';
    button.addEventListener('click', async () => {
      await navigator.clipboard.writeText(code.textContent);
      button.textContent = '복사됨';
      setTimeout(() => (button.textContent = '복사'), 1500);
    });
    code.parentElement.append(button);
  }

  // ② 코드 블록 색상
  if (window.hljs) {
    for (const code of root.querySelectorAll('pre > code:not(.language-mermaid)')) window.hljs.highlightElement(code);
  }

  // ③ Mermaid 블록을 그림으로 (있을 때만 라이브러리를 불러온다)
  const diagrams = [...root.querySelectorAll('pre > code.language-mermaid')];
  if (diagrams.length) {
    // 12.x: ELK 배치(선이 덜 엉킴)와 redux 테마가 기본. strict라 click·HTML 라벨 스크립트는 막힌다.
    const { default: mermaid } = await import('https://cdn.jsdelivr.net/npm/mermaid@12.1.0/dist/mermaid.esm.min.mjs');
    const dark = matchMedia('(prefers-color-scheme: dark)').matches;
    // 넓은 그림을 본문 폭으로 줄이면 글자를 못 읽는다. 원래 크기로 그리고 넘치면 가로 스크롤(.diagram)
    const natural = { useMaxWidth: false };
    mermaid.initialize({
      startOnLoad: false, theme: dark ? 'redux-dark' : 'redux-color', securityLevel: 'strict',
      flowchart: natural, sequence: natural, er: natural, state: natural, class: natural,
    });
    const nodes = [];
    for (const code of diagrams) {
      const source = code.textContent;
      try {
        await mermaid.parse(source);
      } catch (e) {
        // 문법 오류: 원본 코드를 그대로 두고 사유만 붙인다(쓰는 사람이 고칠 수 있게)
        const error = document.createElement('p');
        error.className = 'diagram-error';
        const lines = String(e.message || e).split('\n');
        error.textContent = '다이어그램 문법 오류: ' + [...new Set([lines[0], lines.at(-1)])].join(' ');
        code.parentElement.after(error);
        continue;
      }
      const figure = document.createElement('figure');
      figure.className = 'diagram';
      const div = document.createElement('div');
      div.className = 'mermaid';
      div.textContent = source;
      figure.append(div, zoomButton(figure));
      code.parentElement.replaceWith(figure);
      nodes.push(div);
    }
    if (nodes.length) await mermaid.run({ nodes });
  }
}

// 넓은 그림은 본문 폭에 맞춰 글자가 작아지므로, 화면 가득 다시 보는 버튼을 단다(네이티브 <dialog>)
function zoomButton(figure) {
  const button = document.createElement('button');
  button.type = 'button';
  button.className = 'copy-btn';
  button.textContent = '크게 보기';
  button.addEventListener('click', () => {
    const svg = figure.querySelector('svg');
    if (!svg) return;
    const home = svg.parentElement;
    const dialog = document.createElement('dialog');
    dialog.className = 'diagram-dialog';
    const close = document.createElement('button');
    close.type = 'button';
    close.className = 'copy-btn';
    close.textContent = '닫기';
    close.addEventListener('click', () => dialog.close());
    dialog.addEventListener('click', (e) => e.target === dialog && dialog.close()); // 바깥 클릭
    // 복제하지 않고 옮겼다가 되돌린다(SVG 안 id가 겹치지 않게)
    dialog.addEventListener('close', () => { home.append(svg); dialog.remove(); });
    dialog.append(close, svg);
    document.body.append(dialog);
    dialog.showModal();
  });
  return button;
}

// ④ h2·h3로 목차 만들기
function buildToc(root) {
  const toc = document.getElementById('toc');
  const headings = root.querySelectorAll('h2[id], h3[id]');
  if (!toc || !headings.length) return;
  const title = document.createElement('div');
  title.className = 'toc-title';
  title.textContent = '목차';
  toc.append(title);
  for (const h of headings) {
    const a = document.createElement('a');
    a.href = '#' + encodeURIComponent(h.id);
    a.textContent = h.textContent;
    a.className = h.tagName.toLowerCase();
    toc.append(a);
  }
}

// ⑤ 좁은 화면 메뉴 버튼 (CSP가 인라인 onclick을 막으므로 여기서 연결)
// ☰: 좁은 화면은 사이드바를 덮어 열고, 넓은 화면은 접고 펴며 상태를 기억한다
document.querySelector('.menu')?.addEventListener('click', () => {
  if (matchMedia('(max-width: 768px)').matches) return document.body.classList.toggle('side-open');
  const closed = document.documentElement.classList.toggle('side-closed');
  try { localStorage.setItem('side', closed ? 'closed' : 'open'); } catch {}
});

const article = document.querySelector('article .markdown');
if (article) {
  buildToc(article);
  await enhance(article);
}
