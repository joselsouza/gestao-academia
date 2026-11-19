import { useEffect, useState } from 'react'
import { ativarPush, desativarPush, ehIOS, estadoPush } from '../lib/push.js'
import Icon from './Icon.jsx'
import { Modal, Rotulo, Spinner } from './ui.jsx'

// Estado compartilhado entre o item do menu e o convite (os dois mudam juntos).
let atual = null
const ouvintes = new Set()
const publicar = (e) => { atual = e; ouvintes.forEach((f) => f(e)) }

const CHAVE_CONVITE = 'academia.convitePush'

const TEXTO = {
  equipe: 'Receba um aviso no celular quando um pagamento for confirmado, com o nome do aluno.',
  aluno: 'Receba um aviso no celular quando houver uma nova mensalidade ou cobrança.',
}

/** Estado das notificações neste aparelho e as ações de ativar/desativar. */
export function useNotificacoes(perfil) {
  const [estado, setEstado] = useState(atual)
  const [ocupado, setOcupado] = useState(false)
  const [erro, setErro] = useState(null)

  useEffect(() => {
    ouvintes.add(setEstado)
    if (atual === null) estadoPush().then(publicar)
    return () => { ouvintes.delete(setEstado) }
  }, [])

  // Chamada direto no toque: o pedido de permissão do iPhone só aparece assim.
  function ativar() {
    setErro(null)
    setOcupado(true)
    ativarPush(perfil)
      .then(() => publicar('ativo'))
      .catch(async (e) => { setErro(e.message); publicar(await estadoPush()) })
      .finally(() => setOcupado(false))
  }

  async function desativar() {
    setOcupado(true)
    try { await desativarPush(perfil) } finally { publicar(await estadoPush()); setOcupado(false) }
  }

  return { estado, ocupado, erro, ativar, desativar }
}

/** Passo a passo quando o aparelho ainda não pode ativar (iPhone fora da Tela de Início ou bloqueado). */
function Instrucoes({ estado, onFechar }) {
  return (
    <Modal titulo="Ativar notificações" onFechar={onFechar}>
      {estado === 'instalar' ? (
        <div className="instrucoes-push">
          <p>No iPhone e no iPad, as notificações só funcionam com o sistema <b>adicionado à Tela de Início</b>:</p>
          <ol>
            <li>No Safari, toque em <b>Compartilhar</b> (o quadrado com a seta para cima).</li>
            <li>Escolha <b>Adicionar à Tela de Início</b> e confirme.</li>
            <li>Abra o sistema <b>pelo ícone novo</b>, entre e toque em <b>Ativar notificações</b>.</li>
          </ol>
        </div>
      ) : (
        <div className="instrucoes-push">
          <p>As notificações deste site foram <b>bloqueadas</b> neste aparelho.</p>
          {ehIOS()
            ? <p>Abra <b>Ajustes → Notificações</b>, encontre o app do sistema na lista e ative <b>Permitir Notificações</b>.</p>
            : <p>Toque no <b>cadeado</b> ao lado do endereço do site, em <b>Permissões</b>, e libere <b>Notificações</b>. Depois recarregue a página.</p>}
        </div>
      )}
      <div className="linha" style={{ justifyContent: 'flex-end', marginTop: 18 }}>
        <button className="btn btn-primario" onClick={onFechar}>Entendi</button>
      </div>
    </Modal>
  )
}

/** Item do rodapé do menu. */
export function ItemNotificacoes({ perfil }) {
  const { estado, ocupado, ativar, desativar } = useNotificacoes(perfil)
  const [ajuda, setAjuda] = useState(false)
  if (!estado || estado === 'indisponivel') return null

  const selo = { ativo: 'Ativas', inativo: 'Ativar', instalar: 'Ativar', bloqueado: 'Bloqueadas' }[estado]
  function clicar() {
    if (ocupado) return
    if (estado === 'inativo') ativar()
    else if (estado === 'ativo') {
      if (window.confirm('Parar de receber notificações neste aparelho?')) desativar()
    } else setAjuda(true)
  }

  return (
    <>
      <button type="button" className={`menu-link item-push ${estado}`} onClick={clicar} title="Notificações" aria-busy={ocupado}>
        <Icon nome="sino" /> <span className="rotulo-nav">Notificações</span>
        <span className="rotulo-nav selo-push">{ocupado ? <Spinner /> : selo}</span>
      </button>
      {ajuda && <Instrucoes estado={estado} onFechar={() => setAjuda(false)} />}
    </>
  )
}

/** Convite no topo da página principal (some depois de ativar ou ao fechar). */
export function ConviteNotificacoes({ perfil }) {
  const { estado, ocupado, erro, ativar } = useNotificacoes(perfil)
  const [fechado, setFechado] = useState(() => { try { return localStorage.getItem(CHAVE_CONVITE) === '1' } catch { return false } })
  const [ajuda, setAjuda] = useState(false)
  if (fechado || !['inativo', 'instalar'].includes(estado)) return erro ? <div className="alerta-box erro">{erro}</div> : null

  const fechar = () => { setFechado(true); try { localStorage.setItem(CHAVE_CONVITE, '1') } catch { /* ignora */ } }

  return (
    <div className="convite-push">
      <span className="convite-icone"><Icon nome="sino" /></span>
      <div className="convite-texto">
        <Rotulo>Notificações</Rotulo>
        <p>{TEXTO[perfil === 'ALUNO' ? 'aluno' : 'equipe']}</p>
        {erro && <small className="convite-erro">{erro}</small>}
      </div>
      <div className="convite-acoes">
        <button className="btn btn-primario" disabled={ocupado} onClick={() => (estado === 'instalar' ? setAjuda(true) : ativar())}>
          {ocupado ? <Spinner /> : 'Ativar notificações'}
        </button>
        <button className="btn btn-icone" onClick={fechar} title="Agora não" aria-label="Fechar convite"><Icon nome="fechar" /></button>
      </div>
      {ajuda && <Instrucoes estado={estado} onFechar={() => setAjuda(false)} />}
    </div>
  )
}
