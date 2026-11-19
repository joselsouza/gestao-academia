import { useState } from 'react'
import { Link } from 'react-router-dom'
import { api, qs } from '../../lib/api.js'
import { FAIXAS, data, grausMaximos, hojeISO, nomeFaixa } from '../../lib/format.js'
import { Cabecalho, Erro, Faixa, Modal, Pessoa, Spinner, useCarregar } from '../../components/ui.jsx'

function ModalGraduar({ aluno, onFechar, onFeito }) {
  const [form, setForm] = useState({ faixaNova: aluno.sugestaoFaixa, grausNova: aluno.sugestaoGraus, data: hojeISO(), observacao: '' })
  const [erro, setErro] = useState(null)
  const [salvando, setSalvando] = useState(false)

  async function confirmar(e) {
    e.preventDefault()
    setSalvando(true); setErro(null)
    try {
      const r = await api.post('/api/graduacao', { alunoId: aluno.alunoId, ...form, grausNova: Number(form.grausNova), observacao: form.observacao || null })
      onFeito(r)
    } catch (err) { setErro(err.message); setSalvando(false) }
  }

  return (
    <Modal titulo="Registrar graduação" onFechar={onFechar}>
      <form onSubmit={confirmar} style={{ display: 'grid', gap: 14 }}>
        <Erro erro={erro} />
        <Pessoa nome={aluno.nome} foto={aluno.foto} sub={`${aluno.presencas} treinos desde ${data(aluno.contandoDesde)}`} />
        <div className="linha"><span className="muted">Atual:</span> <Faixa faixa={aluno.faixa} graus={aluno.graus} /></div>
        <div className="form-grade">
          <div className="campo">
            <label htmlFor="fn">Nova faixa</label>
            <select id="fn" value={form.faixaNova} onChange={(e) => setForm({ ...form, faixaNova: e.target.value, grausNova: Math.min(form.grausNova, grausMaximos(e.target.value)) })}>
              {FAIXAS.map((f) => <option key={f} value={f}>{nomeFaixa(f)}</option>)}
            </select>
          </div>
          <div className="campo">
            <label htmlFor="gn">Graus</label>
            <select id="gn" value={form.grausNova} onChange={(e) => setForm({ ...form, grausNova: e.target.value })}>
              {Array.from({ length: grausMaximos(form.faixaNova) + 1 }, (_, i) => <option key={i} value={i}>{i}</option>)}
            </select>
          </div>
        </div>
        <div className="linha"><span className="muted">Nova:</span> <Faixa faixa={form.faixaNova} graus={Number(form.grausNova)} /></div>
        <div className="campo">
          <label htmlFor="dg">Data da graduação</label>
          <input id="dg" type="date" required value={form.data} onChange={(e) => setForm({ ...form, data: e.target.value })} />
        </div>
        <div className="campo">
          <label htmlFor="og">Observação</label>
          <input id="og" maxLength={255} value={form.observacao} onChange={(e) => setForm({ ...form, observacao: e.target.value })} />
        </div>
        <p className="muted" style={{ margin: 0, fontSize: 13 }}>A contagem de frequência do aluno recomeça a partir desta data.</p>
        <div className="linha" style={{ justifyContent: 'flex-end' }}>
          <button type="button" className="btn btn-secundario" onClick={onFechar}>Cancelar</button>
          <button className="btn btn-primario" disabled={salvando}>{salvando ? <Spinner /> : 'Graduar'}</button>
        </div>
      </form>
    </Modal>
  )
}

export default function Aptos() {
  const [somenteAptos, setSomenteAptos] = useState(true)
  const [busca, setBusca] = useState('')
  const [graduando, setGraduando] = useState(null)
  const [msg, setMsg] = useState(null)
  const { dados, erro, carregando, recarregar } = useCarregar(
    () => api.get(`/api/graduacao/status${qs({ somenteAptos })}`), [somenteAptos], `aptos:${somenteAptos}`)

  const termo = busca.trim().toLowerCase()
  const lista = (dados || []).filter((s) => !termo || s.nome.toLowerCase().includes(termo))

  return (
    <>
      <Cabecalho titulo="Aptos a graduar" sub={<>Treinos contados desde a última graduação (ou matrícula) contra a <Link to="/graduacao/regras">frequência mínima</Link> de cada faixa.</>} />
      {msg && <div className="alerta-box ok">{msg}</div>}
      <div className="filtros">
        <input className="input" placeholder="Buscar aluno" value={busca} onChange={(e) => setBusca(e.target.value)} />
        <select className="input" style={{ flex: '0 1 220px' }} value={String(somenteAptos)} onChange={(e) => setSomenteAptos(e.target.value === 'true')}>
          <option value="true">Somente aptos</option>
          <option value="false">Todos os alunos</option>
        </select>
      </div>
      <Erro erro={erro} />
      <div className="card" style={{ padding: 0 }}>
        {carregando && !dados ? <div className="carregando">Carregando…</div> : lista.length === 0 ? (
          <div className="vazio">{somenteAptos ? 'Nenhum aluno apto no momento.' : 'Nenhum aluno.'}</div>
        ) : (
          <table className="tabela">
            <thead><tr><th>Aluno</th><th>Faixa atual</th><th>Treinos</th><th>Tempo</th><th>Progresso</th><th /></tr></thead>
            <tbody>
              {lista.map((s) => (
                <tr key={s.alunoId}>
                  <td className="sem-rotulo"><Pessoa nome={s.nome} foto={s.foto} sub={`desde ${data(s.contandoDesde)}`} /></td>
                  <td data-label="Faixa"><Faixa faixa={s.faixa} graus={s.graus} /></td>
                  <td data-label="Treinos"><strong>{s.presencas}</strong> / {s.presencasMinimas}</td>
                  <td data-label="Tempo">{s.meses} / {s.mesesMinimos} meses</td>
                  <td data-label="Progresso">
                    <div className="linha" style={{ gap: 8, flexWrap: 'nowrap', justifyContent: 'flex-end' }}>
                      <div className={`barra ${s.apto ? 'cheia' : ''}`} style={{ width: 100 }}><div style={{ width: `${s.percentual}%` }} /></div>
                      {s.apto ? <span className="selo ok">Apto</span> : <span className="muted" style={{ fontSize: 12 }}>{s.percentual}%</span>}
                    </div>
                  </td>
                  <td className="acoes-linha">
                    <button className={`btn btn-pequeno ${s.apto ? 'btn-primario' : 'btn-secundario'}`} onClick={() => setGraduando(s)}>Graduar</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
      {graduando && <ModalGraduar aluno={graduando} onFechar={() => setGraduando(null)} onFeito={(r) => {
        setGraduando(null)
        setMsg(`${r.alunoNome} graduado(a) para ${nomeFaixa(r.faixaNova)}${r.grausNova ? ` · ${r.grausNova}º grau` : ''}.`
          + (r.planoNovo ? ` Plano alterado automaticamente para "${r.planoNovo}".` : ''))
        recarregar()
      }} />}
    </>
  )
}
