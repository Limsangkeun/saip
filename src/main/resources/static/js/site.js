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
    const nodes = diagrams.map((code) => {
      const div = document.createElement('div');
      div.className = 'mermaid';
      div.textContent = code.textContent;
      code.parentElement.replaceWith(div);
      return div;
    });
    const { default: mermaid } = await import('https://cdn.jsdelivr.net/npm/mermaid@11.4.1/dist/mermaid.esm.min.mjs');
    const dark = matchMedia('(prefers-color-scheme: dark)').matches;
    mermaid.initialize({ startOnLoad: false, theme: dark ? 'dark' : 'default', securityLevel: 'strict' });
    await mermaid.run({ nodes });
  }
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

const article = document.querySelector('article .markdown');
if (article) {
  buildToc(article);
  await enhance(article);
}
