import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { api, setUnauthorizedHandler, tokenStore } from './api.js'
import { limparCache } from '../components/ui.jsx'
import { esquecerPushAoSair } from './push.js'

const AuthContext = createContext(null)
const USER_KEY = 'academia.user'
const ULTIMO_PERFIL_KEY = 'academia.ultimoPerfil'

/** Perfil da última sessão: ao sair (ou expirar), o aluno volta para o login do aluno. */
// eslint-disable-next-line react-refresh/only-export-components
export function ultimoPerfil() {
  try { return localStorage.getItem(ULTIMO_PERFIL_KEY) } catch { return null }
}

function lerUsuario() {
  try { return JSON.parse(localStorage.getItem(USER_KEY)) } catch { return null }
}

export function AuthProvider({ children }) {
  const [usuario, setUsuario] = useState(() => (tokenStore.get() ? lerUsuario() : null))

  const logout = useCallback(() => {
    tokenStore.clear()
    limparCache()
    try { localStorage.removeItem(USER_KEY) } catch { /* ignora */ }
    setUsuario(null)
  }, [])

  useEffect(() => { setUnauthorizedHandler(logout) }, [logout])

  /** "Sair" pelo botão: este aparelho também para de receber as notificações desta pessoa. */
  const sair = useCallback(() => {
    esquecerPushAoSair(lerUsuario()?.perfil)
    logout()
  }, [logout])

  const guardar = useCallback((u) => {
    try {
      localStorage.setItem(USER_KEY, JSON.stringify(u))
      localStorage.setItem(ULTIMO_PERFIL_KEY, u.perfil)
    } catch { /* ignora */ }
    setUsuario(u)
  }, [])

  const login = useCallback(async (email, senha) => {
    const r = await api.post('/api/auth/login', { email, senha })
    tokenStore.set(r.token)
    guardar({ nome: r.nome, email: r.email, perfil: r.perfil })
  }, [guardar])

  /** Portal do aluno: CPF + senha (a senha padrão da academia, até o aluno trocar). */
  const loginAluno = useCallback(async (cpf, senha) => {
    const r = await api.post('/api/aluno/auth/login', { cpf, senha })
    tokenStore.set(r.token)
    limparCache() // pode haver dados da equipe em memória (mesmo navegador)
    guardar({ nome: r.nome, perfil: 'ALUNO', senhaPadrao: r.senhaPadrao })
  }, [guardar])

  /** Atualiza os dados da sessão (ex.: aluno mudou o nome ou trocou a senha padrão). */
  const atualizarUsuario = useCallback((campos) => {
    setUsuario((u) => {
      const n = { ...u, ...campos }
      try { localStorage.setItem(USER_KEY, JSON.stringify(n)) } catch { /* ignora */ }
      return n
    })
  }, [])

  const value = useMemo(() => ({ usuario, login, loginAluno, logout: sair, atualizarUsuario }),
    [usuario, login, loginAluno, sair, atualizarUsuario])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export const useAuth = () => useContext(AuthContext)
