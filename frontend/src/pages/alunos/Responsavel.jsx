import { useState } from 'react'
import { cpfMask, telefoneMask } from '../../lib/format.js'
import { Erro, Modal, Pessoa } from '../../components/ui.jsx'
import Icon from '../../components/Icon.jsx'

export const RESPONSAVEL_VAZIO = {
  id: null, nome: '', cpf: '', telefone: '', documento: '', dataNascimento: '', email: '', sexo: '',
}

/** Dados vindos da API → formato do formulário (máscaras, strings vazias no lugar de null). */
export function responsavelDoServidor(r) {
  if (!r) return null
  const f = { ...RESPONSAVEL_VAZIO }
  for (const k of Object.keys(f)) f[k] = r[k] ?? f[k]
  return { ...f, cpf: cpfMask(f.cpf), telefone: telefoneMask(f.telefone) }
}

/** Formato do formulário → corpo da requisição. */
export function responsavelParaServidor(r) {
  const vazio = (v) => (v && String(v).trim()) || null
  return {
    id: r.id ?? null,
    nome: r.nome.trim(),
    cpf: r.cpf.replace(/\D/g, ''),
    telefone: r.telefone.replace(/\D/g, ''),
    documento: vazio(r.documento),
    dataNascimento: r.dataNascimento || null,
    email: vazio(r.email),
    sexo: r.sexo || null,
  }
}

/**
 * Campo "Responsável": sem responsável, um botão para cadastrar; com responsável, o nome com
 * as opções de editar e remover. O responsável é gravado junto com o aluno, ao salvar.
 */
export function CampoResponsavel({ valor, onChange, onEditar }) {
  if (!valor) {
    return (
      <div>
        <button type="button" id="responsavel" className="btn btn-escuro" onClick={() => onEditar({ ...RESPONSAVEL_VAZIO })}>
          <Icon nome="mais" /> Cadastrar responsável
        </button>
      </div>
    )
  }
  return (
    <div className="responsavel-escolhido">
      <Pessoa nome={valor.nome} sub={[valor.cpf && `CPF ${valor.cpf}`, valor.telefone].filter(Boolean).join(' · ')} />
      <button type="button" className="btn btn-icone" onClick={() => onEditar(valor)} aria-label="Editar responsável" title="Editar">
        <Icon nome="editar" />
      </button>
      <button type="button" className="btn btn-icone" onClick={() => onChange(null)} aria-label="Remover responsável" title="Remover">
        <Icon nome="fechar" />
      </button>
    </div>
  )
}

/** Modal "Novo/Editar responsável" (campos como no cadastro do aluno). */
export function ModalResponsavel({ inicial, onFechar, onSalvar }) {
  const [r, setR] = useState(inicial)
  const [erro, setErro] = useState(null)
  const set = (campo, mascara = (v) => v) => (e) => setR((x) => ({ ...x, [campo]: mascara(e.target.value) }))

  function salvar(e) {
    e.preventDefault()
    if (r.cpf.replace(/\D/g, '').length !== 11) { setErro('CPF do responsável deve ter 11 dígitos.'); return }
    if (r.telefone.replace(/\D/g, '').length < 10) { setErro('Celular do responsável incompleto.'); return }
    onSalvar(r)
  }

  return (
    <Modal titulo={r.id || inicial.nome ? 'Editar responsável' : 'Cadastrar responsável'} onFechar={onFechar}>
      <form onSubmit={salvar} style={{ display: 'grid', gap: 14 }}>
        <Erro erro={erro} />
        <div className="form-grade">
          <div className="campo largo">
            <label htmlFor="r-nome">Nome *</label>
            <input id="r-nome" required maxLength={120} value={r.nome} onChange={set('nome')} autoFocus />
          </div>
          <div className="campo">
            <label htmlFor="r-cpf">CPF *</label>
            <input id="r-cpf" required inputMode="numeric" placeholder="000.000.000-00" value={r.cpf} onChange={set('cpf', cpfMask)} />
          </div>
          <div className="campo">
            <label htmlFor="r-tel">Celular *</label>
            <input id="r-tel" required inputMode="tel" placeholder="(24) 99999-9999" value={r.telefone} onChange={set('telefone', telefoneMask)} />
          </div>
          <div className="campo">
            <label htmlFor="r-doc">Documento (RG)</label>
            <input id="r-doc" maxLength={30} value={r.documento} onChange={set('documento')} />
          </div>
          <div className="campo">
            <label htmlFor="r-nasc">Data de nascimento *</label>
            <input id="r-nasc" type="date" required value={r.dataNascimento} onChange={set('dataNascimento')} />
          </div>
          <div className="campo">
            <label htmlFor="r-email">E-mail *</label>
            <input id="r-email" type="email" required maxLength={160} value={r.email} onChange={set('email')} />
          </div>
          <div className="campo">
            <label htmlFor="r-sexo">Sexo *</label>
            <select id="r-sexo" required value={r.sexo} onChange={set('sexo')}>
              <option value="">Selecione…</option>
              <option value="MASCULINO">Masculino</option>
              <option value="FEMININO">Feminino</option>
            </select>
          </div>
        </div>
        <span className="ajuda muted">O responsável é gravado junto com o aluno, ao clicar em Salvar no cadastro.</span>
        <div className="linha" style={{ justifyContent: 'flex-end' }}>
          <button type="button" className="btn btn-secundario" onClick={onFechar}>Cancelar</button>
          <button className="btn btn-primario">Salvar</button>
        </div>
      </form>
    </Modal>
  )
}
