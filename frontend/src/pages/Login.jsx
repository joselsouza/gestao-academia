import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../lib/auth.jsx'
import { Erro, Rotulo, Spinner } from '../components/ui.jsx'
import Rodape from '../components/Rodape.jsx'
import { EMAIL, apagarCookie, gravarCookie, lerCookie, podeUsarFuncionais } from '../lib/cookies.js'
import academia from 'virtual:academia'
import Logotipo from '../components/Logotipo.jsx'

export default function Login() {
  const { login } = useAuth()
  const [email, setEmail] = useState(() => lerCookie(EMAIL) || '')
  const [lembrar, setLembrar] = useState(() => !!lerCookie(EMAIL))
  const [senha, setSenha] = useState('')
  const [erro, setErro] = useState(null)
  const [enviando, setEnviando] = useState(false)

  async function entrar(e) {
    e.preventDefault()
    setErro(null)
    setEnviando(true)
    try {
      if (lembrar && podeUsarFuncionais()) gravarCookie(EMAIL, email.trim())
      else apagarCookie(EMAIL)
      await login(email, senha)
    } catch (err) {
      setErro(err.status === 401 ? 'E-mail ou senha inválidos.' : err.message)
    } finally {
      setEnviando(false)
    }
  }

  return (
    <div className="login">
      <div className="login-palco">
      <div className="login-marca">
        <img src="/logo.png" alt={academia.nomeCompleto} />
        <h1>{academia.logotipo ? <><Logotipo /><span className="sr-only">{academia.nome}</span></> : academia.nome}{academia.subtitulo && <><br /><em>{academia.subtitulo}</em></>}</h1>
        <p>Alunos, presença, graduações e mensalidades da academia em um só lugar.</p>
      </div>
      <div className="caixa">
        <Rotulo>Área administrativa</Rotulo>
        <h2>Entrar</h2>
        <p className="sub">Use o e-mail e a senha cadastrados para você.</p>
        <Erro erro={erro} />
        <form onSubmit={entrar}>
          <div className="campo">
            <label htmlFor="email">E-mail</label>
            <input id="email" type="email" autoComplete="username" required value={email}
              onChange={(e) => setEmail(e.target.value)} />
          </div>
          <div className="campo">
            <label htmlFor="senha">Senha</label>
            <input id="senha" type="password" autoComplete="current-password" required value={senha}
              onChange={(e) => setSenha(e.target.value)} />
          </div>
          <label className="checkbox">
            <input type="checkbox" checked={lembrar} onChange={(e) => setLembrar(e.target.checked)} />
            <span>Lembrar meu e-mail neste navegador <small className="muted">(requer aceitar os cookies funcionais)</small></span>
          </label>
          <button className="btn btn-primario" disabled={enviando}>
            {enviando ? <Spinner /> : 'Entrar'}
          </button>
        </form>
        <p className="login-troca">É aluno? <Link to="/aluno">Entre com o seu CPF</Link></p>
      </div>
      </div>
      <Rodape escuro />
    </div>
  )
}
