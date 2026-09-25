// sw.js — 오늘도 해보자!! Service Worker
//
// ★ 배포할 때 아래 VERSION 한 줄만 바꾸면 이전 캐시가 전부 정리됩니다.
const VERSION = '2026-09-25a';
const CACHE = `fitlog-${VERSION}`;

// 오프라인용으로 미리 받아둘 파일
const ASSETS = [
  './',
  './index.html',
  './manifest.json',
  './icon.png'
];

// 설치: 핵심 파일 캐시 (HTTP 캐시 무시하고 새로 받기)
self.addEventListener('install', e => {
  e.waitUntil((async () => {
    const c = await caches.open(CACHE);
    // 하나 실패해도 설치는 진행
    await Promise.all(
      ASSETS.map(u => c.add(new Request(u, { cache: 'reload' })).catch(() => {}))
    );
    await self.skipWaiting();
  })());
});

// 활성화: 이전 버전 캐시 삭제 후 즉시 제어권 가져오기
self.addEventListener('activate', e => {
  e.waitUntil((async () => {
    const keys = await caches.keys();
    await Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k)));
    await self.clients.claim();
  })());
});

// 페이지에서 보낸 즉시 적용 요청
self.addEventListener('message', e => {
  if (e.data === 'SKIP_WAITING') self.skipWaiting();
});

self.addEventListener('fetch', e => {
  const req = e.request;
  if (req.method !== 'GET') return;

  const url = new URL(req.url);
  if (url.origin !== self.location.origin) return;   // 폰트·Firebase 등 외부는 그대로
  if (url.pathname.includes('/diary/')) return;      // diary는 자체 sw.js 담당

  const isDoc = req.mode === 'navigate'
    || req.destination === 'document'
    || (req.headers.get('accept') || '').includes('text/html');

  // HTML은 네트워크 우선 → 배포하면 바로 반영됨
  // 나머지(아이콘 등)는 캐시 먼저 주고 뒤에서 갱신
  e.respondWith(isDoc ? networkFirst(req) : staleWhileRevalidate(req, e));
});

async function networkFirst(req) {
  const cache = await caches.open(CACHE);
  try {
    // cache:'no-store' → 브라우저 HTTP 캐시까지 건너뛰고 서버에서 받아옴
    const fresh = await fetch(new Request(req.url, {
      cache: 'no-store',
      credentials: 'same-origin'
    }));
    if (fresh && fresh.ok) cache.put(req.url, fresh.clone());
    return fresh;
  } catch (err) {
    // 오프라인: 캐시 → index.html 순으로 폴백
    return (await cache.match(req.url))
      || (await cache.match('./index.html'))
      || Response.error();
  }
}

async function staleWhileRevalidate(req, e) {
  const cache = await caches.open(CACHE);
  const cached = await cache.match(req);
  const net = fetch(req).then(res => {
    if (res && res.ok && res.type === 'basic') cache.put(req, res.clone());
    return res;
  }).catch(() => null);

  if (cached) {
    // 응답은 캐시로 즉시, 갱신은 백그라운드에서
    try { e.waitUntil(net); } catch (_) {}
    return cached;
  }
  return (await net) || Response.error();
}
