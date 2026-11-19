package com.academia.gestao.aluno;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public final class AlunoDtos {

    private AlunoDtos() {}

    public record AlunoRequest(
            @NotBlank @Size(max = 120) String nome,
            @Pattern(regexp = "^[0-9()+\\-\\s]{0,20}$", message = "telefone inválido") String telefone,
            @Pattern(regexp = "^[0-9.\\-/\\s]{0,20}$", message = "CPF inválido") String cpf,
            @Email @Size(max = 160) String email,
            @NotNull(message = "informe a data de nascimento") @Past(message = "data de nascimento inválida") LocalDate dataNascimento,
            @NotNull(message = "informe o sexo") Sexo sexo,
            String foto,
            double[] faceDescriptor,
            boolean consentimentoBiometria,
            @NotNull Faixa faixa,
            @Min(0) @Max(6) int graus,
            @PastOrPresent(message = "data da graduação não pode ser no futuro") LocalDate dataUltimaGraduacao,
            Long planoId,
            @Min(1) @Max(28) Integer diaVencimento,
            LocalDate dataInicio,
            Boolean ativo,
            /** null = aluno sem responsável. Com id = atualiza esse responsável; sem id = cria um novo. */
            @Valid ResponsavelRequest responsavel) {}

    public record ResponsavelRequest(
            Long id,
            @NotBlank(message = "informe o nome do responsável") @Size(max = 120) String nome,
            @NotBlank(message = "informe o CPF do responsável") @Pattern(regexp = "^[0-9.\\-\\s]{11,14}$", message = "CPF do responsável inválido") String cpf,
            @NotBlank(message = "informe o celular do responsável") @Pattern(regexp = "^[0-9()+\\-\\s]{0,20}$", message = "celular do responsável inválido") String telefone,
            @Size(max = 30) String documento,
            @Past(message = "data de nascimento do responsável inválida") LocalDate dataNascimento,
            @Email(message = "e-mail do responsável inválido") @Size(max = 160) String email,
            Sexo sexo) {}

    public record ResponsavelResponse(
            Long id, String nome, String cpf, String telefone, String documento, LocalDate dataNascimento,
            String email, Sexo sexo) {

        public static ResponsavelResponse of(Responsavel r) {
            if (r == null) {
                return null;
            }
            return new ResponsavelResponse(r.getId(), r.getNome(), r.getCpf(), r.getTelefone(), r.getDocumento(),
                    r.getDataNascimento(), r.getEmail(), r.getSexo());
        }
    }

    public record AlunoResponse(
            Long id,
            String nome,
            String telefone,
            String cpf,
            String email,
            LocalDate dataNascimento,
            Sexo sexo,
            String foto,
            Faixa faixa,
            int graus,
            Long planoId,
            String planoNome,
            boolean bolsista,
            int diaVencimento,
            LocalDate dataInicio,
            LocalDate dataUltimaGraduacao,
            boolean ativo,
            boolean consentimentoBiometria,
            boolean reconhecimentoFacialAtivo,
            /** Com responsável, as cobranças do Asaas saem no nome e CPF dele. */
            ResponsavelResponse responsavel) {

        /** Para listas: "foto" é a miniatura. */
        public static AlunoResponse resumo(Aluno a) {
            return montar(a, a.getFotoMiniatura(), a.isReconhecivel());
        }

        /** Para a ficha do aluno: "foto" é a foto completa. */
        public static AlunoResponse completo(Aluno a, String foto, boolean reconhecivel) {
            return montar(a, foto, reconhecivel);
        }

        private static AlunoResponse montar(Aluno a, String foto, boolean reconhecivel) {
            return new AlunoResponse(a.getId(), a.getNome(), a.getTelefone(), a.getCpf(), a.getEmail(),
                    a.getDataNascimento(), a.getSexo(), foto, a.getFaixa(), a.getGraus(),
                    a.getPlano() != null ? a.getPlano().getId() : null,
                    a.getPlano() != null ? a.getPlano().getNome() : null,
                    a.isBolsista(), a.getDiaVencimento(), a.getDataInicio(), a.getDataUltimaGraduacao(),
                    a.isAtivo(), a.isConsentimentoBiometria(), reconhecivel,
                    ResponsavelResponse.of(a.getResponsavel()));
        }
    }
}
