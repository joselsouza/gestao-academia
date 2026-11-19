import { useState } from 'react'
import { api } from '../../lib/api.js'
import { data } from '../../lib/format.js'
import { Cabecalho, Erro, Faixa, useCarregar } from '../../components/ui.jsx'

export default function HistoricoGraduacao() {
  const { dados, erro, carregando } = useCarregar(() => api.get('/api/graduacao/historico'))
  const [busca, setBusca] = useState('')
  const termo = busca.trim().toLowerCase()
  const lista = (dados || []).filter((g) => !termo || g.alunoNome.toLowerCase().includes(termo))

  return (
    <>
      <Cabecalho titulo="Histórico de graduações" />
      <div className="filtros">
        <input className="input" placeholder="Buscar aluno" value={busca} onChange={(e) => setBusca(e.target.value)} />
      </div>
      <Erro erro={erro} />
      <div className="card" style={{ padding: 0 }}>
        {carregando && !dados ? <div className="carregando">Carregando…</div> : lista.length === 0 ? <div className="vazio">Nenhuma graduação registrada.</div> : (
          <table className="tabela">
            <thead><tr><th>Data</th><th>Aluno</th><th>De</th><th>Para</th><th className="num">Treinos no período</th><th>Obs.</th></tr></thead>
            <tbody>
              {lista.map((g) => (
                <tr key={g.id}>
                  <td data-label="Data">{data(g.data)}</td>
                  <td data-label="Aluno"><strong>{g.alunoNome}</strong></td>
                  <td data-label="De"><Faixa faixa={g.faixaAnterior} graus={g.grausAnterior} /></td>
                  <td data-label="Para"><Faixa faixa={g.faixaNova} graus={g.grausNova} /></td>
                  <td data-label="Treinos" className="num">{g.presencasNoPeriodo}</td>
                  <td data-label="Obs.">{g.observacao || '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </>
  )
}
