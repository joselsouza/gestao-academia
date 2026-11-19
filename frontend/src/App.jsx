import { useEffect } from 'react'
import { Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { carregarModelos } from './lib/face.js'
import Layout from './components/Layout.jsx'
import { ultimoPerfil, useAuth } from './lib/auth.jsx'
import { MENU_ALUNO } from './lib/menu.js'
import Login from './pages/Login.jsx'
import Dashboard from './pages/Dashboard.jsx'
import AlunosLista from './pages/alunos/AlunosLista.jsx'
import AlunoForm from './pages/alunos/AlunoForm.jsx'
import Planos from './pages/planos/Planos.jsx'
import Mensalidades from './pages/financeiro/Mensalidades.jsx'
import Inadimplentes from './pages/financeiro/Inadimplentes.jsx'
import CobrancasAvulsas from './pages/financeiro/CobrancasAvulsas.jsx'
import NovaCobranca from './pages/financeiro/NovaCobranca.jsx'
import Reconhecimento from './pages/presenca/Reconhecimento.jsx'
import Chamada from './pages/presenca/Chamada.jsx'
import HistoricoPresenca from './pages/presenca/HistoricoPresenca.jsx'
import Aptos from './pages/graduacao/Aptos.jsx'
import Regras from './pages/graduacao/Regras.jsx'
import HistoricoGraduacao from './pages/graduacao/HistoricoGraduacao.jsx'
import Usuarios from './pages/usuarios/Usuarios.jsx'
import CookieBanner from './components/CookieBanner.jsx'
import LoginAluno from './pages/portal/LoginAluno.jsx'
import MinhasMensalidades from './pages/portal/MinhasMensalidades.jsx'
import MeuPerfil from './pages/portal/MeuPerfil.jsx'
import PrimeiroAcesso from './pages/portal/PrimeiroAcesso.jsx'

export default function App() {
  const { usuario } = useAuth()
  const { pathname } = useLocation()

  // Portal do aluno: "Adicionar à Tela de Início" cria um ícone que abre direto no login do aluno.
  const ehAluno = usuario ? usuario.perfil === 'ALUNO' : (pathname.startsWith('/aluno') || ultimoPerfil() === 'ALUNO')
  useEffect(() => {
    const link = document.querySelector('link[rel="manifest"]')
    if (link) link.setAttribute('href', ehAluno ? '/site-aluno.webmanifest' : '/site.webmanifest')
  }, [ehAluno])

  // Depois do login, prepara o reconhecimento facial em segundo plano (Web Worker, sem travar a
  // tela). Assim, ao abrir Presença, ele já está pronto. Espera alguns segundos para não disputar
  // a rede com o carregamento inicial do painel.
  useEffect(() => {
    if (!usuario || usuario.perfil === 'ALUNO') return undefined
    const t = setTimeout(() => { carregarModelos().catch(() => {}) }, 4000)
    return () => clearTimeout(t)
  }, [usuario])

  if (!usuario) {
    return (
      <>
        <Routes>
          <Route path="aluno" element={<LoginAluno />} />
          <Route path="entrar" element={<Login />} />
          {/* Sessão expirada ou "Sair": cada um volta para o seu login. */}
          <Route path="*" element={ultimoPerfil() === 'ALUNO' ? <LoginAluno /> : <Login />} />
        </Routes>
        <CookieBanner />
      </>
    )
  }

  // Link do portal (/aluno) aberto num navegador com a sessão da equipe: mostra o login do aluno
  // (com aviso) em vez de cair no painel.
  if (usuario.perfil !== 'ALUNO' && /^\/aluno\/?$/.test(pathname)) {
    return <LoginAluno />
  }

  // Primeiro acesso do aluno (senha padrão): só libera o portal depois de criar a própria senha.
  if (usuario.perfil === 'ALUNO' && usuario.senhaPadrao) {
    return (
      <Routes>
        <Route path="primeiro-acesso" element={<PrimeiroAcesso />} />
        <Route path="*" element={<Navigate to="/primeiro-acesso" replace />} />
      </Routes>
    )
  }

  // Portal do aluno: só o financeiro dele e o próprio perfil.
  if (usuario.perfil === 'ALUNO') {
    return (
      <>
        <Routes>
          <Route element={<Layout menu={MENU_ALUNO} />}>
            <Route path="financeiro" element={<MinhasMensalidades />} />
            <Route path="perfil" element={<MeuPerfil />} />
            <Route path="*" element={<Navigate to="/financeiro" replace />} />
          </Route>
        </Routes>
        <CookieBanner />
      </>
    )
  }

  return (
    <>
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<Dashboard />} />
        <Route path="alunos" element={<AlunosLista />} />
        <Route path="alunos/novo" element={<AlunoForm />} />
        <Route path="alunos/:id" element={<AlunoForm />} />
        <Route path="planos" element={<Planos />} />
        <Route path="financeiro/mensalidades" element={<Mensalidades />} />
        <Route path="financeiro/inadimplentes" element={<Inadimplentes />} />
        <Route path="financeiro/cobrancas" element={<CobrancasAvulsas />} />
        <Route path="financeiro/cobrancas/nova" element={<NovaCobranca />} />
        <Route path="cobrancas/*" element={<Navigate to="/financeiro/mensalidades" replace />} />
        <Route path="presenca/reconhecimento" element={<Reconhecimento />} />
        <Route path="presenca/chamada" element={<Chamada />} />
        <Route path="presenca/historico" element={<HistoricoPresenca />} />
        <Route path="graduacao/aptos" element={<Aptos />} />
        <Route path="graduacao/regras" element={<Regras />} />
        <Route path="graduacao/historico" element={<HistoricoGraduacao />} />
        {usuario.perfil === 'ADMIN' && <Route path="usuarios" element={<Usuarios />} />}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
    <CookieBanner />
    </>
  )
}
