// Notificações push (Web Push). A equipe recebe "pagamento confirmado"; o aluno, "nova mensalidade/cobrança".
//
// Estados: 'indisponivel' (navegador sem suporte) · 'instalar' (iPhone/iPad: precisa abrir pelo ícone
// da Tela de Início) · 'bloqueado' (permissão negada) · 'inativo' · 'ativo'.
import { api, BASE, tokenStore } from './api.js'

const rotas = (perfil) => (perfil === 'ALUNO' ? '/api/aluno/push' : '/api/push')

export const ehIOS = () => /iPad|iPhone|iPod/.test(navigator.userAgent)
  || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1)

const instalado = () => window.matchMedia?.('(display-mode: standalone)').matches || navigator.standalone === true

const suportado = () => 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window

async function inscricaoAtual() {
  const reg = await navigator.serviceWorker.getRegistration('/')
  return reg ? reg.pushManager.getSubscription() : null
}

export async function estadoPush() {
  if (!suportado()) return ehIOS() && !instalado() ? 'instalar' : 'indisponivel'
  if (Notification.permission === 'denied') return 'bloqueado'
  try {
    return (await inscricaoAtual()) && Notification.permission === 'granted' ? 'ativo' : 'inativo'
  } catch {
    return 'inativo'
  }
}

function chaveBytes(base64url) {
  const b64 = base64url.replace(/-/g, '+').replace(/_/g, '/') + '='.repeat((4 - (base64url.length % 4)) % 4)
  return Uint8Array.from(atob(b64), (c) => c.charCodeAt(0))
}

const corpo = (sub) => {
  const j = sub.toJSON()
  return { endpoint: j.endpoint, p256dh: j.keys.p256dh, auth: j.keys.auth }
}

/**
 * Ativa neste aparelho. Precisa ser chamada direto do toque no botão: o iPhone só mostra o
 * pedido de permissão nesse momento.
 */
export async function ativarPush(perfil) {
  const permissao = await Notification.requestPermission()
  if (permissao !== 'granted') {
    throw new Error(permissao === 'denied'
      ? 'As notificações foram bloqueadas. Libere nas configurações do navegador ou do celular.'
      : 'Permissão não concedida.')
  }
  const reg = await navigator.serviceWorker.register('/sw.js', { scope: '/' })
  await navigator.serviceWorker.ready
  let sub = await reg.pushManager.getSubscription()
  if (!sub) {
    const { chavePublica } = await api.get('/api/push/chave')
    // Alguns navegadores travam aqui quando não têm serviço de push: desiste depois de 20 s.
    sub = await Promise.race([
      reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: chaveBytes(chavePublica) }),
      new Promise((_, falhar) => setTimeout(() => falhar(new Error(
        'Este navegador não conseguiu ativar as notificações. Tente pelo Chrome, Safari ou Edge.')), 20000)),
    ])
  }
  await api.post(`${rotas(perfil)}/inscricao`, corpo(sub))
  await api.post(`${rotas(perfil)}/teste`).catch(() => {})
}

/** Desativa neste aparelho. */
export async function desativarPush(perfil) {
  const sub = await inscricaoAtual()
  if (!sub) return
  await api.post(`${rotas(perfil)}/inscricao/remover`, { endpoint: sub.endpoint }).catch(() => {})
  await sub.unsubscribe()
}

/** Já ativo: confirma no servidor que este aparelho é de quem está logado (silencioso). */
export async function sincronizarPush(perfil) {
  if (!suportado() || Notification.permission !== 'granted') return
  const sub = await inscricaoAtual().catch(() => null)
  if (sub) await api.post(`${rotas(perfil)}/inscricao`, corpo(sub)).catch(() => {})
}

/**
 * Ao sair: o aparelho deixa de receber os avisos desta pessoa (celular compartilhado).
 * Usa o token antes de ele ser apagado.
 */
export function esquecerPushAoSair(perfil) {
  const token = tokenStore.get()
  if (!suportado() || !token) return
  inscricaoAtual().then(async (sub) => {
    if (!sub) return
    await fetch(`${BASE}${rotas(perfil)}/inscricao/remover`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: JSON.stringify({ endpoint: sub.endpoint }),
      keepalive: true,
    }).catch(() => {})
    await sub.unsubscribe().catch(() => {})
  }).catch(() => {})
}
