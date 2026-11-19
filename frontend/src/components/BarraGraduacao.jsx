import { Link } from 'react-router-dom'
import { data as fmtData, nomeFaixa } from '../lib/format.js'

const pct = (valor, minimo) => (minimo ? Math.min(100, Math.round((valor * 100) / minimo)) : 100)

/**
 * Prévia do progresso com presenças ainda não salvas (delta = +1 marcando, -1 desmarcando).
 * Só conta se a data do treino estiver dentro do período atual (desde a última graduação).
 */
export function projetar(status, dataTreino, delta) {
  if (!status || !delta || (status.contandoDesde && dataTreino < status.contandoDesde)) return status
  const presencas = Math.max(0, status.presencas + delta)
  return {
    ...status,
    presencas,
    apto: presencas >= status.presencasMinimas && status.meses >= status.mesesMinimos,
    previa: delta,
  }
}

/** Barra de progresso para a próxima graduação. `compacta` para listas e chamada. */
export default function BarraGraduacao({ status, compacta = false }) {
  if (!status) return null
  const treinos = pct(status.presencas, status.presencasMinimas)
  const tempo = pct(status.meses, status.mesesMinimos)
  const faltamTreinos = Math.max(0, status.presencasMinimas - status.presencas)
  const faltamMeses = Math.max(0, status.mesesMinimos - status.meses)
  const previa = status.previa ? ' previa' : ''

  if (compacta) {
    return (
      <span className="grad-compacta"
        title={`${status.presencas}/${status.presencasMinimas} treinos · ${status.meses}/${status.mesesMinimos} meses desde ${fmtData(status.contandoDesde)}`}>
        <span className={`barra${treinos >= 100 ? ' cheia' : ''}${previa}`}><span style={{ width: `${treinos}%` }} /></span>
        <small>{status.presencas}/{status.presencasMinimas} treinos</small>
        {status.apto && <span className="selo ok">Apto</span>}
      </span>
    )
  }

  return (
    <div className="grad-completa">
      <div className="grad-linha">
        <span className="rotulo-campo">Treinos</span>
        <span className={`barra${treinos >= 100 ? ' cheia' : ''}${previa}`}><span style={{ width: `${treinos}%` }} /></span>
        <strong>{status.presencas}/{status.presencasMinimas}</strong>
      </div>
      <div className="grad-linha">
        <span className="rotulo-campo">Tempo</span>
        <span className={`barra${tempo >= 100 ? ' cheia' : ''}`}><span style={{ width: `${tempo}%` }} /></span>
        <strong>{status.meses}/{status.mesesMinimos} meses</strong>
      </div>
      <p className="muted" style={{ margin: '8px 0 0', fontSize: 13 }}>
        {status.apto ? (
          <><span className="selo ok">Apto a graduar</span> Próximo passo sugerido: <b>{nomeFaixa(status.sugestaoFaixa)}{status.sugestaoGraus ? ` · ${status.sugestaoGraus}º grau` : ''}</b> — <Link to="/graduacao/aptos">graduar</Link></>
        ) : (
          <>Faltam {[faltamTreinos && `${faltamTreinos} treino(s)`, faltamMeses && `${faltamMeses} mês(es)`].filter(Boolean).join(' e ')} · contando desde {fmtData(status.contandoDesde)}</>
        )}
      </p>
    </div>
  )
}
