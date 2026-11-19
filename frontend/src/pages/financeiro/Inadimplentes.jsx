import { useState } from 'react'
import { api } from '../../lib/api.js'
import { brl, competenciaLabel, data, telefoneMask, whatsappLink } from '../../lib/format.js'
import { Cabecalho, Erro, Pessoa, useCarregar } from '../../components/ui.jsx'
import { ModalPagamento } from './Mensalidades.jsx'
import { AcoesLink, contato } from './asaas.jsx'
import academia from 'virtual:academia'

function diasAtraso(venc) {
  return Math.floor((Date.now() - new Date(`${venc}T00:00:00`).getTime()) / 86400000)
}

export default function Inadimplentes() {
  const { dados: d, erro, carregando, recarregar } = useCarregar(async () => {
    const [lista, config] = await Promise.all([
      api.get('/api/financeiro/inadimplentes'), api.get('/api/cobrancas/config').catch(() => null),
    ])
    return { lista, config }
  })
  const dados = d?.lista
  const asaasOk = Boolean(d?.config?.configurado)
  const [pagando, setPagando] = useState(null)
  const [erroAcao, setErroAcao] = useState(null)
  const total = (dados || []).reduce((s, m) => s + Number(m.valor), 0)

  return (
    <>
      <Cabecalho titulo="Inadimplentes" sub={<>Mensalidades pendentes com vencimento já passado, de qualquer mês.</>} />
      <Erro erro={erro || erroAcao} />
      {dados?.length > 0 && (
        <div className="grade-kpi">
          <div className="kpi vermelho"><div className="rotulo">Total em atraso</div><div className="valor">{brl(total)}</div></div>
          <div className="kpi"><div className="rotulo">Mensalidades</div><div className="valor">{dados.length}</div></div>
          <div className="kpi"><div className="rotulo">Alunos</div><div className="valor">{new Set(dados.map((m) => m.alunoId)).size}</div></div>
        </div>
      )}
      <div className="card" style={{ padding: 0 }}>
        {carregando && !dados ? <div className="carregando">Carregando…</div> : !dados?.length ? (
          <div className="vazio">Nenhum aluno inadimplente.</div>
        ) : (
          <table className="tabela">
            <thead><tr><th>Aluno</th><th>Competência</th><th>Vencimento</th><th className="num">Atraso</th><th className="num">Valor</th><th /></tr></thead>
            <tbody>
              {dados.map((m) => {
                const ct = contato(m)
                const wa = whatsappLink(ct.telefone)
                const texto = encodeURIComponent(`Olá, ${ct.nome}! Passando para lembrar da mensalidade ${academia.nome} de ${competenciaLabel(m.competencia.slice(0, 7))}${ct.referente} (${brl(m.valor)}), vencida em ${data(m.vencimento)}.${academia.despedida ? ` ${academia.despedida}` : ''}`)
                return (
                  <tr key={m.id}>
                    <td className="sem-rotulo"><Pessoa nome={m.alunoNome} sub={[m.responsavelNome && `Resp.: ${m.responsavelNome}`, ct.telefone && telefoneMask(ct.telefone)].filter(Boolean).join(' · ') || null} /></td>
                    <td data-label="Competência">{competenciaLabel(m.competencia.slice(0, 7))}</td>
                    <td data-label="Vencimento">{data(m.vencimento)}</td>
                    <td data-label="Atraso" className="num"><span className="selo erro">{diasAtraso(m.vencimento)} dia(s)</span></td>
                    <td data-label="Valor" className="num">{brl(m.valor)}</td>
                    <td className="acoes-linha">
                      {asaasOk && <AcoesLink m={m} atrasada onGerado={recarregar} onErro={setErroAcao} />}
                      {wa && !m.link && <a className="btn btn-secundario btn-pequeno" href={`${wa}?text=${texto}`} target="_blank" rel="noreferrer">Cobrar</a>}{' '}
                      <button className="btn btn-primario btn-pequeno" onClick={() => setPagando(m)}>Receber</button>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        )}
      </div>
      {pagando && <ModalPagamento mensalidade={pagando} onFechar={() => setPagando(null)} onPago={() => { setPagando(null); recarregar() }} />}
    </>
  )
}
