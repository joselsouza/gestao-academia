import { useState } from 'react'
import { api, qs } from '../../lib/api.js'
import { brl, competenciaLabel, data, hojeISO, mesAtual } from '../../lib/format.js'
import { Cabecalho, Erro, Modal, Pessoa, Spinner, useCarregar } from '../../components/ui.jsx'
import { AcoesLink, AvisoConfig } from './asaas.jsx'

export function SeloStatus({ m }) {
  if (m.status === 'PAGO') return <span className="selo ok">Pago</span>
  if (m.status === 'CANCELADO') return <span className="selo neutro">Cancelado</span>
  return m.atrasada ? <span className="selo erro">Atrasado</span> : <span className="selo alerta">Pendente</span>
}

export function ModalPagamento({ mensalidade, onFechar, onPago }) {
  const [form, setForm] = useState({ formaPagamento: 'PIX', dataPagamento: hojeISO(), valor: mensalidade.valor, observacao: '' })
  const [erro, setErro] = useState(null)
  const [salvando, setSalvando] = useState(false)

  async function confirmar(e) {
    e.preventDefault()
    setSalvando(true); setErro(null)
    try {
      await api.post(`/api/financeiro/mensalidades/${mensalidade.id}/pagar`, {
        ...form, valor: Number(String(form.valor).replace(',', '.')), observacao: form.observacao || null,
      })
      onPago()
    } catch (err) {
      setErro(err.message); setSalvando(false)
    }
  }

  return (
    <Modal titulo="Registrar pagamento" onFechar={onFechar}>
      <form onSubmit={confirmar} style={{ display: 'grid', gap: 14 }}>
        <Erro erro={erro} />
        <div><strong>{mensalidade.alunoNome}</strong><br /><span className="muted">Competência {competenciaLabel(mensalidade.competencia.slice(0, 7))} · vence {data(mensalidade.vencimento)}</span></div>
        <div className="campo">
          <label htmlFor="fp">Forma de pagamento</label>
          <select id="fp" value={form.formaPagamento} onChange={(e) => setForm({ ...form, formaPagamento: e.target.value })}>
            <option value="PIX">PIX</option><option value="DINHEIRO">Dinheiro</option>
            <option value="CARTAO">Cartão</option><option value="TRANSFERENCIA">Transferência</option>
          </select>
          {mensalidade.link && <span className="ajuda">O link de pagamento do Asaas desta mensalidade será cancelado.</span>}
        </div>
        <div className="form-grade">
          <div className="campo">
            <label htmlFor="dp">Data do pagamento</label>
            <input id="dp" type="date" required value={form.dataPagamento} onChange={(e) => setForm({ ...form, dataPagamento: e.target.value })} />
          </div>
          <div className="campo">
            <label htmlFor="vp">Valor pago (R$)</label>
            <input id="vp" inputMode="decimal" required value={form.valor} onChange={(e) => setForm({ ...form, valor: e.target.value })} />
          </div>
        </div>
        <div className="campo">
          <label htmlFor="op">Observação</label>
          <input id="op" maxLength={255} value={form.observacao} onChange={(e) => setForm({ ...form, observacao: e.target.value })} />
        </div>
        <div className="linha" style={{ justifyContent: 'flex-end' }}>
          <button type="button" className="btn btn-secundario" onClick={onFechar}>Cancelar</button>
          <button className="btn btn-primario" disabled={salvando}>{salvando ? <Spinner /> : 'Confirmar pagamento'}</button>
        </div>
      </form>
    </Modal>
  )
}

function textoLinks(l) {
  if (!l || (!l.gerados && !l.semCpf)) return ''
  return ` ${l.gerados} link(s) de pagamento gerado(s) no Asaas.${l.semCpf ? ` ${l.semCpf} aluno(s) sem CPF: gere o link pelo botão de cada um.` : ''}`
}

export default function Mensalidades() {
  const [competencia, setCompetencia] = useState(mesAtual())
  const [filtro, setFiltro] = useState('')
  const [pagando, setPagando] = useState(null)
  const [msg, setMsg] = useState(null)
  const [erroAcao, setErroAcao] = useState(null)
  const [gerando, setGerando] = useState(false)
  const [ocupado, setOcupado] = useState(null) // 'links' | 'sincronizar'

  const { dados, erro, carregando, recarregar } = useCarregar(async () => {
    const [lista, resumo, config] = await Promise.all([
      api.get(`/api/financeiro/mensalidades${qs({ competencia })}`),
      api.get(`/api/financeiro/resumo${qs({ competencia })}`),
      api.get('/api/cobrancas/config').catch(() => null),
    ])
    return { lista, resumo, config }
  }, [competencia])
  const asaasOk = Boolean(dados?.config?.configurado)
  const semLink = (dados?.lista || []).filter((m) => m.status === 'PENDENTE' && !m.link).length

  const lista = (dados?.lista || []).filter((m) => {
    if (filtro === 'PAGO') return m.status === 'PAGO'
    if (filtro === 'PENDENTE') return m.status === 'PENDENTE' && !m.atrasada
    if (filtro === 'ATRASADO') return m.atrasada
    return true
  })
  const r = dados?.resumo

  async function gerar() {
    setGerando(true); setMsg(null); setErroAcao(null)
    try {
      const g = await api.post(`/api/financeiro/mensalidades/gerar${qs({ competencia })}`)
      setMsg(`${g.criadas} mensalidade(s) gerada(s). ${g.jaExistentes} já existiam. ${g.bolsistasIsentos} bolsista(s) isento(s).${textoLinks(g.links)}`)
      if (g.links?.falhas?.length) setErroAcao(`Links não gerados: ${g.links.falhas.join(' · ')}`)
      recarregar()
    } catch (err) { setErroAcao(err.message) } finally { setGerando(false) }
  }

  async function gerarLinks() {
    setOcupado('links'); setMsg(null); setErroAcao(null)
    try {
      const l = await api.post(`/api/financeiro/mensalidades/links${qs({ competencia })}`)
      setMsg(textoLinks(l).trim() || 'Nenhum link a gerar.')
      if (l.falhas.length) setErroAcao(`Links não gerados: ${l.falhas.join(' · ')}`)
      recarregar()
    } catch (err) { setErroAcao(err.message) } finally { setOcupado(null) }
  }

  async function sincronizar() {
    setOcupado('sincronizar'); setMsg(null); setErroAcao(null)
    try {
      const s = await api.post('/api/financeiro/asaas/sincronizar')
      setMsg(`${s.verificadas} link(s) conferido(s) no Asaas · ${s.pagas} pagamento(s) novo(s).`)
      if (s.falhas.length) setErroAcao(s.falhas.join(' · '))
      recarregar()
    } catch (err) { setErroAcao(err.message) } finally { setOcupado(null) }
  }

  async function acao(m, tipo, confirmacao) {
    if (confirmacao && !window.confirm(confirmacao)) return
    setErroAcao(null)
    try { await api.post(`/api/financeiro/mensalidades/${m.id}/${tipo}`); recarregar() } catch (err) { setErroAcao(err.message) }
  }

  return (
    <>
      <Cabecalho titulo="Mensalidades" sub={<>Geradas automaticamente todo dia 1º, com vencimento no dia 10, já com o link de pagamento do Asaas (PIX, cartão ou boleto). Quem paga pelo link recebe a baixa sozinho. Bolsistas não geram cobrança.</>}>
        {asaasOk && (
          <button className="btn btn-secundario" onClick={sincronizar} disabled={ocupado !== null}>
            {ocupado === 'sincronizar' ? <Spinner /> : 'Atualizar com Asaas'}
          </button>
        )}
        {asaasOk && semLink > 0 && (
          <button className="btn btn-secundario" onClick={gerarLinks} disabled={ocupado !== null}>
            {ocupado === 'links' ? <Spinner /> : `Gerar links (${semLink})`}
          </button>
        )}
        <button className="btn btn-primario" onClick={gerar} disabled={gerando}>
          {gerando ? <Spinner /> : 'Gerar mensalidades do mês'}
        </button>
      </Cabecalho>

      <div className="filtros">
        <div className="campo">
          <label htmlFor="comp">Competência</label>
          <input id="comp" className="input" type="month" value={competencia} onChange={(e) => e.target.value && setCompetencia(e.target.value)} />
        </div>
        <div className="campo">
          <label htmlFor="st">Situação</label>
          <select id="st" className="input" value={filtro} onChange={(e) => setFiltro(e.target.value)}>
            <option value="">Todas</option><option value="PAGO">Pagas</option>
            <option value="PENDENTE">Pendentes (no prazo)</option><option value="ATRASADO">Atrasadas</option>
          </select>
        </div>
      </div>

      <AvisoConfig config={dados?.config} />
      {msg && <div className="alerta-box ok">{msg}</div>}
      <Erro erro={erro || erroAcao} />

      {r && (
        <div className="grade-kpi">
          <div className="kpi destaque"><div className="rotulo">Previsto</div><div className="valor">{brl(r.previsto)}</div><div className="detalhe">{competenciaLabel(competencia)}</div></div>
          <div className="kpi verde"><div className="rotulo">Recebido</div><div className="valor">{brl(r.recebido)}</div><div className="detalhe">{r.qtdPagas} paga(s)</div></div>
          <div className="kpi"><div className="rotulo">Em aberto</div><div className="valor">{brl(r.pendente)}</div><div className="detalhe">{r.qtdPendentes} pendente(s)</div></div>
          <div className="kpi vermelho"><div className="rotulo">Atrasado</div><div className="valor">{brl(r.atrasado)}</div><div className="detalhe">{r.qtdAtrasadas} atrasada(s)</div></div>
        </div>
      )}

      <div className="card" style={{ padding: 0 }}>
        {carregando && !dados ? <div className="carregando">Carregando…</div> : lista.length === 0 ? (
          <div className="vazio">Nenhuma mensalidade {filtro ? 'com esse filtro' : 'nesta competência. Clique em "Gerar mensalidades do mês".'}</div>
        ) : (
          <table className="tabela">
            <thead><tr><th>Aluno</th><th>Vencimento</th><th className="num">Valor</th><th>Situação</th><th>Pagamento</th><th /></tr></thead>
            <tbody>
              {lista.map((m) => (
                <tr key={m.id}>
                  <td className="sem-rotulo"><Pessoa nome={m.alunoNome} /></td>
                  <td data-label="Vencimento">{data(m.vencimento)}</td>
                  <td data-label="Valor" className="num">{brl(m.valor)}</td>
                  <td data-label="Situação"><SeloStatus m={m} /></td>
                  <td data-label="Pagamento">{m.dataPagamento ? `${data(m.dataPagamento)} · ${m.formaPagamento}` : '—'}</td>
                  <td className="acoes-linha">
                    {m.status === 'PENDENTE' && <>
                      {asaasOk && <AcoesLink m={m} atrasada={m.atrasada} onGerado={recarregar} onErro={setErroAcao} />}
                      <button className="btn btn-primario btn-pequeno" onClick={() => setPagando(m)}>Receber</button>
                      <button className="btn btn-texto btn-pequeno" onClick={() => acao(m, 'cancelar', `Cancelar a mensalidade de ${m.alunoNome}?`)}>Cancelar</button>
                    </>}
                    {m.status === 'PAGO' && <button className="btn btn-texto btn-pequeno" onClick={() => acao(m, 'estornar', `Estornar o pagamento de ${m.alunoNome}?`)}>Estornar</button>}
                    {m.status === 'CANCELADO' && <button className="btn btn-texto btn-pequeno" onClick={() => acao(m, 'estornar')}>Reabrir</button>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {pagando && <ModalPagamento mensalidade={pagando} onFechar={() => setPagando(null)} onPago={() => { setPagando(null); recarregar() }} />}
    </>
  )
}
