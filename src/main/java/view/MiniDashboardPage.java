package view;

public class MiniDashboardPage {
    public static String render() {
        return """
                <!doctype html>
                <html lang="ko">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>Java 프로젝트 모의주식</title>
                  <style>
                    :root { --ink:#1f252d; --muted:#6b7280; --line:#ccd3dd; --panel:#fff; --soft:#f5f7fa; --blue:#2457a7; --green:#047857; --red:#c2413d; --amber:#b7791f; }
                    * { box-sizing:border-box; }
                    body { margin:0; font-family:Segoe UI, Arial, sans-serif; color:var(--ink); background:#e9edf2; }
                    header { background:#ffffff; color:var(--ink); border-bottom:1px solid #c9d1dc; padding:14px 22px; display:flex; justify-content:space-between; align-items:center; gap:16px; position:sticky; top:0; z-index:2; box-shadow:0 1px 0 rgba(20,30,44,.04); }
                    h1 { margin:0; font-size:21px; letter-spacing:0; font-weight:850; }
                    h2 { margin:0; padding:12px 14px; border-bottom:1px solid var(--line); font-size:14px; background:#f7f9fc; display:flex; align-items:center; gap:9px; }
                    h2::before { content:""; width:4px; height:16px; background:var(--blue); display:inline-block; }
                    button { border:1px solid transparent; border-radius:4px; padding:9px 12px; background:var(--blue); color:white; font-weight:800; cursor:pointer; }
                    button.secondary { background:#405164; }
                    button.buy { background:var(--green); }
                    button.sell { background:var(--red); }
                    button.ghost { background:#eef2f7; color:#1f2937; border-color:#d3dae5; }
                    input, select { width:100%; border:1px solid var(--line); border-radius:4px; padding:10px; font-size:14px; background:white; }
                    label { display:grid; gap:6px; color:var(--muted); font-size:12px; font-weight:800; text-transform:uppercase; }
                    table { width:100%; border-collapse:collapse; font-size:14px; }
                    th, td { padding:10px 12px; border-bottom:1px solid #e5eaf0; text-align:left; vertical-align:middle; }
                    th { color:var(--muted); font-size:11px; background:#f7f9fc; position:sticky; top:0; z-index:1; }
                    main { max-width:1500px; margin:0 auto; padding:16px; display:grid; gap:14px; }
                    section { background:var(--panel); border:1px solid var(--line); border-radius:6px; overflow:hidden; box-shadow:0 1px 2px rgba(16,24,40,.04); }
                    .brandBlock { display:grid; gap:3px; }
                    .topbar { display:flex; gap:10px; align-items:center; flex-wrap:wrap; }
                    .pill { border:1px solid #d0d7e2; border-radius:999px; padding:7px 11px; color:#344054; background:#f7f9fc; font-size:13px; }
                    .summary { display:grid; grid-template-columns:repeat(6,minmax(0,1fr)); gap:8px; border:1px solid var(--line); background:#fff; border-radius:6px; overflow:hidden; }
                    .metric { background:#fff; border-right:1px solid #e4e9f0; padding:12px 14px; min-height:78px; }
                    .metric:last-child { border-right:0; }
                    .labelText { color:var(--muted); font-size:11px; font-weight:850; text-transform:uppercase; }
                    .value { margin-top:7px; font-size:22px; font-weight:850; overflow-wrap:anywhere; }
                    .value.time { font-size:17px; line-height:1.35; }
                    .layout { display:grid; grid-template-columns:1fr; gap:16px; align-items:start; }
                    .workspace { display:grid; grid-template-columns:minmax(420px,.95fr) minmax(0,1.35fr); grid-template-areas:"market detail" "lower lower"; gap:14px; align-items:start; }
                    .marketPanel { grid-area:market; }
                    .detailPanel { grid-area:detail; }
                    .lowerPanel { grid-area:lower; }
                    .actions { display:flex; gap:8px; flex-wrap:wrap; }
                    .message { color:var(--muted); padding:0 16px 14px; min-height:22px; }
                    .up { color:var(--green); } .down { color:var(--red); }
                    .marketRow { cursor:pointer; }
                    .marketRow:hover { background:#f8fafc; }
                    .marketRow.selected { background:#eef5ff; box-shadow:inset 4px 0 0 var(--blue); }
                    .tabs { display:flex; gap:6px; padding:10px 10px 0; flex-wrap:wrap; background:#fff; }
                    .tab { background:#eef2f7; color:#1f2937; border-color:#d3dae5; }
                    .tab.active { background:#1f252d; color:#fff; }
                    .marketTools { display:flex; justify-content:space-between; gap:10px; align-items:center; flex-wrap:wrap; padding:12px 16px; border-top:1px solid var(--line); }
                    .marketFilters { display:grid; grid-template-columns:minmax(220px,1fr) auto; gap:10px; padding:12px 16px; border-bottom:1px solid var(--line); align-items:center; }
                    .marketFilters input { min-width:0; }
                    .pageButtons { display:flex; gap:6px; flex-wrap:wrap; }
                    .pageButtons button { min-width:34px; padding:7px 9px; background:#eef2f7; color:#1f2937; border-color:#d3dae5; }
                    .pageButtons button.active { background:#1f252d; color:#fff; }
                    .favoriteToggle.active { background:#1f252d; color:white; }
                    .favoriteButton { width:30px; height:30px; padding:0; display:inline-grid; place-items:center; background:#fff; color:#667085; border:1px solid #d7deea; }
                    .favoriteButton.active { background:#fff8e8; color:var(--amber); border-color:#e4b763; }
                    .stockNameCell { display:flex; align-items:center; gap:8px; min-width:210px; }
                    .stockTitle { display:grid; gap:2px; }
                    .stockTitle strong { font-size:14px; }
                    .stockTitle span { color:var(--muted); font-size:11px; }
                    .quoteButton { padding:6px 9px; font-size:12px; }
                    .tabPanel { display:none; }
                    .tabPanel.active { display:block; }
                    .cards { display:grid; gap:10px; padding:16px; }
                    .card { border:1px solid var(--line); border-radius:6px; padding:12px; background:#fbfcfe; display:grid; gap:8px; }
                    .empty { color:var(--muted); padding:16px; }
                    .detailGrid { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:8px; padding:14px; }
                    .companyInfo { padding:0 16px 16px; color:#344054; line-height:1.55; }
                    .orderPanel { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:12px; padding:0 16px 16px; }
                    .orderCard { border:1px solid var(--line); border-radius:6px; background:#fbfcfe; padding:12px; display:grid; gap:10px; }
                    .inlineOrder { display:grid; grid-template-columns:minmax(72px,1fr) auto; gap:8px; align-items:center; min-width:190px; }
                    .chartWrap { margin:0 16px 16px; border:1px solid var(--line); border-radius:6px; background:#fbfcfe; padding:12px; }
                    .priceChart { width:100%; height:210px; display:block; }
                    .chartLine { fill:none; stroke:var(--blue); stroke-width:3; stroke-linecap:round; stroke-linejoin:round; }
                    .chartArea { fill:rgba(31,95,191,.08); }
                    .chartGrid { stroke:#dbe4ef; stroke-width:1; }
                    .chartPoint { fill:#fff; stroke:var(--blue); stroke-width:2; }
                    .chartMeta { display:flex; justify-content:space-between; gap:10px; color:var(--muted); font-size:12px; flex-wrap:wrap; }
                    .subMeta { color:var(--muted); font-size:12px; }
                    @media (max-width: 1100px) { .workspace { grid-template-columns:1fr; grid-template-areas:"market" "detail" "lower"; } .summary { grid-template-columns:repeat(2,1fr); } .metric { border-right:0; border-bottom:1px solid #e4e9f0; } }
                    @media (max-width: 720px) { header { align-items:flex-start; flex-direction:column; } .summary, .detailGrid, .orderPanel { grid-template-columns:1fr; } main { padding:12px; } }
                  </style>
                </head>
                <body>
                  <header>
                    <div class="brandBlock">
                      <h1>Java 프로젝트 모의주식</h1>
                      <div class="labelText">시장 목록에서 종목을 고르고 바로 매수·매도하는 모의투자 터미널</div>
                    </div>
                    <div class="topbar">
                      <span class="pill" id="statusPill">시세 갱신 대기</span>
                      <button onclick="refresh()">새로고침</button>
                    </div>
                  </header>

                  <main>
                    <div class="summary" id="summary"></div>

                    <div class="layout">
                      <div class="workspace">
                        <section class="marketPanel">
                          <h2>시장 시세</h2>
                          <div class="marketFilters">
                            <input id="stockSearch" type="search" placeholder="종목명, 코드, 업종 검색" oninput="setMarketSearch(this.value)">
                            <button id="favoriteOnlyButton" class="ghost favoriteToggle" type="button" onclick="toggleFavoriteOnly()">즐겨찾기만</button>
                          </div>
                          <table>
                            <thead><tr><th>종목</th><th>가격</th><th>거래량</th><th>변동폭</th><th>변동률</th></tr></thead>
                            <tbody id="stocks"></tbody>
                          </table>
                          <div class="marketTools">
                            <div class="labelText" id="marketPageInfo">1-10 / 0</div>
                            <div class="pageButtons" id="marketPages"></div>
                          </div>
                        </section>

                        <section class="detailPanel">
                          <h2>종목 상세</h2>
                          <div class="detailGrid" id="stockDetail"></div>
                          <div class="companyInfo" id="companyInfo"></div>
                          <div class="orderPanel" id="orderPanel"></div>
                          <div class="message" id="tradeMessage"></div>
                          <div class="chartWrap">
                            <svg class="priceChart" id="priceChart" viewBox="0 0 640 210" role="img" aria-label="종목 가격 변화 추이"></svg>
                            <div class="chartMeta" id="chartMeta"></div>
                          </div>
                        </section>

                        <section class="lowerPanel">
                          <div class="tabs">
                            <button class="tab active" onclick="showTab('portfolio')">보유</button>
                            <button class="tab" onclick="showTab('favorites')">즐겨찾기</button>
                            <button class="tab" onclick="showTab('logs')">기록</button>
                          </div>
                          <div class="tabPanel active" id="panel-portfolio">
                            <table><thead><tr><th>종목</th><th>수량</th><th>평단가</th><th>현재가</th><th>평가금액</th><th>손익</th><th>수익률</th><th>매도</th></tr></thead><tbody id="shares"></tbody></table>
                          </div>
                          <div class="tabPanel" id="panel-favorites">
                            <table><thead><tr><th>종목</th><th>가격</th><th>거래량</th><th>변동률</th><th>관리</th></tr></thead><tbody id="favorites"></tbody></table>
                          </div>
                          <div class="tabPanel" id="panel-logs">
                            <table><thead><tr><th>시간</th><th>구분</th><th>종목</th><th>수량</th><th>금액</th></tr></thead><tbody id="logs"></tbody></table>
                          </div>
                        </section>
                      </div>
                    </div>
                  </main>

                  <script>
                    let state = {};
                    let selectedStockName = '';
                    let refreshing = false;
                    let marketPage = 1;
                    let buyQuantityDraft = '1';
                    let sellQuantityDraft = '1';
                    let marketSearch = '';
                    let favoriteOnly = false;
                    let favoriteStocks = new Set(JSON.parse(localStorage.getItem('favoriteStocks') || '[]'));
                    const stocksPerPage = 10;
                    const won = n => Number(n || 0).toLocaleString('ko-KR') + '원';
                    const count = n => Number(n || 0).toLocaleString('ko-KR');
                    const html = v => String(v ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
                    const shareByName = name => (state.shares || []).find(share => share.stockName === name);
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
                      const portfolio = state.portfolio || {};
                      const broker = state.broker || {};
                      document.getElementById('statusPill').textContent = broker.lastTick || '시세 갱신 대기';
                      document.getElementById('summary').innerHTML = [
                        ['보유 현금', won(portfolio.cash), ''],
                        ['주식 평가액', won(portfolio.stockValue), ''],
                        ['총 자산', won(portfolio.totalAsset), ''],
                        ['실시간 손익', won(portfolio.profit), Number(portfolio.profit || 0) >= 0 ? 'up' : 'down'],
                        ['수익률', `${portfolio.profitRate}%`, Number(portfolio.profit || 0) >= 0 ? 'up' : 'down'],
                        ['시세 기준', broker.lastTick || '시세 갱신 대기', 'time']
                      ].map(([label, value, cls]) => `<div class="metric"><div class="labelText">${label}</div><div class="value ${cls}">${value}</div></div>`).join('');
                      if (!selectedStockName && (state.stocks || []).length) selectedStockName = state.stocks[0].name;
                      renderStocks();
                      renderSelectedStock();
                      renderShares();
                      renderFavorites();
                      renderLogs();
                    }
                    function renderStocks() {
                      const allStocks = filteredMarketStocks();
                      const totalPages = Math.max(1, Math.ceil(allStocks.length / stocksPerPage));
                      if (marketPage > totalPages) marketPage = totalPages;
                      if (marketPage < 1) marketPage = 1;
                      const start = (marketPage - 1) * stocksPerPage;
                      const pageStocks = allStocks.slice(start, start + stocksPerPage);
                      document.getElementById('stockSearch').value = marketSearch;
                      document.getElementById('favoriteOnlyButton').classList.toggle('active', favoriteOnly);
                      document.getElementById('stocks').innerHTML = pageStocks.length ? pageStocks.map(stock => `<tr class="marketRow ${stock.name === selectedStockName ? 'selected' : ''}" data-stock="${html(stock.name)}" onclick="selectStock(this.dataset.stock)">
                        <td><div class="stockNameCell"><button type="button" class="favoriteButton ${favoriteStocks.has(stock.name) ? 'active' : ''}" title="즐겨찾기" aria-label="즐겨찾기" data-stock="${html(stock.name)}" onclick="event.stopPropagation(); toggleFavorite(this.dataset.stock)">${favoriteStocks.has(stock.name) ? '★' : '☆'}</button><div class="stockTitle"><strong>${html(stock.name)}</strong><span>${html(stock.code)} · ${html(stock.sector)}</span></div><button type="button" class="ghost quoteButton" data-stock="${html(stock.name)}" onclick="event.stopPropagation(); selectStock(this.dataset.stock)">보기</button></div></td>
                        <td>${won(stock.price)}</td>
                        <td>${count(stock.tradingVolume || stock.quantity)}</td>
                        <td class="${stock.priceFluct>=0?'up':'down'}">${won(stock.priceFluct)}</td>
                        <td class="${stock.priceFluct>=0?'up':'down'}">${stock.changeRate}%</td>
                      </tr>`).join('') : '<tr><td colspan="5" class="empty">검색 결과가 없습니다.</td></tr>';
                      document.getElementById('marketPageInfo').textContent = `${allStocks.length ? start + 1 : 0}-${Math.min(start + stocksPerPage, allStocks.length)} / ${allStocks.length}개`;
                      document.getElementById('marketPages').innerHTML = Array.from({length:totalPages}, (_, index) => {
                        const page = index + 1;
                        return `<button type="button" class="${page === marketPage ? 'active' : ''}" onclick="goMarketPage(${page})">${page}</button>`;
                      }).join('');
                    }
                    function filteredMarketStocks() {
                      const query = marketSearch.trim().toLowerCase();
                      return (state.stocks || []).filter(stock => {
                        if (favoriteOnly && !favoriteStocks.has(stock.name)) return false;
                        if (!query) return true;
                        return [stock.name, stock.code, stock.market, stock.sector]
                          .some(value => String(value || '').toLowerCase().includes(query));
                      });
                    }
                    function setMarketSearch(value) {
                      marketSearch = value;
                      marketPage = 1;
                      renderStocks();
                    }
                    function toggleFavoriteOnly() {
                      favoriteOnly = !favoriteOnly;
                      marketPage = 1;
                      renderStocks();
                    }
                    function toggleFavorite(name) {
                      if (favoriteStocks.has(name)) {
                        favoriteStocks.delete(name);
                      } else {
                        favoriteStocks.add(name);
                      }
                      localStorage.setItem('favoriteStocks', JSON.stringify([...favoriteStocks]));
                      renderStocks();
                      renderFavorites();
                    }
                    function goMarketPage(page) {
                      marketPage = page;
                      renderStocks();
                    }
                    function renderSelectedStock() {
                      const stock = stockByName(selectedStockName) || (state.stocks || [])[0];
                      if (!stock) {
                        document.getElementById('stockDetail').innerHTML = '<div class="empty">종목이 없습니다.</div>';
                        document.getElementById('companyInfo').innerHTML = '';
                        document.getElementById('orderPanel').innerHTML = '';
                        document.getElementById('priceChart').innerHTML = '<text x="320" y="105" text-anchor="middle" fill="#667085">가격 데이터가 없습니다.</text>';
                        document.getElementById('chartMeta').innerHTML = '';
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
                        ['갱신 시각', stock.lastUpdated || '갱신 대기', '']
                      ].map(([label, value, cls]) => `<div class="metric"><div class="labelText">${label}</div><div class="value ${cls}">${html(value)}</div></div>`).join('');
                      document.getElementById('companyInfo').innerHTML = html(stock.description || '회사 정보가 없습니다.');
                      if (!['buyQuantity', 'sellQuantity'].includes(document.activeElement?.id)) {
                        renderOrderPanel(stock);
                      }
                      renderPriceChart(stock);
                    }
                    function renderOrderPanel(stock) {
                      const share = shareByName(stock.name);
                      const canSell = share && Number(share.quantity) > 0;
                      document.getElementById('orderPanel').innerHTML = `
                        <div class="orderCard">
                          <div class="labelText">선택 종목 매수</div>
                          <strong>${html(stock.name)} · ${won(stock.price)}</strong>
                          <label>매수 수량<input id="buyQuantity" type="number" min="1" value="${html(buyQuantityDraft)}" oninput="buyQuantityDraft=this.value"></label>
                          <button class="buy" type="button" onclick="buySelected()">매수</button>
                        </div>
                        <div class="orderCard">
                          <div class="labelText">보유 종목 매도</div>
                          <strong>${canSell ? `${html(stock.name)} ${count(share.quantity)}주 보유` : '선택 종목 보유 수량 없음'}</strong>
                          <label>매도 수량<input id="sellQuantity" type="number" min="1" max="${canSell ? share.quantity : 1}" value="${html(sellQuantityDraft)}" oninput="sellQuantityDraft=this.value" ${canSell ? '' : 'disabled'}></label>
                          <button class="sell" type="button" onclick="sellSelected()" ${canSell ? '' : 'disabled'}>매도</button>
                        </div>`;
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
                      document.getElementById('shares').innerHTML = (state.shares || []).map((share, index) => {
                        const positive = Number(share.profit || 0) >= 0;
                        const inputId = 'sell-owned-' + index;
                        return `<tr><td><button type="button" class="ghost" data-stock="${html(share.stockName)}" onclick="selectStock(this.dataset.stock)">${html(share.stockName)}</button></td><td>${share.quantity}</td><td>${won(share.averagePrice)}</td><td>${won(share.currentPrice)}</td><td>${won(share.value)}</td><td class="${positive?'up':'down'}">${won(share.profit)}</td><td class="${positive?'up':'down'}">${share.profitRate}%</td><td><div class="inlineOrder"><input id="${inputId}" type="number" min="1" max="${share.quantity}" value="1"><button type="button" class="sell" data-stock="${html(share.stockName)}" onclick="sellOwned(this.dataset.stock, '${inputId}')">매도</button></div></td></tr>`;
                      }).join('') || '<tr><td colspan="8" class="empty">보유 주식이 없습니다.</td></tr>';
                    }
                    function renderLogs() {
                      document.getElementById('logs').innerHTML = (state.logs || []).map(log => `<tr><td>${log.time}</td><td>${log.type}</td><td>${html(log.stockName)}</td><td>${log.quantity}</td><td>${won(log.price)}</td></tr>`).join('') || '<tr><td colspan="5" class="empty">거래 기록이 없습니다.</td></tr>';
                    }
                    function renderFavorites() {
                      const rows = (state.stocks || []).filter(stock => favoriteStocks.has(stock.name));
                      document.getElementById('favorites').innerHTML = rows.length ? rows.map(stock => {
                        const positive = Number(stock.priceFluct || 0) >= 0;
                        return `<tr>
                          <td><button type="button" class="ghost" data-stock="${html(stock.name)}" onclick="selectStock(this.dataset.stock)">${html(stock.name)}</button><div class="subMeta">${html(stock.code)} · ${html(stock.sector)}</div></td>
                          <td>${won(stock.price)}</td>
                          <td>${count(stock.tradingVolume || stock.quantity)}</td>
                          <td class="${positive?'up':'down'}">${stock.changeRate}%</td>
                          <td><button type="button" class="ghost" data-stock="${html(stock.name)}" onclick="toggleFavorite(this.dataset.stock)">해제</button></td>
                        </tr>`;
                      }).join('') : '<tr><td colspan="5" class="empty">시장 시세에서 별표를 누르면 즐겨찾기 종목이 여기에 표시됩니다.</td></tr>';
                    }
                    async function selectStock(name) {
                      selectedStockName = name;
                      buyQuantityDraft = '1';
                      sellQuantityDraft = '1';
                      renderStocks();
                      renderSelectedStock();
                    }
                    async function buySelected() {
                      const qty = Number(document.getElementById('buyQuantity')?.value || 0);
                      try {
                        const result = await api('/api/stock/buy', {stockName:selectedStockName, quantity:String(qty)});
                        document.getElementById('tradeMessage').textContent = result.message;
                        buyQuantityDraft = '1';
                        await refresh();
                      } catch (err) {
                        document.getElementById('tradeMessage').textContent = err.message;
                      }
                    }
                    async function sellSelected() {
                      const qty = Number(document.getElementById('sellQuantity')?.value || 0);
                      try {
                        const result = await api('/api/stock/sell', {stockName:selectedStockName, quantity:String(qty)});
                        document.getElementById('tradeMessage').textContent = result.message;
                        sellQuantityDraft = '1';
                        await refresh();
                      } catch (err) {
                        document.getElementById('tradeMessage').textContent = err.message;
                      }
                    }
                    async function sellOwned(stockName, inputId) {
                      const qty = Number(document.getElementById(inputId)?.value || 0);
                      try {
                        selectedStockName = stockName;
                        const result = await api('/api/stock/sell', {stockName, quantity:String(qty)});
                        document.getElementById('tradeMessage').textContent = result.message;
                        await refresh();
                        showTab('portfolio');
                      } catch (err) {
                        document.getElementById('tradeMessage').textContent = err.message;
                      }
                    }
                    function showTab(name) {
                      const labels = {portfolio:'보유', favorites:'즐겨찾기', logs:'기록'};
                      document.querySelectorAll('.tab').forEach(tab => tab.classList.remove('active'));
                      document.querySelectorAll('.tabPanel').forEach(panel => panel.classList.remove('active'));
                      document.querySelectorAll('.tab').forEach(tab => { if (tab.textContent === labels[name]) tab.classList.add('active'); });
                      document.getElementById('panel-' + name).classList.add('active');
                    }
                    refresh();
                    setInterval(refresh, 1000);
                  </script>
                </body>
                </html>
                """;
    }
}
