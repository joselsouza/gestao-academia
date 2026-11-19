import { useId } from 'react'

const MESES = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez']
const rotuloDia = (iso) => `${iso.slice(8, 10)} ${MESES[Number(iso.slice(5, 7)) - 1]}`

/** Teto "redondo" do eixo Y e os valores das linhas de grade (4 intervalos inteiros). */
function escala(maximo) {
  const passo = Math.max(1, Math.ceil(maximo / 4))
  return { teto: passo * 4, marcas: [0, 1, 2, 3, 4].map((i) => i * passo) }
}

/**
 * Gráfico de linha simples (sem dependências). `pontos` = [{ data: 'yyyy-mm-dd', total }].
 * O ponto de `destaque` (ex.: hoje) aparece maior.
 */
export default function GraficoLinha({ pontos, destaque, unidade = '' }) {
  const id = useId()
  const n = pontos.length
  if (!n) return null
  const { teto, marcas } = escala(Math.max(0, ...pontos.map((p) => p.total)))
  const x = (i) => (n === 1 ? 50 : (i / (n - 1)) * 100)
  const y = (v) => 100 - (v / teto) * 100
  const linha = pontos.map((p, i) => `${i ? 'L' : 'M'}${x(i)} ${y(p.total)}`).join(' ')
  const area = `${linha} L100 100 L0 100 Z`

  return (
    <div className="grafico" role="img"
      aria-label={pontos.map((p) => `${rotuloDia(p.data)}: ${p.total}`).join(', ')}>
      <div className="eixo-y" aria-hidden="true">
        {marcas.map((m) => (
          <div key={m} style={{ top: `${y(m)}%` }}><span>{m}</span></div>
        ))}
      </div>
      <div className="area" aria-hidden="true">
        <svg viewBox="0 0 100 100" preserveAspectRatio="none">
          <defs>
            <linearGradient id={`g${id}`} x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="currentColor" stopOpacity=".1" />
              <stop offset="100%" stopColor="currentColor" stopOpacity="0" />
            </linearGradient>
          </defs>
          <path d={area} fill={`url(#g${id})`} />
          <path d={linha} fill="none" stroke="currentColor" strokeWidth="1.6" vectorEffect="non-scaling-stroke" strokeLinejoin="round" />
        </svg>
        {pontos.map((p, i) => (
          <span key={p.data} className={`ponto ${p.data === destaque ? 'hoje' : ''} ${i < n * 0.2 ? 'inicio' : i > n * 0.8 ? 'fim' : ''}`}
            style={{ left: `${x(i)}%`, top: `${y(p.total)}%` }}>
            <span className="dica">{rotuloDia(p.data)} · {p.total}{unidade}</span>
          </span>
        ))}
      </div>
      <div className="eixo-x" aria-hidden="true">
        {pontos.map((p, i) => (
          <span key={p.data} className={p.data === destaque ? 'hoje' : ''} style={{ left: `${x(i)}%` }}>{rotuloDia(p.data)}</span>
        ))}
      </div>
    </div>
  )
}
