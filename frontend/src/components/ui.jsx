import { useCallback, useEffect, useRef, useState } from 'react'
import { useLocation } from 'react-router-dom'
import { COR_FAIXA, iniciais, nomeFaixa } from '../lib/format.js'
import { secaoDaRota } from '../lib/menu.js'
import Icon from './Icon.jsx'

export function Faixa({ faixa, graus = 0, comNome = true }) {
  if (!faixa) return null
  const listras = Array.from({ length: graus }, (_, i) => <i key={i} />)
  return (
    <span className="faixa-rotulo" title={`${nomeFaixa(faixa)} · ${graus} grau(s)`}>
      <span className={`faixa ${faixa === 'PRETA' ? 'preta' : ''}`}>
        <span className="corpo" style={{ background: COR_FAIXA[faixa] }} />
        <span className="ponteira">{listras}</span>
      </span>
      {comNome && <span>{nomeFaixa(faixa)}{graus > 0 ? ` · ${graus}º` : ''}</span>}
    </span>
  )
}

export function Avatar({ nome, foto, grande = false }) {
  const cls = `avatar${grande ? ' grande' : ''}`
  return foto ? <img className={cls} src={foto} alt={nome} /> : <span className={cls}>{iniciais(nome)}</span>
}

export function Pessoa({ nome, foto, sub }) {
  return (
    <div className="pessoa">
      <Avatar nome={nome} foto={foto} />
      <div className="info">
        <strong>{nome}</strong>
        {sub && <small>{sub}</small>}
      </div>
    </div>
  )
}

/** Rótulo pequeno em caixa-alta com o marcador quadrado (ex.: ▪ VISÃO GERAL). */
export function Rotulo({ children, className = '' }) {
  return <span className={`eyebrow ${className}`}>{children}</span>
}

/** Cabeçalho da página: seção do menu, título grande em serifa, subtítulo opcional e ações. */
export function Cabecalho({ titulo, sub, secao, children }) {
  const { pathname } = useLocation()
  return (
    <header className="pagina-cabecalho">
      <div className="titulos">
        <Rotulo>{secao || secaoDaRota(pathname)}</Rotulo>
        <h1>{titulo}</h1>
        {sub && <p className="cabecalho-sub">{sub}</p>}
      </div>
      {children && <div className="acoes">{children}</div>}
    </header>
  )
}

/** Topo de card: rótulo + título em serifa + informação à direita. */
export function CardTopo({ rotulo, titulo, meta }) {
  return (
    <div className="card-topo">
      <div>
        {rotulo && <Rotulo>{rotulo}</Rotulo>}
        {titulo && <h2 className="card-titulo">{titulo}</h2>}
      </div>
      {meta && <div className="card-meta">{meta}</div>}
    </div>
  )
}

const semMovimento = () => window.matchMedia?.('(prefers-reduced-motion: reduce)').matches

/** Número que "conta" até o valor ao aparecer ou mudar. */
export function Contador({ valor, formatar = (v) => Math.round(v).toLocaleString('pt-BR'), duracao = 800 }) {
  const alvo = Number(valor) || 0
  const [atual, setAtual] = useState(() => (semMovimento() ? alvo : 0))
  const de = useRef(atual)
  useEffect(() => {
    if (semMovimento()) { de.current = alvo; return undefined }
    const inicio = performance.now()
    const origem = de.current
    let id
    const passo = (t) => {
      const p = Math.min(1, (t - inicio) / duracao)
      const v = origem + (alvo - origem) * (1 - (1 - p) ** 3)
      de.current = v
      setAtual(v)
      if (p < 1) id = requestAnimationFrame(passo)
    }
    id = requestAnimationFrame(passo)
    return () => cancelAnimationFrame(id)
  }, [alvo, duracao])
  return <>{formatar(semMovimento() ? alvo : atual)}</>
}

export function Modal({ titulo, onFechar, children, rodape }) {
  useEffect(() => {
    const esc = (e) => e.key === 'Escape' && onFechar()
    window.addEventListener('keydown', esc)
    return () => window.removeEventListener('keydown', esc)
  }, [onFechar])
  return (
    <div className="modal-fundo" onMouseDown={(e) => e.target === e.currentTarget && onFechar()}>
      <div className="modal" role="dialog" aria-modal="true" aria-label={titulo}>
        <header>
          <h2>{titulo}</h2>
          <button className="btn btn-icone" onClick={onFechar} aria-label="Fechar"><Icon nome="fechar" /></button>
        </header>
        <div className="corpo">{children}</div>
        {rodape && <footer>{rodape}</footer>}
      </div>
    </div>
  )
}

export function Erro({ erro }) {
  return erro ? <div className="alerta-box erro" role="alert">{erro}</div> : null
}

export function Spinner() {
  return <span className="spin" aria-label="Carregando" />
}

// Cache em memória: ao voltar para uma tela, os últimos dados aparecem na hora
// enquanto a versão atualizada é buscada em segundo plano.
const cache = new Map()
export const limparCache = () => cache.clear()

/**
 * Carrega dados da API e expõe { dados, erro, carregando, recarregar }.
 * Com `chave`, mostra imediatamente o último resultado guardado para essa chave.
 */
export function useCarregar(fn, deps = [], chave = null) {
  const [estado, setEstado] = useState(() => ({ dados: chave ? cache.get(chave) ?? null : null, erro: null, carregando: true }))
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const carregar = useCallback(fn, deps)
  const recarregar = useCallback(async () => {
    setEstado((s) => ({ dados: chave ? cache.get(chave) ?? null : s.dados, erro: null, carregando: true }))
    try {
      const dados = await carregar()
      if (chave) cache.set(chave, dados)
      setEstado({ dados, erro: null, carregando: false })
    } catch (e) {
      setEstado((s) => ({ dados: s.dados, erro: e.message, carregando: false }))
    }
  }, [carregar, chave])
  useEffect(() => { recarregar() }, [recarregar])
  return { ...estado, recarregar }
}
