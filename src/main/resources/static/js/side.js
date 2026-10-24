// 넓은 화면에서 접어 둔 사이드바를 그리기 전에 적용한다(깜빡임 방지). 버튼 처리는 site.js.
try { if (localStorage.getItem('side') === 'closed') document.documentElement.classList.add('side-closed'); } catch {}
