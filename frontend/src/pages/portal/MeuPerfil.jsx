import { useEffect, useState } from 'react'
import { api } from '../../lib/api.js'
import { useAuth } from '../../lib/auth.jsx'
import { cpfMask, telefoneMask } from '../../lib/format.js'
import { Cabecalho, Erro, Faixa, Spinner, useCarregar } from '../../components/ui.jsx'

const SENHA_VAZIA = { senhaAtual: '', novaSenha: '', confirmacao: '' }

function Dados({ perfil, onSalvo }) {
  const [form, setForm] = useState({ nome: perfil.nome, telefone: perfil.telefone || '', email: perfil.email || '' })
  const [erro, setErro] = useState(null)
  const [ok, setOk] = useState(false)
  const [salvando, setSalvando] = useState(false)

  async function salvar(e) {
    e.preventDefault()
    setErro(null); setOk(false); setSalvando(true)
    try {
      const p = await api.put('/api/aluno/me', {
        nome: form.nome,
        telefone: form.telefone.replace(/\D/g, '') || null,
        email: form.email.trim() || null,
      })
      onSalvo(p)
      setOk(true)
    } catch (err) {
      setErro(err.message)
    } finally {
      setSalvando(false)
    }
  }

  return (
    <form className="card" onSubmit={salvar}>
      <h2>Meus dados</h2>
      <Erro erro={erro} />
      {ok && <div className="alerta-box ok">Dados atualizados.</div>}
      <div className="form-grade">
        <div className="campo largo">
          <label htmlFor="nome">Nome completo *</label>
          <input id="nome" required maxLength={120} value={form.nome} onChange={(e) => setForm({ ...form, nome: e.target.value })} />
        </div>
        <div className="campo">
          <label htmlFor="tel">Telefone / WhatsApp</label>
          <input id="tel" inputMode="tel" placeholder="(24) 99999-9999" value={telefoneMask(form.telefone)}
            onChange={(e) => setForm({ ...form, telefone: e.target.value })} />
        </div>
        <div className="campo">
          <label htmlFor="email">E-mail</label>
          <input id="email" type="email" maxLength={160} value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
        </div>
        <div className="campo">
          <label htmlFor="cpf">CPF</label>
          <input id="cpf" value={cpfMask(perfil.cpf || '')} disabled />
          <span className="ajuda">É o seu login. Para corrigir, fale com a recepção.</span>
        </div>
        <div className="campo">
          <label>Graduação e plano</label>
          <div style={{ display: 'grid', gap: 6, paddingTop: 6 }}>
            <Faixa faixa={perfil.faixa} graus={perfil.graus} />
            <small className="muted">{perfil.planoNome || 'Sem plano'}</small>
          </div>
        </div>
      </div>
      <div className="linha" style={{ justifyContent: 'flex-end', marginTop: 16 }}>
        <button className="btn btn-primario" disabled={salvando}>{salvando ? <Spinner /> : 'Salvar dados'}</button>
      </div>
    </form>
  )
}

function Senha() {
  const [form, setForm] = useState(SENHA_VAZIA)
  const [erro, setErro] = useState(null)
  const [ok, setOk] = useState(false)
  const [salvando, setSalvando] = useState(false)
  const set = (campo) => (e) => setForm({ ...form, [campo]: e.target.value })

  async function trocar(e) {
    e.preventDefault()
    setErro(null); setOk(false)
    if (form.novaSenha !== form.confirmacao) { setErro('A confirmação não confere com a nova senha.'); return }
    setSalvando(true)
    try {
      await api.put('/api/aluno/me/senha', { senhaAtual: form.senhaAtual, novaSenha: form.novaSenha })
      setForm(SENHA_VAZIA)
      setOk(true)
    } catch (err) {
      setErro(err.message)
    } finally {
      setSalvando(false)
    }
  }

  return (
    <form className="card" id="senha" onSubmit={trocar}>
      <h2>Senha</h2>
      <Erro erro={erro} />
      {ok && <div className="alerta-box ok">Senha alterada. Use a nova senha no próximo acesso.</div>}
      <div className="form-grade">
        <div className="campo largo">
          <label htmlFor="sa">Senha atual *</label>
          <input id="sa" type="password" autoComplete="current-password" required value={form.senhaAtual} onChange={set('senhaAtual')} />
        </div>
        <div className="campo">
          <label htmlFor="ns">Nova senha *</label>
          <input id="ns" type="password" autoComplete="new-password" required minLength={6} maxLength={72}
            value={form.novaSenha} onChange={set('novaSenha')} />
          <span className="ajuda">Mínimo de 6 caracteres.</span>
        </div>
        <div className="campo">
          <label htmlFor="cs">Confirme a nova senha *</label>
          <input id="cs" type="password" autoComplete="new-password" required minLength={6} maxLength={72}
            value={form.confirmacao} onChange={set('confirmacao')} />
        </div>
      </div>
      <div className="linha" style={{ justifyContent: 'flex-end', marginTop: 16 }}>
        <button className="btn btn-primario" disabled={salvando}>{salvando ? <Spinner /> : 'Alterar senha'}</button>
      </div>
    </form>
  )
}

export default function MeuPerfil() {
  const { atualizarUsuario } = useAuth()
  const { dados: perfil, erro, carregando } = useCarregar(() => api.get('/api/aluno/me'))

  // Se a recepção redefiniu a senha, o aluno volta para a tela de primeiro acesso.
  useEffect(() => {
    if (perfil) atualizarUsuario({ nome: perfil.nome, senhaPadrao: perfil.senhaPadrao })
  }, [perfil, atualizarUsuario])

  return (
    <>
      <Cabecalho secao="Área do aluno" titulo="Editar perfil" sub="Mantenha o seu telefone e e-mail atualizados e troque a sua senha." />
      <Erro erro={erro} />
      {carregando && !perfil ? <div className="carregando">Carregando…</div> : perfil && (
        <>
          <Dados perfil={perfil} onSalvo={(p) => atualizarUsuario({ nome: p.nome })} />
          <Senha />
        </>
      )}
    </>
  )
}
