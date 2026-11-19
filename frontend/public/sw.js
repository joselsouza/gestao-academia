// Service worker: recebe as notificações push e abre o sistema ao tocar nelas.
// (Não guarda páginas em cache: o site continua sempre na versão mais nova.)

self.addEventListener('install', () => self.skipWaiting())
self.addEventListener('activate', (e) => e.waitUntil(self.clients.claim()))

self.addEventListener('push', (e) => {
  let d = {}
  try { d = e.data ? e.data.json() : {} } catch { d = { corpo: e.data ? e.data.text() : '' } }
  e.waitUntil(self.registration.showNotification(d.titulo || 'Aviso', {
    body: d.corpo || '',
    icon: '/icon-192.png',
    tag: d.tag || undefined,
    data: { url: d.url || '/' },
  }))
})

self.addEventListener('notificationclick', (e) => {
  e.notification.close()
  const url = new URL((e.notification.data && e.notification.data.url) || '/', self.location.origin).href
  e.waitUntil((async () => {
    const janelas = await self.clients.matchAll({ type: 'window', includeUncontrolled: true })
    for (const j of janelas) {
      if (new URL(j.url).origin !== self.location.origin) continue
      try { if (j.navigate) await j.navigate(url) } catch { /* segue para o foco */ }
      return j.focus()
    }
    return self.clients.openWindow(url)
  })())
})
