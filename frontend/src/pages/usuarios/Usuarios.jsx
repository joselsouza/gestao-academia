import { useState } from 'react'
import { api } from '../../lib/api.js'
import { useAuth } from '../../lib/auth.jsx'
import { data } from '../../lib/format.js'
import { Cabecalho, Erro, Modal, Pessoa, Spinner, useCarregar } from '../../components/ui.jsx'
import Icon from '../../components/Icon.jsx'

const NOVO = { nome: '', email: '', senha: '', perfil: 'INSTRUTOR', ativo: true }
const PERFIL = { ADMIN: 'Administrador', INSTRUTOR: 'Instrutor' }

export default function Usuarios() {
  const { usuario } = useAuth()
  const { dados, erro, carregando, recarregar } = useCarregar(() => api.get('/api/usuarios'))
  const [form, setForm] = useState(null)
  const [erroForm, setErroForm] = useState(null)
  const [salvando, setSalvando] = useState(false)

  const ehVoce = (u) => u.email.toLowerCase() === usuario?.email?.toLowerCase()

  async function salvar(e) {
    e.preventDefault()
    setSalvando(true); setErroForm(null)
    try {
      if (form.id) {
        await api.put(`/api/usuarios/${form.id}`, { nome: form.nome, perfil: form.perfil, ativo: form.ativo, senha: form.senha || null })
      } else {
        await api.post('/api/usuarios', { nome: form.nome, email: form.email, senha: form.senha, perfil: form.perfil })
      }
      setForm(null)
      recarregar()
    } catch (err) {
      setErroForm(err.message)
    } finally {
      setSalvando(false)
    }
  }

  return (
    <>
      <Cabecalho titulo="Usuários de acesso" sub={<><b>Administrador</b> acessa tudo, inclusive esta tela. <b>Instrutor</b> acessa tudo, menos o cadastro de usuários.</>}>
        <button className="btn btn-primario" onClick={() => { setErroForm(null); setForm(NOVO) }}><Icon nome="mais" /> Novo usuário</button>
      </Cabecalho>
      <Erro erro={erro} />
      <div className="card" style={{ padding: 0 }}>
        {carregando && !dados ? <div className="carregando">Carregando…</div> : (
          <table className="tabela">
            <thead><tr><th>Usuário</th><th>Perfil</th><th>Situação</th><th>Criado em</th><th /></tr></thead>
            <tbody>
              {(dados || []).map((u) => (
                <tr key={u.id}>
                  <td className="sem-rotulo"><Pessoa nome={u.nome} sub={u.email + (ehVoce(u) ? ' · você' : '')} /></td>
                  <td data-label="Perfil">{u.perfil === 'ADMIN' ? <span className="selo escuro">{PERFIL.ADMIN}</span> : <span className="selo neutro">{PERFIL.INSTRUTOR}</span>}</td>
                  <td data-label="Situação">{u.ativo ? <span className="selo ok">Ativo</span> : <span className="selo neutro">Inativo</span>}</td>
                  <td data-label="Criado em">{data(u.criadoEm)}</td>
                  <td className="acoes-linha">
                    <button className="btn btn-texto btn-pequeno" onClick={() => { setErroForm(null); setForm({ ...u, senha: '' }) }}>Editar</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {form && (
        <Modal titulo={form.id ? 'Editar usuário' : 'Novo usuário'} onFechar={() => setForm(null)}>
          <form onSubmit={salvar} style={{ display: 'grid', gap: 14 }}>
            <Erro erro={erroForm} />
            <div className="campo">
              <label htmlFor="un">Nome *</label>
              <input id="un" required maxLength={120} value={form.nome} onChange={(e) => setForm({ ...form, nome: e.target.value })} />
            </div>
            <div className="campo">
              <label htmlFor="ue">E-mail de acesso *</label>
              <input id="ue" type="email" required disabled={!!form.id} value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
              {form.id && <span className="ajuda">O e-mail não pode ser alterado.</span>}
            </div>
            <div className="campo">
              <label htmlFor="us">{form.id ? 'Nova senha' : 'Senha *'}</label>
              <input id="us" type="password" autoComplete="new-password" minLength={6} maxLength={72} required={!form.id}
                value={form.senha} onChange={(e) => setForm({ ...form, senha: e.target.value })} />
              <span className="ajuda">{form.id ? 'Deixe em branco para manter a senha atual. ' : ''}Mínimo de 6 caracteres.</span>
            </div>
            <div className="campo">
              <label htmlFor="up">Perfil</label>
              <select id="up" value={form.perfil} disabled={form.id && ehVoce(form)} onChange={(e) => setForm({ ...form, perfil: e.target.value })}>
                <option value="INSTRUTOR">{PERFIL.INSTRUTOR}</option>
                <option value="ADMIN">{PERFIL.ADMIN}</option>
              </select>
            </div>
            {form.id && !ehVoce(form) && (
              <label className="checkbox">
                <input type="checkbox" checked={form.ativo} onChange={(e) => setForm({ ...form, ativo: e.target.checked })} />
                <span>Usuário ativo (pode entrar no sistema)</span>
              </label>
            )}
            <div className="linha" style={{ justifyContent: 'flex-end' }}>
              <button type="button" className="btn btn-secundario" onClick={() => setForm(null)}>Cancelar</button>
              <button className="btn btn-primario" disabled={salvando}>{salvando ? <Spinner /> : 'Salvar'}</button>
            </div>
          </form>
        </Modal>
      )}
    </>
  )
}
