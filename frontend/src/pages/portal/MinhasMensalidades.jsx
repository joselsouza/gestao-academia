import { api } from '../../lib/api.js'
import { useAuth } from '../../lib/auth.jsx'
import { brl, competenciaLabel, data } from '../../lib/format.js'
import { Cabecalho, Erro, useCarregar } from '../../components/ui.jsx'
import { SeloStatus } from '../financeiro/Mensalidades.jsx'
import { SeloCobranca, tipoLabel } from '../financeiro/asaas.jsx'
import { ConviteNotificacoes } from '../../components/Notificacoes.jsx'

function BotaoPagar({ aberta, link }) {
  if (!aberta) return null
  return link
    ? <a className="btn btn-primario btn-pequeno" href={link} target="_blank" rel="noopener noreferrer">Pagar</a>
    : <small className="muted">Link ainda não disponível. Fale com a recepção.</small>
}

export default function MinhasMensalidades() {
  const { atualizarUsuario } = useAuth()
  const { dados, erro, carregando } = useCarregar(async () => {
    try {
      const [mensalidades, cobrancas] = await Promise.all([
        api.get('/api/aluno/me/mensalidades'),
        api.get('/api/aluno/me/cobrancas'),
      ])
      return { mensalidades, cobrancas }
    } catch (e) {
      // 403 = a recepção redefiniu a senha: volta para a tela de primeiro acesso.
      if (e.status === 403) atualizarUsuario({ senhaPadrao: true })
      throw e
    }
  }, [atualizarUsuario], 'aluno:financeiro')

  const mensalidades = dados?.mensalidades || []
  const cobrancas = dados?.cobrancas || []

  // Tudo o que está em aberto (mensalidades + cobranças avulsas), no mesmo formato.
  const abertos = [
    ...mensalidades.filter((m) => m.status === 'PENDENTE').map((m) => ({ valor: m.valor, vencimento: m.vencimento, atrasado: m.atrasada })),
    ...cobrancas.filter((c) => c.status === 'PENDENTE' || c.status === 'VENCIDA')
      .map((c) => ({ valor: c.valor, vencimento: c.vencimento, atrasado: c.status === 'VENCIDA' })),
  ]
  const atrasados = abertos.filter((x) => x.atrasado)
  const soma = (l) => l.reduce((s, x) => s + Number(x.valor), 0)
  const proximo = abertos.filter((x) => !x.atrasado && x.vencimento).sort((a, b) => a.vencimento.localeCompare(b.vencimento))[0]

  return (
    <>
      <Cabecalho secao="Área do aluno" titulo="Financeiro"
        sub="As suas mensalidades e outras cobranças. Pague pelo link (PIX, cartão ou boleto): a baixa é automática." />
      <Erro erro={erro} />
      <ConviteNotificacoes perfil="ALUNO" />

      {dados && (
        <div className="grade-kpi">
          <div className={`kpi ${atrasados.length ? 'vermelho' : 'verde'}`}>
            <div className="rotulo">Em aberto</div>
            <div className="valor">{brl(soma(abertos))}</div>
            <div className="detalhe">{abertos.length ? `${abertos.length} cobrança(s)` : 'Tudo em dia'}</div>
          </div>
          <div className="kpi">
            <div className="rotulo">Próximo vencimento</div>
            <div className="valor">{proximo ? data(proximo.vencimento) : '—'}</div>
            <div className="detalhe">{proximo ? brl(proximo.valor) : 'Nada a vencer'}</div>
          </div>
          {atrasados.length > 0 && (
            <div className="kpi vermelho">
              <div className="rotulo">Atrasadas</div>
              <div className="valor">{atrasados.length}</div>
              <div className="detalhe">{brl(soma(atrasados))}</div>
            </div>
          )}
        </div>
      )}

      <div className="card" style={{ padding: 0 }}>
        <h2 style={{ padding: '20px 20px 0' }}>Mensalidades</h2>
        {carregando && !dados ? <div className="carregando">Carregando…</div> : mensalidades.length === 0 ? (
          <div className="vazio">Nenhuma mensalidade por aqui ainda.</div>
        ) : (
          <table className="tabela">
            <thead><tr><th>Mês</th><th>Vencimento</th><th className="num">Valor</th><th>Situação</th><th>Pagamento</th><th /></tr></thead>
            <tbody>
              {mensalidades.map((m) => (
                <tr key={m.id}>
                  <td className="sem-rotulo"><strong style={{ textTransform: 'capitalize' }}>{competenciaLabel(m.competencia.slice(0, 7))}</strong></td>
                  <td data-label="Vencimento">{data(m.vencimento)}</td>
                  <td data-label="Valor" className="num">{brl(m.valor)}</td>
                  <td data-label="Situação"><SeloStatus m={m} /></td>
                  <td data-label="Pagamento">{m.dataPagamento ? data(m.dataPagamento) : '—'}</td>
                  <td className="acoes-linha"><BotaoPagar aberta={m.status === 'PENDENTE'} link={m.link} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {cobrancas.length > 0 && (
        <div className="card" style={{ padding: 0 }}>
          <h2 style={{ padding: '20px 20px 0' }}>Outras cobranças</h2>
          <table className="tabela">
            <thead><tr><th>Descrição</th><th>Vencimento</th><th className="num">Valor</th><th>Situação</th><th>Pagamento</th><th /></tr></thead>
            <tbody>
              {cobrancas.map((c) => (
                <tr key={c.id}>
                  <td className="sem-rotulo">
                    <strong>{c.descricao || 'Cobrança'}</strong><br /><small className="muted">{tipoLabel(c)}</small>
                  </td>
                  <td data-label="Vencimento">{data(c.vencimento)}</td>
                  <td data-label="Valor" className="num">{brl(c.valor)}</td>
                  <td data-label="Situação"><SeloCobranca c={c} /></td>
                  <td data-label="Pagamento">{c.dataPagamento ? data(c.dataPagamento) : '—'}</td>
                  <td className="acoes-linha"><BotaoPagar aberta={c.status === 'PENDENTE' || c.status === 'VENCIDA'} link={c.link} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  )
}
