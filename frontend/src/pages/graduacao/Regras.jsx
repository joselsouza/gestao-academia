import { useEffect, useState } from 'react'
import { api } from '../../lib/api.js'
import { Cabecalho, Erro, Faixa, Spinner, useCarregar } from '../../components/ui.jsx'

export default function Regras() {
  const { dados, erro, carregando } = useCarregar(() => api.get('/api/graduacao/regras'))
  const [form, setForm] = useState([])
  const [salvando, setSalvando] = useState(false)
  const [msg, setMsg] = useState(null)
  const [erroSalvar, setErroSalvar] = useState(null)

  useEffect(() => { if (dados) setForm(dados.map((r) => ({ ...r }))) }, [dados])

  const alterar = (i, campo, valor) => setForm((f) => f.map((r, j) => (j === i ? { ...r, [campo]: valor } : r)))

  async function salvar(e) {
    e.preventDefault()
    setSalvando(true); setMsg(null); setErroSalvar(null)
    try {
      const alteradas = form.filter((r, i) =>
        Number(r.presencasMinimas) !== dados[i].presencasMinimas || Number(r.mesesMinimos) !== dados[i].mesesMinimos)
      await Promise.all(alteradas.map((r) => api.put(`/api/graduacao/regras/${r.faixa}`, {
        presencasMinimas: Number(r.presencasMinimas), mesesMinimos: Number(r.mesesMinimos),
      })))
      setMsg(alteradas.length ? `${alteradas.length} regra(s) atualizada(s).` : 'Nada para salvar.')
    } catch (err) { setErroSalvar(err.message) } finally { setSalvando(false) }
  }

  return (
    <>
      <Cabecalho titulo="Frequência mínima" sub={<>Para cada faixa atual, quantos treinos (presenças) e quantos meses desde a última graduação o aluno precisa para ficar <b>apto</b> ao próximo grau ou faixa.</>} />
      <Erro erro={erro || erroSalvar} />
      {msg && <div className="alerta-box ok">{msg}</div>}
      {carregando && !dados ? <div className="carregando">Carregando…</div> : (
        <form onSubmit={salvar}>
          <div className="card" style={{ padding: 0 }}>
            <table className="tabela">
              <thead><tr><th>Faixa atual</th><th>Treinos mínimos</th><th>Meses mínimos</th></tr></thead>
              <tbody>
                {form.map((r, i) => (
                  <tr key={r.faixa}>
                    <td className="sem-rotulo"><Faixa faixa={r.faixa} /></td>
                    <td data-label="Treinos mínimos">
                      <input className="input" type="number" min={0} max={1000} required style={{ width: 110 }}
                        value={r.presencasMinimas} onChange={(e) => alterar(i, 'presencasMinimas', e.target.value)} />
                    </td>
                    <td data-label="Meses mínimos">
                      <input className="input" type="number" min={0} max={240} required style={{ width: 110 }}
                        value={r.mesesMinimos} onChange={(e) => alterar(i, 'mesesMinimos', e.target.value)} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="linha" style={{ justifyContent: 'flex-end', marginTop: 16 }}>
            <button className="btn btn-primario" disabled={salvando}>{salvando ? <Spinner /> : 'Salvar regras'}</button>
          </div>
        </form>
      )}
    </>
  )
}
