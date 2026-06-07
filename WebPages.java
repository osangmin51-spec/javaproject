class MiniDashboardPage {
    static String render() {
        return """
                <!doctype html>
                <html lang="ko">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>Java 프로젝트 모의주식</title>
                  <style>
                    :root { --ink:#182230; --muted:#667085; --line:#d7deea; --panel:#fff; --blue:#1f5fbf; --green:#0b7f55; --red:#bd3d3a; }
                    * { box-sizing:border-box; }
                    body { margin:0; font-family:Segoe UI, Arial, sans-serif; color:var(--ink); background:#eef2f7; }
                    header { background:#111d2f; color:#fff; padding:18px 24px; display:flex; justify-content:space-between; align-items:center; gap:16px; position:sticky; top:0; z-index:2; }
                    h1 { margin:0; font-size:22px; letter-spacing:0; }
                    h2 { margin:0; padding:14px 16px; border-bottom:1px solid var(--line); font-size:16px; background:#fbfcfe; }
                    button { border:0; border-radius:6px; padding:10px 13px; background:var(--blue); color:white; font-weight:800; cursor:pointer; }
                    button.secondary { background:#405164; }
                    button.buy { background:var(--green); }
                    button.sell { background:var(--red); }
                    button.ghost { background:#e7edf6; color:#1f2937; }
                    input, select { width:100%; border:1px solid var(--line); border-radius:6px; padding:10px; font-size:14px; background:white; }
                    label { display:grid; gap:6px; color:var(--muted); font-size:12px; font-weight:800; text-transform:uppercase; }
                    table { width:100%; border-collapse:collapse; font-size:14px; }
                    th, td { padding:11px 12px; border-bottom:1px solid #edf1f7; text-align:left; vertical-align:middle; }
                    th { color:var(--muted); font-size:12px; }
                    main { max-width:1440px; margin:0 auto; padding:20px; display:grid; gap:16px; }
                    section { background:var(--panel); border:1px solid var(--line); border-radius:8px; overflow:hidden; }
                    .topbar { display:flex; gap:10px; align-items:center; flex-wrap:wrap; }
                    .pill { border:1px solid rgba(255,255,255,.22); border-radius:999px; padding:7px 10px; color:#dbe4ef; font-size:13px; }
                    .hero { display:grid; grid-template-columns:1fr; gap:16px; align-items:stretch; max-width:920px; }
                    .auth { display:grid; grid-template-columns:1fr 1fr; gap:12px; }
                    .auth form { display:grid; gap:10px; padding:16px; }
                    .summary { display:grid; grid-template-columns:repeat(5,minmax(0,1fr)); gap:12px; }
                    .metric { background:#fff; border:1px solid var(--line); border-radius:8px; padding:14px; min-height:86px; }
                    .labelText { color:var(--muted); font-size:12px; font-weight:800; text-transform:uppercase; }
                    .value { margin-top:8px; font-size:24px; font-weight:850; }
                    .layout { display:grid; grid-template-columns:1fr; gap:16px; align-items:start; }
                    .workspace { display:grid; gap:16px; }
                    .tradeBox { display:grid; grid-template-columns:1fr 110px; gap:10px; padding:16px; align-items:end; }
                    .actions { display:flex; gap:8px; flex-wrap:wrap; }
                    .message { color:var(--muted); padding:0 16px 14px; min-height:22px; }
                    .up { color:var(--green); } .down { color:var(--red); }
                    .tabs { display:flex; gap:8px; padding:12px 12px 0; flex-wrap:wrap; }
                    .tab { background:#e7edf6; color:#1f2937; }
                    .tab.active { background:var(--blue); color:#fff; }
                    .tabPanel { display:none; }
                    .tabPanel.active { display:block; }
                    .cards { display:grid; gap:10px; padding:16px; }
                    .card { border:1px solid var(--line); border-radius:8px; padding:12px; background:#fbfcfe; display:grid; gap:8px; }
                    .postHead { display:flex; justify-content:space-between; gap:10px; align-items:flex-start; }
                    .postForm { display:grid; grid-template-columns:1fr; gap:10px; padding:16px; border-bottom:1px solid var(--line); }
                    .commentForm { display:grid; grid-template-columns:1fr auto; gap:8px; }
                    .comments { color:var(--muted); font-size:13px; display:grid; gap:4px; }
                    .empty { color:var(--muted); padding:16px; }
                    .detailGrid { display:grid; grid-template-columns:repeat(4,minmax(0,1fr)); gap:10px; padding:16px; }
                    .companyInfo { padding:0 16px 16px; color:#344054; line-height:1.55; }
                    .chartWrap { margin:0 16px 16px; border:1px solid var(--line); border-radius:8px; background:#fbfcfe; padding:12px; }
                    .priceChart { width:100%; height:210px; display:block; }
                    .chartLine { fill:none; stroke:var(--blue); stroke-width:3; stroke-linecap:round; stroke-linejoin:round; }
                    .chartArea { fill:rgba(31,95,191,.08); }
                    .chartGrid { stroke:#dbe4ef; stroke-width:1; }
                    .chartPoint { fill:#fff; stroke:var(--blue); stroke-width:2; }
                    .chartMeta { display:flex; justify-content:space-between; gap:10px; color:var(--muted); font-size:12px; flex-wrap:wrap; }
                    .newsList { display:grid; gap:10px; padding:0 16px 16px; }
                    .newsItem { border:1px solid var(--line); border-radius:8px; padding:12px; background:#fbfcfe; display:grid; gap:6px; }
                    .newsItem a { color:var(--blue); font-weight:850; text-decoration:none; }
                    .newsItem p { margin:0; color:#344054; line-height:1.45; }
                    .newsMeta { color:var(--muted); font-size:12px; }
                    .impactRow { display:flex; gap:8px; align-items:center; flex-wrap:wrap; }
                    .impactBadge { border-radius:999px; padding:4px 8px; font-size:12px; font-weight:850; }
                    .impactGood { background:#e7f7ef; color:var(--green); }
                    .impactBad { background:#fdeceb; color:var(--red); }
                    .impactNeutral { background:#edf1f7; color:#405164; }
                    @media (max-width: 1100px) { .hero, .layout { grid-template-columns:1fr; } .summary { grid-template-columns:repeat(2,1fr); } }
                    @media (max-width: 720px) { header { align-items:flex-start; flex-direction:column; } .auth, .summary, .tradeBox, .detailGrid { grid-template-columns:1fr; } main { padding:12px; } }
                  </style>
                </head>
                <body>
                  <header>
                    <div>
                      <h1>Java 프로젝트 모의주식</h1>
                      <div class="labelText">실시간 가격 구독과 포트폴리오 손익을 확인하는 Java 웹앱</div>
                    </div>
                    <div class="topbar">
                      <span class="pill" id="loginPill">로그인 필요</span>
                      <button onclick="refresh()">새로고침</button>
                      <button class="secondary" onclick="logout()">로그아웃</button>
                    </div>
                  </header>

                  <main>
                    <div class="hero" id="authArea">
                      <section>
                        <h2>로그인 / 회원가입</h2>
                        <div class="auth">
                          <form id="loginForm">
                            <label>아이디<input name="id" value="test1"></label>
                            <label>비밀번호<input name="pwd" type="password" value="1234"></label>
                            <button>로그인</button>
                          </form>
                          <form id="registerForm">
                            <label>이름<input name="name" placeholder="홍길동"></label>
                            <label>아이디<input name="id" placeholder="새 아이디"></label>
                            <label>비밀번호<input name="pwd" type="password"></label>
                            <button class="secondary">회원가입</button>
                          </form>
                        </div>
                        <div class="message" id="loginMessage"></div>
                      </section>
                    </div>

                    <div class="summary" id="summary"></div>

                    <div class="layout">
                      <div class="workspace">
                        <section>
                          <h2>매매</h2>
                          <form id="tradeForm" class="tradeBox">
                            <label>종목 선택<select name="stockName" id="stockSelect"></select></label>
                            <label>수량<input name="quantity" type="number" min="1" value="1"></label>
                            <div class="actions">
                              <button class="buy" name="side" value="buy">구매</button>
                              <button class="sell" name="side" value="sell">판매</button>
                              <button type="button" class="ghost" onclick="nextDay()">다음날</button>
                            </div>
                          </form>
                          <div class="message" id="tradeMessage"></div>
                          <table>
                            <thead><tr><th>종목</th><th>가격</th><th>거래량</th><th>변동폭</th><th>변동률</th></tr></thead>
                            <tbody id="stocks"></tbody>
                          </table>
                        </section>

                        <section>
                          <h2>종목 상세 / 뉴스</h2>
                          <div class="detailGrid" id="stockDetail"></div>
                          <div class="companyInfo" id="companyInfo"></div>
                          <div class="chartWrap">
                            <svg class="priceChart" id="priceChart" viewBox="0 0 640 210" role="img" aria-label="종목 가격 변화 추이"></svg>
                            <div class="chartMeta" id="chartMeta"></div>
                          </div>
                          <div class="newsList" id="newsList"></div>
                        </section>

                        <section>
                          <div class="tabs">
                            <button class="tab active" onclick="showTab('portfolio')">보유</button>
                            <button class="tab" onclick="showTab('logs')">기록</button>
                            <button class="tab" onclick="showTab('board')">게시판</button>
                          </div>
                          <div class="tabPanel active" id="panel-portfolio">
                            <table><thead><tr><th>종목</th><th>수량</th><th>평단가</th><th>현재가</th><th>평가금액</th><th>손익</th><th>수익률</th></tr></thead><tbody id="shares"></tbody></table>
                          </div>
                          <div class="tabPanel" id="panel-logs">
                            <table><thead><tr><th>시간</th><th>구분</th><th>종목</th><th>수량</th><th>금액</th></tr></thead><tbody id="logs"></tbody></table>
                          </div>
                          <div class="tabPanel" id="panel-board">
                            <form id="postForm" class="postForm">
                              <label>제목<input name="title" placeholder="투자 메모 제목"></label>
                              <label>내용<input name="content" placeholder="오늘의 전략이나 느낀 점"></label>
                              <button>게시글 작성</button>
                            </form>
                            <div class="cards" id="posts"></div>
                          </div>
                        </section>
                        <div class="message" id="dayMessage"></div>
                      </div>
                    </div>
                  </main>

                  <script>
                    let state = {};
                    let selectedStockName = '';
                    let selectedNews = null;
                    let refreshing = false;
                    const won = n => Number(n || 0).toLocaleString('ko-KR') + '원';
                    const count = n => Number(n || 0).toLocaleString('ko-KR');
                    const html = v => String(v ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
                    async function api(path, body) {
                      const options = body ? {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(body)} : {};
                      const res = await fetch(path, options);
                      const data = await res.json();
                      if (!res.ok || data.ok === false) throw new Error(data.error || '요청 실패');
                      return data;
                    }
                    async function refresh() {
                      if (refreshing) return;
                      refreshing = true;
                      try {
                        state = await api('/api/state');
                        render();
                      } finally {
                        refreshing = false;
                      }
                    }
                    function stockByName(name) {
                      return (state.stocks || []).find(stock => stock.name === name);
                    }
                    function render() {
                      const logged = !!state.loggedIn;
                      const member = state.member || {};
                      const portfolio = state.portfolio || {};
                      document.getElementById('authArea').style.display = logged ? 'none' : 'grid';
                      document.getElementById('loginPill').textContent = logged ? `${member.id} · ${member.day}일차` : '로그인 필요';
                      document.getElementById('summary').innerHTML = [
                        ['보유 현금', logged ? won(portfolio.cash) : '-', ''],
                        ['주식 평가액', logged ? won(portfolio.stockValue) : '-', ''],
                        ['총 자산', logged ? won(portfolio.totalAsset) : '-', ''],
                        ['실시간 손익', logged ? won(portfolio.profit) : '-', Number(portfolio.profit || 0) >= 0 ? 'up' : 'down'],
                        ['수익률', logged ? `${portfolio.profitRate}%` : '-', Number(portfolio.profit || 0) >= 0 ? 'up' : 'down']
                      ].map(([label, value, cls]) => `<div class="metric"><div class="labelText">${label}</div><div class="value ${cls}">${value}</div></div>`).join('');
                      if (!selectedStockName && (state.stocks || []).length) selectedStockName = state.stocks[0].name;
                      renderStocks();
                      renderSelectedStock();
                      renderShares();
                      renderLogs();
                      renderPosts();
                    }
                    function renderStocks() {
                      document.getElementById('stocks').innerHTML = (state.stocks || []).map(stock => `<tr>
                        <td><button type="button" class="ghost" onclick="selectStock('${html(stock.name)}')">${html(stock.name)}</button></td>
                        <td>${won(stock.price)}<div class="newsMeta">${html(stock.quoteSource || '초기 데이터')}</div></td>
                        <td>${count(stock.tradingVolume || stock.quantity)}</td>
                        <td class="${stock.priceFluct>=0?'up':'down'}">${won(stock.priceFluct)}</td>
                        <td class="${stock.priceFluct>=0?'up':'down'}">${stock.changeRate}%</td>
                      </tr>`).join('');
                      document.getElementById('stockSelect').innerHTML = (state.stocks || []).map(stock => `<option value="${html(stock.name)}">${html(stock.name)} · ${won(stock.price)}</option>`).join('');
                      if (selectedStockName) document.getElementById('stockSelect').value = selectedStockName;
                    }
                    function renderSelectedStock() {
                      const stock = stockByName(selectedStockName) || (state.stocks || [])[0];
                      if (!stock) {
                        document.getElementById('stockDetail').innerHTML = '<div class="empty">종목이 없습니다.</div>';
                        document.getElementById('companyInfo').innerHTML = '';
                        document.getElementById('priceChart').innerHTML = '<text x="320" y="105" text-anchor="middle" fill="#667085">가격 데이터가 없습니다.</text>';
                        document.getElementById('chartMeta').innerHTML = '';
                        document.getElementById('newsList').innerHTML = '';
                        return;
                      }
                      const positive = Number(stock.priceFluct || 0) >= 0;
                      document.getElementById('stockDetail').innerHTML = [
                        ['선택 종목', stock.name, ''],
                        ['종목 코드', stock.code, ''],
                        ['시장', stock.market, ''],
                        ['업종', stock.sector, ''],
                        ['현재가', won(stock.price), ''],
                        ['변동폭', won(stock.priceFluct), positive ? 'up' : 'down'],
                        ['변동률', `${stock.changeRate}%`, positive ? 'up' : 'down'],
                        ['거래량', count(stock.tradingVolume || stock.quantity), ''],
                        ['시세 출처', stock.quoteSource || '초기 데이터', ''],
                        ['갱신 시각', stock.lastUpdated || 'KIS 갱신 대기', '']
                      ].map(([label, value, cls]) => `<div class="metric"><div class="labelText">${label}</div><div class="value ${cls}">${html(value)}</div></div>`).join('');
                      document.getElementById('companyInfo').innerHTML = html(stock.description || '회사 정보가 없습니다.');
                      renderPriceChart(stock);
                      renderNews();
                    }
                    function renderPriceChart(stock) {
                      const svg = document.getElementById('priceChart');
                      const meta = document.getElementById('chartMeta');
                      const history = (stock.history || []).filter(point => Number(point.price) > 0);
                      if (history.length < 2) {
                        svg.innerHTML = '<text x="320" y="105" text-anchor="middle" fill="#667085">실시간 가격이 더 쌓이면 추이 그래프가 표시됩니다.</text>';
                        meta.innerHTML = '<span>가격 포인트 1개 이하</span>';
                        return;
                      }
                      const width = 640;
                      const height = 210;
                      const pad = 24;
                      const prices = history.map(point => Number(point.price));
                      const min = Math.min(...prices);
                      const max = Math.max(...prices);
                      const range = Math.max(1, max - min);
                      const points = history.map((point, index) => {
                        const x = pad + (index * (width - pad * 2)) / Math.max(1, history.length - 1);
                        const y = height - pad - ((Number(point.price) - min) * (height - pad * 2)) / range;
                        return {x, y, price:Number(point.price), time:point.time};
                      });
                      const line = points.map(point => `${point.x.toFixed(1)},${point.y.toFixed(1)}`).join(' ');
                      const area = `${pad},${height - pad} ${line} ${width - pad},${height - pad}`;
                      const guideY = [pad, height / 2, height - pad].map(y => `<line class="chartGrid" x1="${pad}" y1="${y}" x2="${width - pad}" y2="${y}"></line>`).join('');
                      const circles = points.slice(-8).map(point => `<circle class="chartPoint" cx="${point.x.toFixed(1)}" cy="${point.y.toFixed(1)}" r="3"><title>${html(point.time)} ${won(point.price)}</title></circle>`).join('');
                      svg.innerHTML = `${guideY}<polygon class="chartArea" points="${area}"></polygon><polyline class="chartLine" points="${line}"></polyline>${circles}`;
                      const latest = history[history.length - 1];
                      const first = history[0];
                      const diff = Number(latest.price) - Number(first.price);
                      const cls = diff >= 0 ? 'up' : 'down';
                      meta.innerHTML = `<span>최근 ${history.length}개 가격 포인트</span><span>최저 ${won(min)} · 최고 ${won(max)}</span><span class="${cls}">${first.time} 대비 ${won(diff)}</span>`;
                    }
                    function renderShares() {
                      document.getElementById('shares').innerHTML = (state.shares || []).map(share => {
                        const positive = Number(share.profit || 0) >= 0;
                        return `<tr><td>${html(share.stockName)}</td><td>${share.quantity}</td><td>${won(share.averagePrice)}</td><td>${won(share.currentPrice)}</td><td>${won(share.value)}</td><td class="${positive?'up':'down'}">${won(share.profit)}</td><td class="${positive?'up':'down'}">${share.profitRate}%</td></tr>`;
                      }).join('') || '<tr><td colspan="7" class="empty">보유 주식이 없습니다.</td></tr>';
                    }
                    function renderLogs() {
                      document.getElementById('logs').innerHTML = (state.logs || []).map(log => `<tr><td>${log.time}</td><td>${log.type}</td><td>${html(log.stockName)}</td><td>${log.quantity}</td><td>${won(log.price)}</td></tr>`).join('') || '<tr><td colspan="5" class="empty">거래 기록이 없습니다.</td></tr>';
                    }
                    function renderNews() {
                      const box = document.getElementById('newsList');
                      if (!selectedNews || selectedNews.stockName !== selectedStockName) {
                        box.innerHTML = '<div class="empty">종목을 클릭하면 관련 뉴스가 표시됩니다.</div>';
                        return;
                      }
                      const items = selectedNews.items || [];
                      const head = `<div class="message">${html(selectedNews.source)} · ${html(selectedNews.message)}</div>`;
                      box.innerHTML = head + (items.length ? items.map(item => {
                        const impact = item.impact || '중립';
                        const impactClass = impact.includes('호재') ? 'impactGood' : impact.includes('악재') ? 'impactBad' : 'impactNeutral';
                        return `<article class="newsItem">
                          <a href="${html(item.link)}" target="_blank" rel="noopener noreferrer">${html(item.title)}</a>
                          <div class="impactRow"><span class="impactBadge ${impactClass}">${html(impact)}</span><span class="newsMeta">${html(item.impactReason || '')}</span></div>
                          <p>${html(item.description)}</p>
                          <div class="newsMeta">${html(item.pubDate)}</div>
                        </article>`;
                      }).join('') : '<div class="empty">표시할 뉴스가 없습니다.</div>');
                    }
                    async function selectStock(name) {
                      selectedStockName = name;
                      selectedNews = null;
                      document.getElementById('stockSelect').value = name;
                      renderSelectedStock();
                      try {
                        selectedNews = await api('/api/news?stockName=' + encodeURIComponent(name));
                        renderNews();
                      } catch (err) {
                        selectedNews = {stockName:name, source:'뉴스', message:err.message, items:[]};
                        renderNews();
                      }
                    }
                    function renderPosts() {
                      document.getElementById('posts').innerHTML = (state.posts || []).map(post => `<div class="card">
                        <div class="postHead"><strong>${html(post.title)}</strong><span class="labelText">${html(post.author)} · ${post.createdAt}</span></div>
                        <div>${html(post.content)}</div>
                        <div class="comments">${(post.comments || []).map(comment => `<div>${html(comment.author)}: ${html(comment.content)}</div>`).join('')}</div>
                        <div class="commentForm"><input id="comment-${post.id}" placeholder="댓글 입력"><button onclick="comment(${post.id})">댓글</button></div>
                      </div>`).join('') || '<div class="empty">게시글이 없습니다.</div>';
                    }
                    function showTab(name) {
                      const labels = {portfolio:'보유', logs:'기록', board:'게시판'};
                      document.querySelectorAll('.tab').forEach(tab => tab.classList.remove('active'));
                      document.querySelectorAll('.tabPanel').forEach(panel => panel.classList.remove('active'));
                      document.querySelectorAll('.tab').forEach(tab => { if (tab.textContent === labels[name]) tab.classList.add('active'); });
                      document.getElementById('panel-' + name).classList.add('active');
                    }
                    async function submitForm(form, path) {
                      return api(path, Object.fromEntries(new FormData(form).entries()));
                    }
                    document.getElementById('loginForm').addEventListener('submit', async e => {
                      e.preventDefault();
                      try { const result = await submitForm(e.target, '/api/login'); document.getElementById('loginMessage').textContent = result.message; await refresh(); }
                      catch (err) { document.getElementById('loginMessage').textContent = err.message; }
                    });
                    document.getElementById('registerForm').addEventListener('submit', async e => {
                      e.preventDefault();
                      try { const result = await submitForm(e.target, '/api/register'); document.getElementById('loginMessage').textContent = result.message; e.target.reset(); await refresh(); }
                      catch (err) { document.getElementById('loginMessage').textContent = err.message; }
                    });
                    document.getElementById('tradeForm').addEventListener('submit', async e => {
                      e.preventDefault();
                      const payload = Object.fromEntries(new FormData(e.target).entries());
                      try { const result = await api(e.submitter.value === 'buy' ? '/api/stock/buy' : '/api/stock/sell', payload); document.getElementById('tradeMessage').textContent = result.message; await refresh(); }
                      catch (err) { document.getElementById('tradeMessage').textContent = err.message; }
                    });
                    document.getElementById('stockSelect').addEventListener('change', e => selectStock(e.target.value));
                    document.getElementById('postForm').addEventListener('submit', async e => {
                      e.preventDefault();
                      try { await submitForm(e.target, '/api/board/write'); e.target.reset(); await refresh(); showTab('board'); }
                      catch (err) { alert(err.message); }
                    });
                    async function logout() { await api('/api/logout', {}); await refresh(); }
                    async function nextDay() { try { const result = await api('/api/day/next', {}); document.getElementById('dayMessage').textContent = result.message; await refresh(); } catch (err) { document.getElementById('dayMessage').textContent = err.message; } }
                    async function comment(postId) {
                      const input = document.getElementById('comment-' + postId);
                      if (!input.value.trim()) return;
                      await api('/api/comment/write', {postId, content:input.value});
                      input.value = '';
                      await refresh();
                      showTab('board');
                    }
                    refresh();
                    setInterval(refresh, 1000);
                  </script>
                </body>
                </html>
                """;
    }
}
