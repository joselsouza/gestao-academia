import { abrirPreferenciasCookies } from '../lib/cookies.js'

export default function Rodape({ escuro = false }) {
  return (
    <footer className={`rodape-site ${escuro ? 'escuro' : ''}`}>
      <span>
        Feito por{' '}
        <a href="https://github.com/gagoraro" target="_blank" rel="noreferrer">José Neto</a>
      </span>
      <button className="btn-link" onClick={abrirPreferenciasCookies}>Preferências de cookies</button>
    </footer>
  )
}
