import { useState } from 'react'
import { api } from '../../lib/api.js'
import { brl, competenciaLabel, data, whatsappLink } from '../../lib/format.js'
import { Spinner } from '../../components/ui.jsx'
import Icon from '../../components/Icon.jsx'
import academia from 'virtual:academia'

export const FORMAS = {
  INDEFINIDA: 'Aluno escolhe (PIX, cartão ou boleto)',
  PIX: 'PIX',
  CARTAO: 'Cartão de crédito',
  BOLETO: 'Boleto',
}

export const CICLOS = { MENSAL: 'Mensal', TRIMESTRAL: 'Trimestral', SEMESTRAL: 'Semestral', ANUAL: 'Anual' }

/** Dias até vencer a partir dos quais a cobrança aparece como "próxima do vencimento". */
export const DIAS_ALERTA = 5

export function tipoLabel(c) {
  return c.tipo === 'RECORRENTE' ? `Recorrente · ${CICLOS[c.ciclo] || ''}` : 'Única'
}

export function SeloCobranca({ c }) {
  switch (c.status) {
    case 'PAGA': return <span className="selo ok">Paga</span>
    case 'VENCIDA': return <span className="selo erro">Vencida</span>
    case 'ESTORNADA': return <span className="selo neutro">Estornada</span>
    case 'ENCERRADA': return <span className="selo neutro">Encerrada</span>
    default: return <span className="selo alerta">Em aberto</span>
  }
}

/** "vence hoje", "vence em 3 dias", "vencida há 2 dias" — só para cobranças em aberto. */
export function Prazo({ c }) {
  if (c.diasParaVencer == null || !['PENDENTE', 'VENCIDA'].includes(c.status)) return null
  const d = c.diasParaVencer
  if (d < 0) return <span className="selo erro">há {-d} dia{d === -1 ? '' : 's'}</span>
  if (d === 0) return <span className="selo erro">vence hoje</span>
  if (d <= DIAS_ALERTA) return <span className="selo alerta">em {d} dia{d === 1 ? '' : 's'}</span>
  return <span className="muted" style={{ fontSize: 12 }}>em {d} dias</span>
}

async function copiarTexto(texto) {
  try {
    await navigator.clipboard.writeText(texto)
    return true
  } catch {
    // Navegadores sem clipboard API (ou sem HTTPS): recurso antigo
    const ta = document.createElement('textarea')
    ta.value = texto
    ta.style.position = 'fixed'; ta.style.opacity = '0'
    document.body.appendChild(ta)
    ta.select()
    const ok = document.execCommand('copy')
    ta.remove()
    return ok
  }
}

export function BotaoCopiar({ link, pequeno = true, rotulo = 'Copiar link' }) {
  const [copiado, setCopiado] = useState(false)
  if (!link) return null
  async function copiar() {
    if (await copiarTexto(link)) {
      setCopiado(true)
      setTimeout(() => setCopiado(false), 2000)
    } else {
      window.prompt('Copie o link da cobrança:', link)
    }
  }
  return (
    <button type="button" className={`btn btn-secundario ${pequeno ? 'btn-pequeno' : ''}`} onClick={copiar}>
      <Icon nome="link" /> {copiado ? 'Copiado!' : rotulo}
    </button>
  )
}

/**
 * Com quem falar sobre a cobrança: o responsável (aluno menor — é ele quem paga) ou o próprio aluno.
 * `referente` é o trecho ", referente a Beatriz" usado nas mensagens ao responsável.
 */
export function contato(c) {
  const primeiro = (n) => n.trim().split(/\s+/)[0]
  if (c.responsavelNome) {
    return { nome: primeiro(c.responsavelNome), telefone: c.responsavelTelefone, referente: `, referente a ${primeiro(c.alunoNome)}` }
  }
  return { nome: primeiro(c.alunoNome), telefone: c.alunoTelefone, referente: '' }
}

export function mensagemWhatsapp(c) {
  const { nome } = contato(c)
  return `Olá, ${nome}! Segue o link para pagamento de ${c.descricao || `sua cobrança ${academia.nome}`} `
    + `(${brl(c.valor)}, vencimento ${data(c.vencimento)}):\n${c.link}${fecho()}`
}

export function BotaoWhatsapp({ c, texto, pequeno = true }) {
  const wa = whatsappLink(contato(c).telefone)
  if (!wa || !c.link) return null
  return (
    <a className={`btn btn-secundario ${pequeno ? 'btn-pequeno' : ''}`} href={`${wa}?text=${encodeURIComponent(texto || mensagemWhatsapp(c))}`}
      target="_blank" rel="noreferrer">
      Enviar no WhatsApp
    </a>
  )
}

export function AvisoConfig({ config }) {
  if (!config) return null
  if (!config.configurado) {
    return (
      <div className="alerta-box aviso">
        Integração com o Asaas ainda não configurada. Defina <code>ASAAS_API_KEY</code> no servidor para gerar links de pagamento.
      </div>
    )
  }
  return (
    <>
      {config.sandbox && (
        <div className="alerta-box info">
          Ambiente de <strong>testes (sandbox)</strong> do Asaas: as cobranças não são reais.
        </div>
      )}
      {!config.webhook && (
        <div className="alerta-box info">
          Webhook do Asaas não configurado: os pagamentos feitos pelo link são conferidos uma vez por dia
          ou ao clicar em "Atualizar com Asaas".
        </div>
      )}
    </>
  )
}

/** Despedida da academia no fim das mensagens com link (ex.: "Oss!"), ou nada. */
const fecho = () => (academia.despedida ? `\n\n${academia.despedida}` : '')

/** Mensagem de WhatsApp de uma mensalidade com o link do Asaas. */
export function mensagemMensalidade(m, atrasada = false) {
  const { nome, referente } = contato(m)
  const mes = competenciaLabel(m.competencia.slice(0, 7))
  return atrasada
    ? `Olá, ${nome}! Passando para lembrar da mensalidade ${academia.nome} de ${mes}${referente} (${brl(m.valor)}), vencida em ${data(m.vencimento)}. Para pagar por PIX ou cartão:\n${m.link}${fecho()}`
    : `Olá, ${nome}! Segue o link da mensalidade ${academia.nome} de ${mes}${referente} (${brl(m.valor)}, vencimento ${data(m.vencimento)}). Dá para pagar por PIX ou cartão:\n${m.link}${fecho()}`
}

/**
 * Gera o link do Asaas de uma mensalidade. Se o aluno não tem CPF (o Asaas exige), pede na hora;
 * o CPF fica salvo no cadastro. Devolve a mensalidade atualizada ou null se o usuário desistiu.
 */
export async function gerarLinkMensalidade(m) {
  let cpf = null
  if (!m.alunoComCpf) {
    cpf = window.prompt(`CPF de ${m.alunoNome} (o Asaas exige para gerar o link):`)
    if (!cpf) return null
  }
  return api.post(`/api/financeiro/mensalidades/${m.id}/link`, { cpf })
}

/** Ações de link do Asaas para uma mensalidade pendente: gerar, copiar e enviar. */
export function AcoesLink({ m, atrasada = false, onGerado, onErro }) {
  const [gerando, setGerando] = useState(false)
  if (m.status !== 'PENDENTE') return null
  if (m.link) {
    return <><BotaoCopiar link={m.link} /><BotaoWhatsapp c={m} texto={mensagemMensalidade(m, atrasada)} /></>
  }
  async function gerar() {
    setGerando(true)
    try {
      const r = await gerarLinkMensalidade(m)
      if (r) onGerado?.(r)
    } catch (err) { onErro?.(err.message) } finally { setGerando(false) }
  }
  return (
    <button type="button" className="btn btn-secundario btn-pequeno" onClick={gerar} disabled={gerando}
      title="Gera o link de pagamento (PIX, cartão ou boleto) no Asaas">
      {gerando ? <Spinner /> : <><Icon nome="link" /> Gerar link</>}
    </button>
  )
}
