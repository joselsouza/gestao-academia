import { useEffect, useMemo, useState } from 'react'
import { api, qs } from '../../lib/api.js'
import { data as fmtData, hojeISO } from '../../lib/format.js'
import { Avatar, Cabecalho, Erro, Faixa, Spinner, useCarregar } from '../../components/ui.jsx'
import { useAvisoSaida } from '../../components/useAvisoSaida.js'
import Icon from '../../components/Icon.jsx'
import BarraGraduacao, { projetar } from '../../components/BarraGraduacao.jsx'

const iguais = (a, b) => a.size === b.size && [...a].every((x) => b.has(x))

export default function Chamada() {
  const [data, setData] = useState(hojeISO())
  const [busca, setBusca] = useState('')
  const [selecionados, setSelecionados] = useState(new Set())
  const [salvando, setSalvando] = useState(false)
  const [msg, setMsg] = useState(null)
  const [erroAcao, setErroAcao] = useState(null)

  const { dados, erro, carregando, recarregar } = useCarregar(async () => {
    const [alunos, presencas, graduacao] = await Promise.all([
      api.get('/api/alunos?ativo=true'),
      api.get(`/api/presencas${qs({ inicio: data, fim: data })}`),
      api.get('/api/graduacao/status?somenteAptos=false').catch(() => []),
    ])
    return {
      alunos,
      presencas: new Map(presencas.map((p) => [p.alunoId, p])),
      graduacao: new Map(graduacao.map((g) => [g.alunoId, g])),
    }
  }, [data])

  // Ao carregar o dia, a seleção local parte do que já está salvo.
  const salvos = useMemo(() => new Set(dados ? dados.presencas.keys() : []), [dados])
  useEffect(() => { setSelecionados(new Set(salvos)) }, [salvos])

  const alterado = dados ? !iguais(salvos, selecionados) : false
  useAvisoSaida(alterado)

  function alternar(id) {
    setMsg(null)
    setSelecionados((s) => {
      const n = new Set(s)
      if (n.has(id)) n.delete(id)
      else n.add(id)
      return n
    })
  }

  function trocarData(nova) {
    if (!nova) return
    if (alterado && !window.confirm('Há alterações não salvas nesta chamada. Descartar?')) return
    setMsg(null)
    setData(nova)
  }

  async function salvar() {
    setSalvando(true); setErroAcao(null); setMsg(null)
    try {
      const r = await api.post('/api/presencas/salvar', {
        data,
        removerAusentes: true,
        itens: [...selecionados].map((alunoId) => ({ alunoId, origem: 'MANUAL' })),
      })
      setMsg(`Presença salva: ${r.totalNoDia} aluno(s) presente(s) em ${fmtData(r.data)}.`)
      await recarregar()
    } catch (err) {
      setErroAcao(err.message)
    } finally {
      setSalvando(false)
    }
  }

  const termo = busca.trim().toLowerCase()
  const alunos = (dados?.alunos || []).filter((a) => !termo || a.nome.toLowerCase().includes(termo))

  return (
    <>
      <Cabecalho titulo="Chamada manual" />
      <div className="filtros">
        <div className="campo">
          <label htmlFor="dt">Data do treino</label>
          <input id="dt" className="input" type="date" value={data} max={hojeISO()} onChange={(e) => trocarData(e.target.value)} />
        </div>
        <div className="campo" style={{ maxWidth: 'none' }}>
          <label htmlFor="bu">Buscar</label>
          <input id="bu" className="input" placeholder="Nome do aluno" value={busca} onChange={(e) => setBusca(e.target.value)} />
        </div>
      </div>
      <Erro erro={erro || erroAcao} />
      {msg && <div className="alerta-box ok">{msg}</div>}
      {dados && <p className="muted">Toque nos alunos presentes e depois em <b>Salvar presença</b>. Sem salvar, a presença não é registrada. A barra mostra os treinos para a próxima graduação (listrada = ainda não salvo).</p>}

      {carregando && !dados ? <div className="carregando">Carregando…</div> : (
        <div className="chamada">
          {alunos.map((a) => {
            const marcado = selecionados.has(a.id)
            const salvo = dados.presencas.get(a.id)
            return (
              <button key={a.id} className={marcado ? 'presente' : ''} onClick={() => alternar(a.id)} aria-pressed={marcado}>
                <Avatar nome={a.nome} foto={a.foto} />
                <span style={{ minWidth: 0 }}>
                  <strong style={{ display: 'block', fontWeight: 500, marginBottom: 4 }}>{a.nome}</strong>
                  <Faixa faixa={a.faixa} graus={a.graus} />
                  <BarraGraduacao compacta
                    status={projetar(dados.graduacao.get(a.id), data, (marcado ? 1 : 0) - (salvo ? 1 : 0))} />
                  {salvo?.origem === 'RECONHECIMENTO_FACIAL' && <small className="muted" style={{ display: 'block' }}>salva via reconhecimento facial</small>}
                </span>
                <span className="marca" aria-hidden="true"><Icon nome="check" /></span>
              </button>
            )
          })}
        </div>
      )}

      {dados && (
        <div className={`barra-salvar ${alterado ? 'pendente' : ''}`}>
          <span>
            <strong>{selecionados.size}</strong> de {dados.alunos.length} presente(s)
            {alterado ? <span className="selo alerta" style={{ marginLeft: 8 }}>Não salvo</span> : <span className="selo ok" style={{ marginLeft: 8 }}>Salvo</span>}
          </span>
          <button className="btn btn-primario" onClick={salvar} disabled={!alterado || salvando}>
            {salvando ? <Spinner /> : 'Salvar presença'}
          </button>
        </div>
      )}
    </>
  )
}
