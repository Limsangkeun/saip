// 노트 본문 꾸미기: 복사 버튼, Mermaid, 코드 색상, 목차
const body = document.querySelector('.markdown');

if (body) {
  // ① 코드 블록 복사 버튼
  for (const code of body.querySelectorAll('pre > code:not(.language-mermaid)')) {
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

  // ② Mermaid 블록을 그림으로 (있을 때만 라이브러리를 불러온다)
  const diagrams = [...body.querySelectorAll('pre > code.language-mermaid')];
  if (diagrams.length) {
    for (const code of diagrams) {
      const div = document.createElement('div');
      div.className = 'mermaid';
      div.textContent = code.textContent;
      code.parentElement.replaceWith(div);
    }
    const { default: mermaid } = await import('https://cdn.jsdelivr.net/npm/mermaid@11.4.1/dist/mermaid.esm.min.mjs');
    const dark = matchMedia('(prefers-color-scheme: dark)').matches;
    mermaid.initialize({ startOnLoad: false, theme: dark ? 'dark' : 'default', securityLevel: 'strict' });
    await mermaid.run({ querySelector: '.mermaid' });
  }

  // ③ 나머지 코드 블록 색상
  if (window.hljs) {
    for (const code of body.querySelectorAll('pre > code')) window.hljs.highlightElement(code);
  }

  // ④ h2·h3로 목차 만들기
  const toc = document.getElementById('toc');
  const headings = body.querySelectorAll('h2[id], h3[id]');
  if (toc && headings.length) {
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
}
