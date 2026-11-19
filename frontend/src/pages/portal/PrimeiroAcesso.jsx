import { useState } from 'react'
import { api } from '../../lib/api.js'
import { useAuth } from '../../lib/auth.jsx'
import { Erro, Rotulo, Spinner } from '../../components/ui.jsx'
import Rodape from '../../components/Rodape.jsx'
import academia from 'virtual:academia'
import Logotipo from '../../components/Logotipo.jsx'

/** Primeiro acesso (ou senha redefinida pela recepção): o aluno cria a própria senha antes de entrar. */
export default function PrimeiroAcesso() {
  const { usuario, atualizarUsuario, logout } = useAuth()
  const [form, setForm] = useState({ novaSenha: '', confirmacao: '' })
  const [erro, setErro] = useState(null)
  const [salvando, setSalvando] = useState(false)
  const set = (campo) => (e) => setForm({ ...form, [campo]: e.target.value })

  async function salvar(e) {
    e.preventDefault()
    setErro(null)
    if (form.novaSenha !== form.confirmacao) { setErro('A confirmação não confere com a nova senha.'); return }
    setSalvando(true)
    try {
      await api.put('/api/aluno/me/senha', { novaSenha: form.novaSenha })
      atualizarUsuario({ senhaPadrao: false })
    } catch (err) {
      setErro(err.message)
      setSalvando(false)
    }
  }

  return (
    <div className="login">
      <div className="login-palco">
        <div className="login-marca">
          <img src="/logo.png" alt={academia.nomeCompleto} />
          <h1>{academia.logotipo ? <><Logotipo /><span className="sr-only">{academia.nome}</span></> : academia.nome}{academia.subtitulo && <><br /><em>{academia.subtitulo}</em></>}</h1>
          <p>Olá, {usuario?.nome?.split(' ')[0]}! Para proteger os seus dados, crie uma senha só sua.</p>
        </div>
        <div className="caixa">
          <Rotulo>Primeiro acesso</Rotulo>
          <h2>Crie a sua senha</h2>
          <p className="sub">A senha padrão serve só para o primeiro acesso. Nos próximos, entre com o CPF e a senha nova.</p>
          <Erro erro={erro} />
          <form onSubmit={salvar}>
            <div className="campo">
              <label htmlFor="ns">Nova senha</label>
              <input id="ns" type="password" autoComplete="new-password" required minLength={6} maxLength={72}
                value={form.novaSenha} onChange={set('novaSenha')} />
              <span className="ajuda">Mínimo de 6 caracteres, diferente da senha padrão.</span>
            </div>
            <div className="campo">
              <label htmlFor="cs">Confirme a nova senha</label>
              <input id="cs" type="password" autoComplete="new-password" required minLength={6} maxLength={72}
                value={form.confirmacao} onChange={set('confirmacao')} />
            </div>
            <button className="btn btn-primario" disabled={salvando}>
              {salvando ? <Spinner /> : 'Salvar e entrar'}
            </button>
          </form>
          <p className="login-troca"><button type="button" className="btn btn-texto btn-pequeno" onClick={logout}>Sair</button></p>
        </div>
      </div>
      <Rodape escuro />
    </div>
  )
}
