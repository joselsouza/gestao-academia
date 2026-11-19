// Cookies do site e consentimento (LGPD).
//  - af_cookies (essencial): guarda a escolha do usuário no aviso de cookies.
//  - af_email   (funcional): lembra o e-mail na tela de login — só com "Aceitar todos".
// A sessão de login fica no armazenamento local do navegador (essencial ao funcionamento).

const UM_ANO = 60 * 60 * 24 * 365
export const CONSENTIMENTO = 'af_cookies'
export const EMAIL = 'af_email'

export function lerCookie(nome) {
  const par = document.cookie.split('; ').find((c) => c.startsWith(`${nome}=`))
  return par ? decodeURIComponent(par.slice(nome.length + 1)) : null
}

export function gravarCookie(nome, valor, maxAge = UM_ANO) {
  const seguro = window.location.protocol === 'https:' ? '; Secure' : ''
  document.cookie = `${nome}=${encodeURIComponent(valor)}; Max-Age=${maxAge}; Path=/; SameSite=Lax${seguro}`
}

export function apagarCookie(nome) {
  document.cookie = `${nome}=; Max-Age=0; Path=/; SameSite=Lax`
}

/** 'todos' | 'essenciais' | null (ainda não escolheu) */
export const consentimento = () => lerCookie(CONSENTIMENTO)

export function definirConsentimento(valor) {
  gravarCookie(CONSENTIMENTO, valor)
  if (valor !== 'todos') apagarCookie(EMAIL)
  window.dispatchEvent(new Event('af-cookies-alterado'))
}

export const podeUsarFuncionais = () => consentimento() === 'todos'

export function abrirPreferenciasCookies() {
  window.dispatchEvent(new Event('af-cookies-abrir'))
}
