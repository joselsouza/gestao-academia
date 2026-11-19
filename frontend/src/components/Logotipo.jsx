import academia from 'virtual:academia'

/**
 * Nome da academia como logotipo. Usa a imagem `logotipo` do academia.json (se houver);
 * senão, o texto da `marca` em duas partes (ex.: EXEMPLO + BJJ).
 */
export default function Logotipo({ className = '' }) {
  if (academia.logotipo) {
    return (
      <span className={`logotipo ${className}`} role="img" aria-label={academia.nome}
        style={{ '--logotipo-url': `url("${academia.logotipo}")`, '--logotipo-proporcao': academia.logotipoProporcao }} />
    )
  }
  return <span className={className}>{academia.marca[0]}<b>{academia.marca[1]}</b></span>
}
