export const FAIXAS = ['BRANCA', 'CINZA', 'AMARELA', 'LARANJA', 'VERDE', 'AZUL', 'ROXA', 'MARROM', 'PRETA']

export const COR_FAIXA = {
  BRANCA: '#f4f4f4', CINZA: '#8d8d8d', AMARELA: '#f2c200', LARANJA: '#f07c00', VERDE: '#2e7d32',
  AZUL: '#1556b8', ROXA: '#6a1b9a', MARROM: '#5d3a1a', PRETA: '#151515',
}

export const grausMaximos = (faixa) => (faixa === 'PRETA' ? 6 : 4)

export const nomeFaixa = (f) => (f ? f.charAt(0) + f.slice(1).toLowerCase() : '')

const moeda = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })
export const brl = (v) => moeda.format(Number(v || 0))

export function data(iso) {
  if (!iso) return '—'
  const [a, m, d] = iso.slice(0, 10).split('-')
  return `${d}/${m}/${a}`
}

export function competenciaLabel(ym) {
  const [a, m] = ym.split('-').map(Number)
  return new Date(a, m - 1, 1).toLocaleDateString('pt-BR', { month: 'long', year: 'numeric' })
}

/** Data de hoje no fuso local, em ISO (yyyy-MM-dd). */
export function hojeISO() {
  const d = new Date()
  return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10)
}

export const mesAtual = () => hojeISO().slice(0, 7)

export function iniciais(nome = '') {
  return nome.trim().split(/\s+/).slice(0, 2).map((p) => p[0]).join('').toUpperCase()
}

export function telefoneMask(v = '') {
  const d = v.replace(/\D/g, '').slice(0, 11)
  if (d.length <= 2) return d
  if (d.length <= 6) return `(${d.slice(0, 2)}) ${d.slice(2)}`
  if (d.length <= 10) return `(${d.slice(0, 2)}) ${d.slice(2, 6)}-${d.slice(6)}`
  return `(${d.slice(0, 2)}) ${d.slice(2, 7)}-${d.slice(7)}`
}

export function whatsappLink(tel) {
  const d = (tel || '').replace(/\D/g, '')
  return d ? `https://wa.me/55${d}` : null
}

export function cpfMask(v = '') {
  const d = v.replace(/\D/g, '').slice(0, 11)
  return d.replace(/(\d{3})(\d)/, '$1.$2').replace(/(\d{3})(\d)/, '$1.$2').replace(/(\d{3})(\d{1,2})$/, '$1-$2')
}

/** Idade completa (anos) a partir de uma data ISO (yyyy-MM-dd), ou null se inválida/vazia. */
export function idade(iso) {
  if (!iso || !/^\d{4}-\d{2}-\d{2}$/.test(iso)) return null
  const [a, m, d] = iso.split('-').map(Number)
  const hoje = new Date()
  let anos = hoje.getFullYear() - a
  if (hoje.getMonth() + 1 < m || (hoje.getMonth() + 1 === m && hoje.getDate() < d)) anos--
  return anos >= 0 && anos < 130 ? anos : null
}

export const MAIORIDADE = 18

