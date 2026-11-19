import { useEffect, useRef, useState } from 'react'
import { api } from '../../lib/api.js'
import { carregarModelos, detectarRostos, liberar, prepararImagem } from '../../lib/face.js'
import { data as fmtData, hojeISO } from '../../lib/format.js'
import { Avatar, Cabecalho, Erro, Faixa, Spinner } from '../../components/ui.jsx'
import { useAvisoSaida } from '../../components/useAvisoSaida.js'
import Icon from '../../components/Icon.jsx'
import BarraGraduacao, { projetar } from '../../components/BarraGraduacao.jsx'

export default function Reconhecimento() {
  const inputCamera = useRef(null)
  const inputGaleria = useRef(null)
  const [data, setData] = useState(hojeISO())
  const [modelos, setModelos] = useState('carregando') // carregando | ok | erro
  const [etapa, setEtapa] = useState(null) // null | 'detectando' | 'comparando'
  const [progresso, setProgresso] = useState(null) // { atual, total } ao processar várias fotos
  const [foto, setFoto] = useState(null) // última foto: { dataUrl, width, height, rostos, porRosto }
  const [ultimo, setUltimo] = useState(null) // resumo da última foto
  const [lista, setLista] = useState(new Map()) // alunoId -> item a salvar
  const [alunos, setAlunos] = useState([])
  const [graduacao, setGraduacao] = useState(new Map())
  const [manual, setManual] = useState('')
  const [salvando, setSalvando] = useState(false)
  const [msg, setMsg] = useState(null)
  const [erro, setErro] = useState(null)

  useEffect(() => {
    carregarModelos().then(() => setModelos('ok')).catch(() => setModelos('erro'))
    api.get('/api/alunos?ativo=true').then(setAlunos).catch(() => {})
    api.get('/api/graduacao/status?somenteAptos=false')
      .then((l) => setGraduacao(new Map(l.map((g) => [g.alunoId, g])))).catch(() => {})
  }, [])

  const pendentes = [...lista.values()].filter((x) => !x.jaEstavaPresente).length
  useAvisoSaida(pendentes > 0)

  /** Recebe uma ou mais fotos (câmera ou galeria) e processa uma de cada vez. */
  async function processar(e) {
    const arquivos = [...(e.target.files || [])]
    e.target.value = ''
    if (!arquivos.length) return
    setErro(null); setMsg(null)
    const semRosto = []
    try {
      for (let i = 0; i < arquivos.length; i++) {
        setProgresso(arquivos.length > 1 ? { atual: i + 1, total: arquivos.length } : null)
        const achou = await processarArquivo(arquivos[i])
        if (!achou) semRosto.push(i + 1)
      }
      if (semRosto.length) {
        setErro(arquivos.length > 1
          ? `Nenhum rosto encontrado na(s) foto(s) ${semRosto.join(', ')}. Tente fotos mais próximas e bem iluminadas.`
          : 'Nenhum rosto encontrado na foto. Tente uma foto mais próxima e bem iluminada.')
      }
    } catch (err) {
      setErro(err.message)
    } finally {
      setEtapa(null)
      setProgresso(null)
    }
  }

  /** Processa uma foto; devolve false se não houver rosto. */
  async function processarArquivo(file) {
    setEtapa('detectando')
    const prep = await prepararImagem(file, 1600)
    let rostos
    try {
      rostos = await detectarRostos(prep)
    } finally {
      liberar(prep)
    }
    // Exibe o arquivo original (o navegador já cuida da orientação); as caixas usam proporções.
    const imagem = { dataUrl: URL.createObjectURL(file), width: prep.width, height: prep.height }
    setFoto((antiga) => { if (antiga?.dataUrl?.startsWith('blob:')) URL.revokeObjectURL(antiga.dataUrl); return { ...imagem, rostos, porRosto: new Map() } })
    if (rostos.length === 0) {
      setUltimo(null)
      return false
    }
    setEtapa('comparando')
    const r = await api.post('/api/presencas/reconhecimento', { data, rostos: rostos.map((x) => x.descritor) })
    setFoto({ ...imagem, rostos, porRosto: new Map(r.reconhecidos.map((x) => [x.indiceRosto, x])) })
    setUltimo(r)
    // Junta com o que já estava na lista (permite várias fotos da mesma turma)
    setLista((atual) => {
      const n = new Map(atual)
      r.reconhecidos.forEach((x) => {
        const antes = n.get(x.alunoId)
        if (!antes || (antes.origem === 'RECONHECIMENTO_FACIAL' && x.confianca > antes.confianca)) {
          n.set(x.alunoId, { ...x, origem: 'RECONHECIMENTO_FACIAL' })
        }
      })
      return n
    })
    return true
  }

  function remover(alunoId) {
    setLista((atual) => { const n = new Map(atual); n.delete(alunoId); return n })
  }

  function adicionarManual() {
    const a = alunos.find((x) => String(x.id) === manual)
    if (!a) return
    setLista((atual) => new Map(atual).set(a.id, {
      alunoId: a.id, alunoNome: a.nome, foto: a.foto, faixa: a.faixa, graus: a.graus, origem: 'MANUAL', jaEstavaPresente: false,
    }))
    setManual('')
  }

  function trocarData(nova) {
    if (!nova) return
    if (lista.size && !window.confirm('A lista atual ainda não foi salva. Descartar e trocar a data?')) return
    setLista(new Map()); setFoto(null); setUltimo(null); setMsg(null); setData(nova)
  }

  async function salvar() {
    setSalvando(true); setErro(null); setMsg(null)
    try {
      const r = await api.post('/api/presencas/salvar', {
        data,
        removerAusentes: false,
        itens: [...lista.values()].map((x) => ({ alunoId: x.alunoId, origem: x.origem, confianca: x.confianca ?? null })),
      })
      setMsg(`Presença salva: ${r.adicionadas} nova(s) presença(s) em ${fmtData(r.data)}. Total no dia: ${r.totalNoDia}.`)
      api.get('/api/graduacao/status?somenteAptos=false')
        .then((l) => setGraduacao(new Map(l.map((g) => [g.alunoId, g])))).catch(() => {})
      setLista(new Map()); setFoto(null); setUltimo(null)
    } catch (err) {
      setErro(err.message)
    } finally {
      setSalvando(false)
    }
  }

  const itens = [...lista.values()].sort((a, b) => a.alunoNome.localeCompare(b.alunoNome))
  const disponiveis = alunos.filter((a) => !lista.has(a.id))

  return (
    <>
      <Cabecalho titulo="Presença por reconhecimento facial" sub={<>Tire uma ou mais fotos da turma. Os alunos reconhecidos entram na lista abaixo — confira e clique em <b>Salvar presença</b>. Sem salvar, a presença não é registrada.</>} />

      <div className="card">
        <div className="filtros" style={{ marginBottom: 12 }}>
          <div className="campo">
            <label htmlFor="dt">Data do treino</label>
            <input id="dt" className="input" type="date" value={data} max={hojeISO()} onChange={(e) => trocarData(e.target.value)} />
          </div>
        </div>

        {modelos === 'erro' && <div className="alerta-box erro">Falha ao carregar os modelos de reconhecimento. Recarregue a página.</div>}

        <div className="upload-area" aria-busy={modelos !== 'ok' || !!etapa}>
          {modelos === 'carregando' ? (
            <span className="linha" style={{ justifyContent: 'center' }}><Spinner /> Preparando reconhecimento facial…</span>
          ) : etapa ? (
            <span className="linha" style={{ justifyContent: 'center' }}>
              <Spinner /> {etapa === 'detectando' ? 'Procurando rostos na foto…' : 'Comparando com os alunos…'}
              {progresso && ` (foto ${progresso.atual} de ${progresso.total})`}
            </span>
          ) : (
            <>
              <Icon nome="camera" className="icone" />
              <div className="titulo-upload">
                {lista.size ? 'Adicionar outra foto' : 'Foto da turma'}
              </div>
              <div className="botoes-foto">
                <button type="button" className="btn btn-primario" onClick={() => inputCamera.current.click()} disabled={modelos !== 'ok'}>
                  <Icon nome="camera" /> Tirar foto
                </button>
                <button type="button" className="btn btn-secundario" onClick={() => inputGaleria.current.click()} disabled={modelos !== 'ok'}>
                  <Icon nome="galeria" /> Escolher da galeria
                </button>
              </div>
              <small className="muted">Todos de frente, rostos visíveis. Pela galeria dá para escolher várias fotos de uma vez.</small>
            </>
          )}
          {/* Câmera: capture abre direto a câmera. Galeria: sem capture, com seleção múltipla. */}
          <input ref={inputCamera} type="file" accept="image/*" capture="environment" hidden onChange={processar} />
          <input ref={inputGaleria} type="file" accept="image/*" multiple hidden onChange={processar} />
        </div>
      </div>

      <Erro erro={erro} />
      {msg && <div className="alerta-box ok">{msg}</div>}
      {ultimo && ultimo.alunosCadastradosComBiometria === 0 && (
        <div className="alerta-box aviso">Nenhum aluno tem rosto cadastrado ainda. Cadastre a foto e o consentimento em Alunos.</div>
      )}
      {ultimo && (
        <p className="muted">
          Última foto: {ultimo.totalRostos} rosto(s), {ultimo.reconhecidos.length} reconhecido(s), {ultimo.rostosNaoReconhecidos.length} não reconhecido(s).
        </p>
      )}

      {(foto || lista.size > 0) && (
        <div className="duas-colunas">
          {foto && (
            <div className="card" style={{ textAlign: 'center' }}>
              <div className="foto-turma">
                <img src={foto.dataUrl} alt="Foto da turma" />
                {foto.rostos.map((r, i) => {
                  const m = foto.porRosto.get(i)
                  return (
                    <div key={i} className={`caixa-rosto ${m ? 'ok' : ''}`} style={{
                      left: `${(r.box.x / foto.width) * 100}%`, top: `${(r.box.y / foto.height) * 100}%`,
                      width: `${(r.box.w / foto.width) * 100}%`, height: `${(r.box.h / foto.height) * 100}%`,
                    }}>
                      {ultimo && <span>{m ? m.alunoNome.split(' ')[0] : '?'}</span>}
                    </div>
                  )
                })}
              </div>
            </div>
          )}

          <div className="card">
            <h2>Lista para salvar · {fmtData(data)}</h2>
            {itens.length === 0 ? <div className="vazio">Ninguém na lista ainda.</div> : (
              <div className="lista-reconhecidos" style={{ gridTemplateColumns: '1fr' }}>
                {itens.map((x) => (
                  <div key={x.alunoId} className="item">
                    <Avatar nome={x.alunoNome} foto={x.foto} />
                    <div style={{ minWidth: 0, flex: 1 }}>
                      <strong>{x.alunoNome}</strong>
                      <div className="linha" style={{ gap: 6, fontSize: 12 }}>
                        <Faixa faixa={x.faixa} comNome={false} />
                        {x.origem === 'RECONHECIMENTO_FACIAL'
                          ? <span className="muted">{Math.round(x.confianca * 100)}% de confiança</span>
                          : <span className="muted">adicionado manualmente</span>}
                        {x.jaEstavaPresente && <span className="selo neutro">já registrado</span>}
                      </div>
                      <BarraGraduacao compacta status={projetar(graduacao.get(x.alunoId), data, x.jaEstavaPresente ? 0 : 1)} />
                    </div>
                    <button className="btn btn-icone" onClick={() => remover(x.alunoId)} title="Tirar da lista" aria-label="Tirar da lista"><Icon nome="fechar" /></button>
                  </div>
                ))}
              </div>
            )}

            <div className="linha" style={{ marginTop: 14, flexWrap: 'nowrap' }}>
              <select className="input" style={{ flex: 1 }} value={manual} onChange={(e) => setManual(e.target.value)} aria-label="Adicionar aluno não reconhecido">
                <option value="">+ Adicionar aluno não reconhecido…</option>
                {disponiveis.map((a) => <option key={a.id} value={a.id}>{a.nome}</option>)}
              </select>
              <button className="btn btn-secundario" onClick={adicionarManual} disabled={!manual}>Adicionar</button>
            </div>
          </div>
        </div>
      )}

      {lista.size > 0 && (
        <div className="barra-salvar pendente">
          <span><strong>{lista.size}</strong> aluno(s) na lista <span className="selo alerta" style={{ marginLeft: 8 }}>Não salvo</span></span>
          <div className="linha">
            <button className="btn btn-texto" onClick={() => { if (window.confirm('Descartar a lista?')) { setLista(new Map()); setFoto(null); setUltimo(null) } }}>Descartar</button>
            <button className="btn btn-primario" onClick={salvar} disabled={salvando}>{salvando ? <Spinner /> : 'Salvar presença'}</button>
          </div>
        </div>
      )}
    </>
  )
}
