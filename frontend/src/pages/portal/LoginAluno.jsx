import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../../lib/auth.jsx'
import { cpfMask } from '../../lib/format.js'
import { Erro, Rotulo, Spinner } from '../../components/ui.jsx'
import Rodape from '../../components/Rodape.jsx'
import academia from 'virtual:academia'
import Logotipo from '../../components/Logotipo.jsx'

export default function LoginAluno() {
  const { usuario, loginAluno } = useAuth()
  const navigate = useNavigate()
  const equipe = usuario && usuario.perfil !== 'ALUNO'
  const [cpf, setCpf] = useState('')
  const [senha, setSenha] = useState('')
  const [erro, setErro] = useState(null)
  const [enviando, setEnviando] = useState(false)

  async function entrar(e) {
    e.preventDefault()
    setErro(null)
    setEnviando(true)
    try {
      await loginAluno(cpf, senha)
      navigate('/', { replace: true })
    } catch (err) {
      setErro(err.status === 401 ? 'CPF ou senha inválidos.' : err.message)
      setEnviando(false)
    }
  }

  return (
    <div className="login">
      <div className="login-palco">
        <div className="login-marca">
          <img src="/logo.png" alt={academia.nomeCompleto} />
          <h1>{academia.logotipo ? <><Logotipo /><span className="sr-only">{academia.nome}</span></> : academia.nome}{academia.subtitulo && <><br /><em>{academia.subtitulo}</em></>}</h1>
          <p>Acompanhe as suas mensalidades, pague pelo link e mantenha os seus dados em dia.</p>
        </div>
        <div className="caixa">
          <Rotulo>Área do aluno</Rotulo>
          <h2>Entrar</h2>
          <p className="sub">Use o seu CPF. No primeiro acesso, a senha é a senha padrão informada pela academia.</p>
          {equipe && (
            <div className="alerta-box aviso">
              Este navegador está conectado como <strong>{usuario.nome}</strong> (equipe). Ao entrar como aluno,
              essa sessão será encerrada. <Link to="/">Voltar ao painel</Link>
            </div>
          )}
          <Erro erro={erro} />
          <form onSubmit={entrar}>
            <div className="campo">
              <label htmlFor="cpf">CPF</label>
              <input id="cpf" inputMode="numeric" autoComplete="username" placeholder="000.000.000-00" required
                value={cpf} onChange={(e) => setCpf(cpfMask(e.target.value))} />
            </div>
            <div className="campo">
              <label htmlFor="senha">Senha</label>
              <input id="senha" type="password" autoComplete="current-password" required value={senha}
                onChange={(e) => setSenha(e.target.value)} />
            </div>
            <button className="btn btn-primario" disabled={enviando}>
              {enviando ? <Spinner /> : 'Entrar'}
            </button>
          </form>
          <p className="login-troca">Esqueceu a senha? Peça para a recepção redefinir. {!equipe && <>· <Link to="/entrar">Área da equipe</Link></>}</p>
        </div>
      </div>
      <Rodape escuro />
    </div>
  )
}
