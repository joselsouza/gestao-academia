import { useEffect, useState } from 'react'
import { consentimento, definirConsentimento } from '../lib/cookies.js'

export default function CookieBanner() {
  const [aberto, setAberto] = useState(() => !consentimento())
  const [detalhes, setDetalhes] = useState(false)

  useEffect(() => {
    const abrir = () => { setDetalhes(true); setAberto(true) }
    window.addEventListener('af-cookies-abrir', abrir)
    return () => window.removeEventListener('af-cookies-abrir', abrir)
  }, [])

  if (!aberto) return null

  const escolher = (valor) => { definirConsentimento(valor); setAberto(false); setDetalhes(false) }

  return (
    <div className="cookie-banner" role="dialog" aria-live="polite" aria-label="Aviso de cookies">
      <div className="cookie-texto">
        <strong>🍪 Este site usa cookies</strong>
        <p>
          Usamos cookies e armazenamento local essenciais para manter você conectado e guardar suas preferências.
          Com a sua permissão, também lembramos o seu e-mail na tela de login.
        </p>
        {detalhes && (
          <ul className="cookie-lista">
            <li><b>Essenciais</b> (sempre ativos): sessão de login e registro da sua escolha sobre cookies.</li>
            <li><b>Funcionais</b> (opcionais): lembrar o e-mail de acesso neste navegador.</li>
            <li>Não usamos cookies de publicidade nem de rastreamento.</li>
          </ul>
        )}
        <button className="btn-link" onClick={() => setDetalhes((v) => !v)}>
          {detalhes ? 'Ocultar detalhes' : 'Saiba mais'}
        </button>
      </div>
      <div className="cookie-acoes">
        <button className="btn btn-secundario" onClick={() => escolher('essenciais')}>Somente essenciais</button>
        <button className="btn btn-primario" onClick={() => escolher('todos')}>Aceitar todos</button>
      </div>
    </div>
  )
}
