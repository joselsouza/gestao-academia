-- Dados pessoais do aluno e responsável (obrigatório informar para menores de 18 anos).

-- Responsável legal. Um mesmo responsável pode estar ligado a vários alunos (irmãos).
CREATE TABLE responsaveis (
    id               BIGSERIAL PRIMARY KEY,
    nome             VARCHAR(120) NOT NULL,
    cpf              VARCHAR(14)  NOT NULL,   -- só dígitos
    telefone         VARCHAR(20)  NOT NULL,
    documento        VARCHAR(30),             -- RG ou outro documento
    data_nascimento  DATE,
    email            VARCHAR(160),
    sexo             VARCHAR(10) CHECK (sexo IN ('MASCULINO', 'FEMININO')),
    cep              VARCHAR(8),
    logradouro       VARCHAR(160),
    numero           VARCHAR(20),
    complemento      VARCHAR(80),
    bairro           VARCHAR(80),
    cidade           VARCHAR(80),
    -- Cliente no Asaas (cus_...): quando o aluno tem responsável, a cobrança sai no nome e CPF dele.
    asaas_customer_id VARCHAR(40),
    criado_em        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_responsaveis_nome ON responsaveis (lower(nome));
CREATE INDEX idx_responsaveis_cpf  ON responsaveis (cpf);

-- Nulos para os alunos já cadastrados: o formulário passa a pedir na próxima edição.
ALTER TABLE alunos
    ADD COLUMN data_nascimento DATE,
    ADD COLUMN sexo            VARCHAR(10) CHECK (sexo IN ('MASCULINO', 'FEMININO')),
    ADD COLUMN responsavel_id  BIGINT REFERENCES responsaveis(id) ON DELETE SET NULL;
CREATE INDEX idx_alunos_responsavel ON alunos (responsavel_id);
