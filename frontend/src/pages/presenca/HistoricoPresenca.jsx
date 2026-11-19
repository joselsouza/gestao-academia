import { useState } from 'react'
import { api, qs } from '../../lib/api.js'
import { data as fmt, hojeISO } from '../../lib/format.js'
import { Cabecalho, Erro, Faixa, useCarregar } from '../../components/ui.jsx'

function menosDias(iso, n) {
  const d = new Date(`${iso}T12:00:00`)
  d.setDate(d.getDate() - n)
  return d.toISOString().slice(0, 10)
}

export default function HistoricoPresenca() {
  const [inicio, setInicio] = useState(menosDias(hojeISO(), 6))
  const [fim, setFim] = useState(hojeISO())
  const [busca, setBusca] = useState('')
  const { dados, erro, carregando } = useCarregar(() => api.get(`/api/presencas${qs({ inicio, fim })}`), [inicio, fim])

  const termo = busca.trim().toLowerCase()
  const lista = (dados || []).filter((p) => !termo || p.alunoNome.toLowerCase().includes(termo))
  const porDia = lista.reduce((acc, p) => { (acc[p.data] ||= []).push(p); return acc }, {})

  return (
    <>
      <Cabecalho titulo="Histórico de presença" />
      <div className="filtros">
        <div className="campo"><label htmlFor="i">De</label><input id="i" className="input" type="date" value={inicio} onChange={(e) => e.target.value && setInicio(e.target.value)} /></div>
        <div className="campo"><label htmlFor="f">Até</label><input id="f" className="input" type="date" value={fim} onChange={(e) => e.target.value && setFim(e.target.value)} /></div>
        <div className="campo" style={{ maxWidth: 'none' }}><label htmlFor="b">Aluno</label><input id="b" className="input" placeholder="Filtrar por nome" value={busca} onChange={(e) => setBusca(e.target.value)} /></div>
      </div>
      <Erro erro={erro} />
      {carregando && !dados ? <div className="carregando">Carregando…</div> : Object.keys(porDia).length === 0 ? (
        <div className="card vazio">Nenhuma presença no período.</div>
      ) : Object.entries(porDia).map(([dia, ps]) => (
        <div className="card" key={dia}>
          <h2>{fmt(dia)} · {ps.length} presente(s)</h2>
          <table className="tabela">
            <tbody>
              {ps.map((p) => (
                <tr key={p.id}>
                  <td className="sem-rotulo"><strong>{p.alunoNome}</strong></td>
                  <td data-label="Faixa"><Faixa faixa={p.faixa} graus={p.graus} /></td>
                  <td data-label="Origem" className="acoes-linha">
                    {p.origem === 'RECONHECIMENTO_FACIAL'
                      ? <span className="selo escuro">Reconhecimento facial{p.confianca != null ? ` · ${Math.round(p.confianca * 100)}%` : ''}</span>
                      : <span className="selo neutro">Manual</span>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ))}
    </>
  )
}
