import { useEffect, useState } from 'react'
import { Link, NavLink, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../lib/auth.jsx'
import { MENU, grupoAtivo } from '../lib/menu.js'
import { iniciais } from '../lib/format.js'
import Icon from './Icon.jsx'
import Logotipo from './Logotipo.jsx'
import { ItemNotificacoes } from './Notificacoes.jsx'
import { sincronizarPush } from '../lib/push.js'
import Rodape from './Rodape.jsx'
import academia from 'virtual:academia'

const CHAVE_FIXO = 'academia.menuFixo'

function lerFixo() {
  try { return localStorage.getItem(CHAVE_FIXO) === '1' } catch { return false }
}

function ItemMenu({ g, aberto, ativo, onAlternar }) {
  if (g.to) {
    return (
      <li>
        <NavLink to={g.to} end className={({ isActive }) => `menu-link ${isActive ? 'ativo' : ''}`} title={g.titulo}>
          <Icon nome={g.icone} /> <span className="rotulo-nav">{g.titulo}</span>
        </NavLink>
      </li>
    )
  }
  return (
    <li className={`menu-grupo ${aberto ? 'aberto' : ''} ${ativo ? 'contem-ativo' : ''}`}>
      <button type="button" className="menu-link" onClick={onAlternar} aria-expanded={aberto} title={g.titulo}>
        <Icon nome={g.icone} /> <span className="rotulo-nav">{g.titulo}</span>
        <Icon nome="chevron" className="seta" />
      </button>
      <div className="submenu-wrap">
        <ul className="submenu">
          {g.itens.map((i) => (
            <li key={i.to}>
              <NavLink to={i.to} end className={({ isActive }) => (isActive ? 'ativo' : '')}>{i.titulo}</NavLink>
            </li>
          ))}
        </ul>
      </div>
    </li>
  )
}

const NOME_PERFIL = { ADMIN: 'Administrador', INSTRUTOR: 'Instrutor', ALUNO: 'Aluno' }

export default function Layout({ menu = MENU }) {
  const { usuario, logout } = useAuth()
  const { pathname } = useLocation()

  // Notificações já ativas neste aparelho: confirma no servidor que são de quem está logado.
  useEffect(() => { if (usuario?.perfil) sincronizarPush(usuario.perfil) }, [usuario?.perfil])
  const [menuAberto, setMenuAberto] = useState(false) // drawer no celular
  const [fixo, setFixo] = useState(lerFixo) // menu expandido fixo no desktop
  const [gruposAbertos, setGruposAbertos] = useState(() => new Set([grupoAtivo(pathname)]))

  // Fecha o drawer ao navegar; garante que o grupo da página atual esteja expandido; volta ao topo.
  useEffect(() => {
    setMenuAberto(false)
    const g = grupoAtivo(pathname)
    if (g) setGruposAbertos((s) => (s.has(g) ? s : new Set(s).add(g)))
    window.scrollTo({ top: 0 })
  }, [pathname])

  // Ao voltar para o desktop, o drawer do celular não deve continuar aberto.
  useEffect(() => {
    const mq = window.matchMedia('(min-width: 1024px)')
    const fechar = () => mq.matches && setMenuAberto(false)
    mq.addEventListener('change', fechar)
    return () => mq.removeEventListener('change', fechar)
  }, [])

  function alternarFixo() {
    setFixo((v) => {
      try { localStorage.setItem(CHAVE_FIXO, v ? '0' : '1') } catch { /* ignora */ }
      return !v
    })
  }

  const alternarGrupo = (titulo) =>
    setGruposAbertos((s) => {
      const n = new Set(s)
      if (n.has(titulo)) n.delete(titulo)
      else n.add(titulo)
      return n
    })

  const ativo = grupoAtivo(pathname)
  const visiveis = menu.filter((g) => !g.somenteAdmin || usuario?.perfil === 'ADMIN')
  const item = (g) => (
    <ItemMenu key={g.titulo} g={g} aberto={gruposAbertos.has(g.titulo)} ativo={ativo === g.titulo}
      onAlternar={() => alternarGrupo(g.titulo)} />
  )

  return (
    <div className={`layout ${fixo ? 'fixo' : ''} ${menuAberto ? 'menu-aberto' : ''}`}>
      <header className="topbar">
        <button
          className={`hamburguer ${menuAberto ? 'aberto' : ''}`}
          onClick={() => setMenuAberto((v) => !v)}
          aria-label={menuAberto ? 'Fechar menu' : 'Abrir menu'}
          aria-expanded={menuAberto}
        >
          <span />
        </button>
        <Link to="/" className="marca">
          <img src="/logo.png" alt="" />
          <Logotipo />
        </Link>
      </header>

      <div className={`overlay ${menuAberto ? 'visivel' : ''}`} onClick={() => setMenuAberto(false)} aria-hidden="true" />

      <nav className={`nav ${menuAberto ? 'aberto' : ''}`} aria-label="Menu principal">
        <div className="nav-topo">
          <Link to="/" className="nav-marca" title={academia.nome}>
            <img src="/logo.png" alt={academia.nome} />
            <span className="rotulo-nav"><Logotipo /></span>
          </Link>
          <button type="button" className={`nav-fixar ${fixo ? 'ativo' : ''}`} onClick={alternarFixo}
            title={fixo ? 'Recolher menu' : 'Manter menu aberto'} aria-pressed={fixo}>
            <Icon nome="fixar" />
          </button>
        </div>

        <ul className="menu">{visiveis.filter((g) => !g.base).map(item)}</ul>

        <div className="nav-base">
          <ul className="menu">{visiveis.filter((g) => g.base).map(item)}</ul>
          <ItemNotificacoes perfil={usuario?.perfil} />
          <div className="nav-usuario" title={usuario?.nome}>
            <span className="avatar pequeno">{iniciais(usuario?.nome || '?')}</span>
            <span className="rotulo-nav">
              <strong>{usuario?.nome}</strong>
              <small>{NOME_PERFIL[usuario?.perfil] || usuario?.perfil}</small>
            </span>
          </div>
          <button type="button" className="menu-link" onClick={logout} title="Sair">
            <Icon nome="sair" /> <span className="rotulo-nav">Sair</span>
          </button>
        </div>
      </nav>

      <main className="conteudo">
        <div className="conteudo-inner">
          <div key={pathname} className="pagina">
            <Outlet />
          </div>
        </div>
        <Rodape />
      </main>
    </div>
  )
}
