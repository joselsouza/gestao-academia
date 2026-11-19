import { useState } from 'react'
import { api } from '../../lib/api.js'
import { FAIXAS, brl, nomeFaixa } from '../../lib/format.js'
import { Cabecalho, Erro, Faixa, Modal, Spinner, useCarregar } from '../../components/ui.jsx'
import Icon from '../../components/Icon.jsx'

const NOVO = { nome: '', tipo: 'MENSALIDADE', valor: '', descricao: '', ativo: true, faixas: [] }

export default function Planos() {
  const { dados, erro, carregando, recarregar } = useCarregar(() => api.get('/api/planos'), [], 'planos')
  const [editando, setEditando] = useState(null)
  const [erroForm, setErroForm] = useState(null)
  const [salvando, setSalvando] = useState(false)

  async function salvar(e) {
    e.preventDefault()
    setSalvando(true); setErroForm(null)
    const body = { ...editando, valor: editando.tipo === 'BOLSISTA' ? 0 : Number(String(editando.valor).replace(',', '.')) }
    try {
      await (editando.id ? api.put(`/api/planos/${editando.id}`, body) : api.post('/api/planos', body))
      setEditando(null)
      recarregar()
    } catch (err) {
      setErroForm(err.message)
    } finally {
      setSalvando(false)
    }
  }

  async function remover(p) {
    const msg = p.alunosAtivos > 0
      ? `"${p.nome}" tem alunos vinculados e será apenas desativado. Continuar?`
      : `Excluir o plano "${p.nome}"?`
    if (!window.confirm(msg)) return
    try { await api.del(`/api/planos/${p.id}`); recarregar() } catch (err) { alert(err.message) }
  }

  return (
    <>
      <Cabecalho titulo="Planos de mensalidade" sub={<>Alunos pagantes usam um plano do tipo <b>Mensalidade</b>; bolsistas usam um plano <b>Bolsista</b> (isento).</>}>
        <button className="btn btn-primario" onClick={() => { setErroForm(null); setEditando(NOVO) }}><Icon nome="mais" /> Novo plano</button>
      </Cabecalho>
      <Erro erro={erro} />
      <div className="card" style={{ padding: 0 }}>
        {carregando && !dados ? <div className="carregando">Carregando…</div> : !dados?.length ? <div className="vazio">Nenhum plano.</div> : (
          <table className="tabela">
            <thead><tr><th>Plano</th><th>Tipo</th><th>Faixas vinculadas</th><th className="num">Valor</th><th className="num">Alunos ativos</th><th>Situação</th><th /></tr></thead>
            <tbody>
              {dados.map((p) => (
                <tr key={p.id}>
                  <td data-label="Plano"><strong>{p.nome}</strong>{p.descricao && <><br /><small className="muted">{p.descricao}</small></>}</td>
                  <td data-label="Tipo">{p.tipo === 'BOLSISTA' ? <span className="selo escuro">Bolsista · isento</span> : <span className="selo neutro">Mensalidade</span>}</td>
                  <td data-label="Faixas vinculadas">
                    {p.faixas?.length ? (
                      <span className="faixas-plano">{p.faixas.map((f) => <Faixa key={f} faixa={f} />)}</span>
                    ) : <span className="muted">—</span>}
                  </td>
                  <td data-label="Valor" className="num">{p.tipo === 'BOLSISTA' ? '—' : brl(p.valor)}</td>
                  <td data-label="Alunos ativos" className="num">{p.alunosAtivos}</td>
                  <td data-label="Situação">{p.ativo ? <span className="selo ok">Ativo</span> : <span className="selo neutro">Inativo</span>}</td>
                  <td className="acoes-linha">
                    <button className="btn btn-texto btn-pequeno" onClick={() => { setErroForm(null); setEditando({ ...p, descricao: p.descricao || '', faixas: p.faixas || [] }) }}>Editar</button>
                    <button className="btn btn-texto btn-pequeno" onClick={() => remover(p)}>Excluir</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {editando && (
        <Modal titulo={editando.id ? 'Editar plano' : 'Novo plano'} onFechar={() => setEditando(null)}>
          <form onSubmit={salvar} style={{ display: 'grid', gap: 14 }}>
            <Erro erro={erroForm} />
            <div className="campo">
              <label htmlFor="pn">Nome *</label>
              <input id="pn" required value={editando.nome} onChange={(e) => setEditando({ ...editando, nome: e.target.value })} />
            </div>
            <div className="campo">
              <label htmlFor="pt">Tipo</label>
              <select id="pt" value={editando.tipo} onChange={(e) => setEditando({ ...editando, tipo: e.target.value })}>
                <option value="MENSALIDADE">Aluno (mensalidade)</option>
                <option value="BOLSISTA">Aluno bolsista (isento)</option>
              </select>
            </div>
            {editando.tipo === 'MENSALIDADE' && (
              <div className="campo">
                <label htmlFor="pv">Valor mensal (R$) *</label>
                <input id="pv" required inputMode="decimal" value={editando.valor} onChange={(e) => setEditando({ ...editando, valor: e.target.value })} />
              </div>
            )}
            <div className="campo">
              <label htmlFor="pd">Descrição</label>
              <input id="pd" maxLength={255} value={editando.descricao} onChange={(e) => setEditando({ ...editando, descricao: e.target.value })} />
            </div>
            <fieldset className="campo-faixas">
              <legend className="rotulo-campo">Faixas vinculadas (plano automático no cadastro e na graduação)</legend>
              <div className="grade-faixas">
                {FAIXAS.map((f) => {
                  const outro = (dados || []).find((x) => x.id !== editando.id && x.faixas?.includes(f))
                  const marcado = editando.faixas.includes(f)
                  return (
                    <label key={f} className={`checkbox ${outro ? 'desabilitado' : ''}`} title={outro ? `Já vinculada ao plano "${outro.nome}"` : ''}>
                      <input type="checkbox" checked={marcado} disabled={!!outro}
                        onChange={() => setEditando({ ...editando, faixas: marcado ? editando.faixas.filter((x) => x !== f) : [...editando.faixas, f] })} />
                      <span><Faixa faixa={f} comNome={false} /> {nomeFaixa(f)}{outro && <small className="muted"> · em "{outro.nome}"</small>}</span>
                    </label>
                  )
                })}
              </div>
              <span className="ajuda">Cada faixa só pode estar em um plano.</span>
            </fieldset>
            {editando.id && (
              <label className="checkbox">
                <input type="checkbox" checked={editando.ativo} onChange={(e) => setEditando({ ...editando, ativo: e.target.checked })} />
                <span>Plano ativo</span>
              </label>
            )}
            <div className="linha" style={{ justifyContent: 'flex-end' }}>
              <button type="button" className="btn btn-secundario" onClick={() => setEditando(null)}>Cancelar</button>
              <button className="btn btn-primario" disabled={salvando}>{salvando ? <Spinner /> : 'Salvar'}</button>
            </div>
          </form>
        </Modal>
      )}
    </>
  )
}
