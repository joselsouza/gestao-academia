import { Link } from 'react-router-dom'
import { api } from '../lib/api.js'
import { brl, competenciaLabel, hojeISO } from '../lib/format.js'
import { Cabecalho, CardTopo, Contador, Erro, Rotulo, useCarregar } from '../components/ui.jsx'
import GraficoLinha from '../components/GraficoLinha.jsx'
import Icon from '../components/Icon.jsx'
import { ConviteNotificacoes } from '../components/Notificacoes.jsx'

const real = (v) => brl(v)

export default function Dashboard() {
  const { dados: d, erro, carregando } = useCarregar(() => api.get('/api/dashboard'), [], 'painel')
  if (carregando && !d) return <div className="carregando">Carregando</div>
  if (erro && !d) return <Erro erro={erro} />

  const f = d.financeiroMes
  const hoje = hojeISO()
  const totalPeriodo = d.presencasUltimos14Dias.reduce((s, x) => s + x.total, 0)
  const pctRecebido = f.previsto > 0 ? Math.min(100, Math.round((f.recebido / f.previsto) * 100)) : 0

  return (
    <>
      <Cabecalho titulo="Painel" sub="A academia inteira em um relance.">
        <Link className="btn btn-secundario" to="/presenca/chamada">Chamada manual</Link>
        <Link className="btn btn-primario" to="/presenca/reconhecimento"><Icon nome="camera" /> Marcar presença</Link>
      </Cabecalho>
      <Erro erro={erro} />
      <ConviteNotificacoes perfil="EQUIPE" />

      <div className="painel-topo">
        <div className="card card-numero">
          <div className="card-topo" style={{ marginBottom: 0 }}>
            <Rotulo>Alunos ativos</Rotulo>
            <div className="card-meta">{d.bolsistas} bolsista(s)</div>
          </div>
          <div className="numero"><Contador valor={d.alunosAtivos} /></div>
          <div className="legenda">matriculados / todas as faixas</div>
          <Link className="btn btn-primario" to="/alunos">Ver alunos</Link>
        </div>

        <div className="card">
          <CardTopo rotulo="Frequência no tempo" titulo="Presenças nos últimos 14 dias"
            meta={<>{totalPeriodo} no período</>} />
          <GraficoLinha pontos={d.presencasUltimos14Dias} destaque={hoje} unidade=" presente(s)" />
        </div>
      </div>

      <div className="grade-kpi">
        <Link to="/presenca/historico" className="kpi">
          <span className="kpi-icone"><Icon nome="presenca" /></span>
          <div className="rotulo">Presenças hoje</div>
          <div className="valor"><Contador valor={d.presencasHoje} /></div>
        </Link>
        <Link to="/graduacao/aptos" className={`kpi ${d.aptosGraduacao ? 'verde' : ''}`}>
          <span className="kpi-icone"><Icon nome="seta-cima" /></span>
          <div className="rotulo">Aptos a graduar</div>
          <div className="valor"><Contador valor={d.aptosGraduacao} /></div>
        </Link>
        <Link to="/financeiro/inadimplentes" className={`kpi ${d.inadimplentes ? 'vermelho' : ''}`}>
          <span className="kpi-icone"><Icon nome="seta-baixo" /></span>
          <div className="rotulo">Inadimplentes</div>
          <div className="valor"><Contador valor={d.inadimplentes} /></div>
        </Link>
      </div>

      <div className="card">
        <CardTopo rotulo="Financeiro" titulo={`Mensalidades de ${competenciaLabel(f.competencia)}`}
          meta={<Link to="/financeiro/mensalidades">Abrir mensalidades →</Link>} />

        <div className="progresso-fin">
          <div className="linha-info"><span>Recebido {pctRecebido}%</span><span>{brl(f.recebido)} de {brl(f.previsto)}</span></div>
          <div className={`barra ${pctRecebido >= 100 ? 'cheia' : ''}`}><div style={{ width: `${pctRecebido}%` }} /></div>
        </div>

        <div className="grade-kpi">
          <div className="kpi"><div className="rotulo">Previsto</div><div className="valor"><Contador valor={f.previsto} formatar={real} /></div><div className="detalhe">{f.qtdPagas + f.qtdPendentes} mensalidade(s)</div></div>
          <div className="kpi verde"><div className="rotulo">Recebido</div><div className="valor"><Contador valor={f.recebido} formatar={real} /></div><div className="detalhe">{f.qtdPagas} paga(s)</div></div>
          <div className="kpi"><div className="rotulo">Em aberto</div><div className="valor"><Contador valor={f.pendente} formatar={real} /></div><div className="detalhe">{f.qtdPendentes} pendente(s)</div></div>
          <div className="kpi vermelho"><div className="rotulo">Atrasado</div><div className="valor"><Contador valor={f.atrasado} formatar={real} /></div><div className="detalhe">{f.qtdAtrasadas} atrasada(s)</div></div>
        </div>

        {f.qtdPagas + f.qtdPendentes === 0 && (
          <p className="muted" style={{ margin: '20px 0 0', textAlign: 'center' }}>
            Nenhuma mensalidade gerada neste mês. <Link to="/financeiro/mensalidades">Gerar agora</Link>
          </p>
        )}
      </div>
    </>
  )
}
