package com.academia.gestao.aluno;

import com.academia.gestao.aluno.AlunoDtos.AlunoRequest;
import com.academia.gestao.aluno.AlunoDtos.AlunoResponse;
import com.academia.gestao.aluno.AlunoDtos.ResponsavelRequest;
import com.academia.gestao.common.NaoEncontradoException;
import com.academia.gestao.common.NegocioException;
import com.academia.gestao.plano.PlanoRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AlunoService {

    /** Tamanho do vetor gerado pelo face-api (FaceRecognitionNet). */
    public static final int DIMENSAO_DESCRITOR = 128;
    private static final int TAMANHO_MAX_FOTO = 1_500_000; // ~1,1 MB em base64

    private final AlunoRepository alunos;
    private final AlunoMidiaRepository midias;
    private final PlanoRepository planos;
    private final ResponsavelRepository responsaveis;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<AlunoResponse> listar(String busca, Boolean ativo) {
        String termo = busca == null ? "" : busca.trim().toLowerCase();
        return alunos.findAllComPlano().stream()
                .filter(a -> ativo == null || a.isAtivo() == ativo)
                .filter(a -> termo.isEmpty() || a.getNome().toLowerCase().contains(termo)
                        || (a.getTelefone() != null && a.getTelefone().contains(termo)))
                .map(AlunoResponse::resumo)
                .toList();
    }

    // Os métodos abaixo devolvem o DTO ainda dentro da transação (open-in-view está desligado).

    @Transactional(readOnly = true)
    public AlunoResponse buscar(Long id) {
        Aluno a = alunos.findById(id).orElseThrow(() -> new NaoEncontradoException("Aluno", id));
        AlunoMidia m = midia(id);
        return AlunoResponse.completo(a, m.getFoto(), reconhecivel(a, m));
    }

    @Transactional
    public AlunoResponse criar(AlunoRequest req) {
        Aluno a = new Aluno();
        a.setDataInicio(LocalDate.now(clock));
        aplicarDados(a, req);
        alunos.saveAndFlush(a); // cria a linha; foto e vetor entram em seguida via AlunoMidia
        AlunoMidia m = midia(a.getId());
        aplicarMidia(a, m, req);
        return AlunoResponse.completo(a, m.getFoto(), reconhecivel(a, m));
    }

    @Transactional
    public AlunoResponse atualizar(Long id, AlunoRequest req) {
        Aluno a = alunos.findById(id).orElseThrow(() -> new NaoEncontradoException("Aluno", id));
        aplicarDados(a, req);
        AlunoMidia m = midia(id);
        aplicarMidia(a, m, req);
        return AlunoResponse.completo(a, m.getFoto(), reconhecivel(a, m));
    }

    /** "Excluir" = desativar: mantém histórico de presença, graduação e financeiro. */
    @Transactional
    public void desativar(Long id) {
        Aluno a = alunos.findById(id).orElseThrow(() -> new NaoEncontradoException("Aluno", id));
        a.setAtivo(false);
    }

    private AlunoMidia midia(Long id) {
        return midias.findById(id).orElseThrow(() -> new NaoEncontradoException("Aluno", id));
    }

    private static boolean reconhecivel(Aluno a, AlunoMidia m) {
        return a.isAtivo() && a.isConsentimentoBiometria() && m.getFaceDescriptor() != null;
    }

    private void aplicarDados(Aluno a, AlunoRequest req) {
        if (req.graus() > req.faixa().grausMaximos()) {
            throw new NegocioException("A faixa " + req.faixa() + " permite no máximo "
                    + req.faixa().grausMaximos() + " graus.");
        }
        if (req.foto() != null && req.foto().length() > TAMANHO_MAX_FOTO) {
            throw new NegocioException("Foto muito grande. Envie uma imagem menor.");
        }
        if (req.faceDescriptor() != null && req.faceDescriptor().length != DIMENSAO_DESCRITOR) {
            throw new NegocioException("Vetor facial inválido (esperado " + DIMENSAO_DESCRITOR + " posições).");
        }
        String cpf = Documentos.cpfOuNull(req.cpf());
        boolean vaiFicarAtivo = req.ativo() != null ? req.ativo() : a.isAtivo();
        // O CPF é o login do portal do aluno: dois alunos ativos não podem dividir o mesmo.
        if (cpf != null && vaiFicarAtivo && (!cpf.equals(a.getCpf()) || !a.isAtivo())) {
            alunos.findAtivosComCpf(cpf, a.getId()).stream().findFirst().ifPresent(outro -> {
                throw new NegocioException("O CPF informado já está no cadastro de " + outro.getNome() + ".");
            });
        }
        a.setNome(req.nome().trim());
        a.setTelefone(req.telefone() == null ? null : req.telefone().trim());
        a.setCpf(cpf);
        a.setEmail(emailOuNull(req.email()));
        a.setDataNascimento(req.dataNascimento());
        a.setSexo(req.sexo());
        a.setFaixa(req.faixa());
        a.setGraus(req.graus());
        a.setDataUltimaGraduacao(req.dataUltimaGraduacao());
        a.setResponsavel(req.responsavel() == null ? null : salvarResponsavel(req.responsavel()));
        a.setPlano(req.planoId() == null ? null
                : planos.findById(req.planoId()).orElseThrow(() -> new NaoEncontradoException("Plano", req.planoId())));
        if (req.diaVencimento() != null) {
            a.setDiaVencimento(req.diaVencimento());
        }
        if (req.dataInicio() != null) {
            a.setDataInicio(req.dataInicio());
        }
        if (req.ativo() != null) {
            a.setAtivo(req.ativo());
        }
        a.setConsentimentoBiometria(req.consentimentoBiometria());
    }

    /**
     * Com id: atualiza aquele responsável. Sem id: reaproveita o responsável que já tem o mesmo CPF
     * (ex.: irmãos cadastrados separadamente) ou cria um novo.
     */
    private Responsavel salvarResponsavel(ResponsavelRequest req) {
        String cpf = Documentos.cpfOuNull(req.cpf());
        if (cpf == null || cpf.length() != 11) {
            throw new NegocioException("CPF do responsável deve ter 11 dígitos.");
        }
        Responsavel r = req.id() != null
                ? responsaveis.findById(req.id()).orElseThrow(() -> new NaoEncontradoException("Responsável", req.id()))
                : responsaveis.findFirstByCpf(cpf).orElseGet(Responsavel::new);
        r.setNome(req.nome().trim());
        r.setCpf(cpf);
        r.setTelefone(req.telefone().trim());
        r.setDocumento(textoOuNull(req.documento()));
        r.setDataNascimento(req.dataNascimento());
        r.setEmail(emailOuNull(req.email()));
        r.setSexo(req.sexo());
        return responsaveis.save(r);
    }

    private static String textoOuNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static String emailOuNull(String v) {
        return v == null || v.isBlank() ? null : v.trim().toLowerCase();
    }

    private void aplicarMidia(Aluno a, AlunoMidia m, AlunoRequest req) {
        String foto = req.foto() == null || req.foto().isBlank() ? null : req.foto();
        if (!Objects.equals(foto, m.getFoto()) || (foto != null && a.getFotoMiniatura() == null)) {
            m.setFoto(foto);
            a.setFotoMiniatura(foto == null ? null : Miniaturas.gerar(foto));
        }
        // LGPD: o vetor facial (dado biométrico) só é guardado com consentimento e com foto.
        if (!req.consentimentoBiometria() || foto == null) {
            m.setFaceDescriptor(null);
        } else if (req.faceDescriptor() != null) {
            m.setFaceDescriptor(req.faceDescriptor());
        }
    }
}
