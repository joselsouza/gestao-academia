import { useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../../lib/api.js'
import { brl, data, telefoneMask } from '../../lib/format.js'
import { Cabecalho, Erro, Pessoa, Spinner, useCarregar } from '../../components/ui.jsx'
import Icon from '../../components/Icon.jsx'
import { AvisoConfig, BotaoCopiar, BotaoWhatsapp, DIAS_ALERTA, Prazo, SeloCobranca, contato, tipoLabel } from './asaas.jsx'

const emAberto = (c) => c.status === 'PENDENTE' || c.status === 'VENCIDA'
const proxima = (c) => c.status === 'PENDENTE' && c.diasParaVencer != null && c.diasParaVencer <= DIAS_ALERTA

const FILTROS = {
  '': () => true,
  PROXIMAS: proxima,
  VENCIDAS: (c) => c.status === 'VENCIDA',
  ABERTAS: emAberto,
  PAGAS: (c) => c.status === 'PAGA',
  ENCERRADAS: (c) => c.status === 'ESTORNADA' || c.status === 'ENCERRADA',
}

export default function CobrancasAvulsas() {
  const [filtro, setFiltro] = useState('')
  const [tipo, setTipo] = useState('')
  const [busca, setBusca] = useState('')
  const [msg, setMsg] = useState(null)
  const [erroAcao, setErroAcao] = useState(null)
  const [sincronizando, setSincronizando] = useState(false)
  const [ocupado, setOcupado] = useState(null) // id da linha em ação

  const { dados, erro, carregando, recarregar } = useCarregar(async () => {
    const [lista, config] = await Promise.all([api.get('/api/cobrancas'), api.get('/api/cobrancas/config')])
    return { lista, config }
  }, [], 'cobrancas')

  const todas = dados?.lista || []
  const termo = busca.trim().toLowerCase()
  const lista = todas
    .filter(FILTROS[filtro])
    .filter((c) => !tipo || c.tipo === tipo)
    .filter((c) => !termo || c.alunoNome.toLowerCase().includes(termo))

  const abertas = todas.filter(emAberto)
  const vencidas = todas.filter((c) => c.status === 'VENCIDA')
  const proximas = todas.filter(proxima)
  const soma = (l) => l.reduce((s, c) => s + Number(c.valor), 0)

  async function sincronizarTodas() {
    setSincronizando(true); setMsg(null); setErroAcao(null)
    try {
      const r = await api.post('/api/cobrancas/sincronizar')
      setMsg(`${r.atualizadas} cobrança(s) atualizada(s) com o Asaas.`)
      if (r.falhas.length) setErroAcao(`Não foi possível atualizar: ${r.falhas.join(' · ')}`)
      recarregar()
    } catch (err) { setErroAcao(err.message) } finally { setSincronizando(false) }
  }

  async function sincronizar(c) {
    setOcupado(c.id); setErroAcao(null)
    try { await api.post(`/api/cobrancas/${c.id}/sincronizar`); recarregar() } catch (err) { setErroAcao(err.message) } finally { setOcupado(null) }
  }

  async function excluir(c) {
    const aviso = c.tipo === 'RECORRENTE'
      ? `Excluir a cobrança recorrente de ${c.alunoNome}? A assinatura será cancelada no Asaas e as parcelas em aberto, removidas.`
      : `Excluir a cobrança de ${c.alunoNome} (${brl(c.valor)})? Ela também será removida no Asaas e o link deixará de funcionar.`
    if (!window.confirm(aviso)) return
    setOcupado(c.id); setErroAcao(null); setMsg(null)
    try { await api.del(`/api/cobrancas/${c.id}`); setMsg('Cobrança excluída.'); recarregar() } catch (err) { setErroAcao(err.message) } finally { setOcupado(null) }
  }

  return (
    <>
      <Cabecalho titulo="Cobranças avulsas" sub={<>Cobranças fora da mensalidade (exame de faixa, kimono, eventos), emitidas pelo Asaas. A baixa do pagamento é automática.</>}>
        <button className="btn btn-secundario" onClick={sincronizarTodas} disabled={sincronizando || !dados?.config?.configurado}>
          {sincronizando ? <Spinner /> : 'Atualizar situação'}
        </button>
        <Link className="btn btn-primario" to="/financeiro/cobrancas/nova">Nova cobrança</Link>
      </Cabecalho>

      <AvisoConfig config={dados?.config} />
      {msg && <div className="alerta-box ok">{msg}</div>}
      <Erro erro={erro || erroAcao} />

      {dados && (
        <div className="grade-kpi">
          <div className="kpi destaque"><div className="rotulo">Em aberto</div><div className="valor">{brl(soma(abertas))}</div><div className="detalhe">{abertas.length} cobrança(s)</div></div>
          <button type="button" className="kpi" onClick={() => setFiltro('PROXIMAS')}>
            <div className="rotulo">Vencem em até {DIAS_ALERTA} dias</div><div className="valor" style={{ color: 'var(--alerta)' }}>{proximas.length}</div><div className="detalhe">{brl(soma(proximas))}</div>
          </button>
          <button type="button" className="kpi vermelho" onClick={() => setFiltro('VENCIDAS')}>
            <div className="rotulo">Vencidas</div><div className="valor">{vencidas.length}</div><div className="detalhe">{brl(soma(vencidas))}</div>
          </button>
          <button type="button" className="kpi" onClick={() => setFiltro('PAGAS')}>
            <div className="rotulo">Pagas</div><div className="valor" style={{ color: 'var(--ok)' }}>{todas.filter((c) => c.status === 'PAGA').length}</div><div className="detalhe">última parcela de cada cobrança</div>
          </button>
        </div>
      )}

      <div className="filtros">
        <input className="input" type="search" placeholder="Buscar aluno…" value={busca} onChange={(e) => setBusca(e.target.value)} />
        <div className="campo">
          <label htmlFor="sit">Situação</label>
          <select id="sit" className="input" value={filtro} onChange={(e) => setFiltro(e.target.value)}>
            <option value="">Todas</option>
            <option value="PROXIMAS">Próximas do vencimento</option>
            <option value="VENCIDAS">Vencidas</option>
            <option value="ABERTAS">Em aberto</option>
            <option value="PAGAS">Pagas</option>
            <option value="ENCERRADAS">Encerradas / estornadas</option>
          </select>
        </div>
        <div className="campo">
          <label htmlFor="tp">Tipo</label>
          <select id="tp" className="input" value={tipo} onChange={(e) => setTipo(e.target.value)}>
            <option value="">Todos</option><option value="UNICA">Única</option><option value="RECORRENTE">Recorrente</option>
          </select>
        </div>
      </div>

      <div className="card" style={{ padding: 0 }}>
        {carregando && !dados ? <div className="carregando">Carregando…</div> : lista.length === 0 ? (
          <div className="vazio">{todas.length ? 'Nenhuma cobrança com esse filtro.' : 'Nenhuma cobrança avulsa emitida. As mensalidades ficam em Financeiro › Mensalidades.'}</div>
        ) : (
          <table className="tabela">
            <thead><tr><th>Aluno</th><th>Tipo</th><th>Vencimento</th><th className="num">Valor</th><th>Situação</th><th /></tr></thead>
            <tbody>
              {lista.map((c) => (
                <tr key={c.id}>
                  <td className="sem-rotulo"><Pessoa nome={c.alunoNome} sub={c.descricao || (contato(c).telefone ? telefoneMask(contato(c).telefone) : null)} /></td>
                  <td data-label="Tipo">{tipoLabel(c)}</td>
                  <td data-label="Vencimento">
                    <div className="linha" style={{ gap: 6 }}>{data(c.vencimento)} <Prazo c={c} /></div>
                    {c.tipo === 'RECORRENTE' && c.status === 'PAGA' && c.proximoVencimento && (
                      <small className="muted">próxima: {data(c.proximoVencimento)}</small>
                    )}
                  </td>
                  <td data-label="Valor" className="num">{brl(c.valor)}</td>
                  <td data-label="Situação">
                    <SeloCobranca c={c} />
                    {c.dataPagamento && <small className="muted" style={{ display: 'block' }}>em {data(c.dataPagamento)}</small>}
                  </td>
                  <td className="acoes-linha">
                    {emAberto(c) && <><BotaoCopiar link={c.link} /> <BotaoWhatsapp c={c} /></>}
                    {c.link && !emAberto(c) && <a className="btn btn-texto btn-pequeno" href={c.link} target="_blank" rel="noreferrer">Fatura</a>}
                    <button className="btn btn-texto btn-pequeno" onClick={() => sincronizar(c)} disabled={ocupado === c.id} title="Atualizar com o Asaas">
                      {ocupado === c.id ? <Spinner /> : <Icon nome="atualizar" />}
                    </button>
                    {!(c.tipo === 'UNICA' && c.status === 'PAGA') && (
                      <button className="btn btn-texto btn-pequeno" onClick={() => excluir(c)} disabled={ocupado === c.id}>Excluir</button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </>
  )
}
