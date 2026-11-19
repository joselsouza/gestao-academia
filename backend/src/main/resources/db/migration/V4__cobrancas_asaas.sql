-- Cobranças pelo Asaas (PIX / cartão / boleto), únicas ou recorrentes (assinaturas).
-- O Asaas exige CPF do cliente; o e-mail é opcional. O id do cliente no Asaas fica no aluno.
ALTER TABLE alunos
    ADD COLUMN cpf               VARCHAR(14),
    ADD COLUMN email             VARCHAR(160),
    ADD COLUMN asaas_customer_id VARCHAR(40);

CREATE TABLE cobrancas (
    id                  BIGSERIAL PRIMARY KEY,
    aluno_id            BIGINT        NOT NULL REFERENCES alunos(id) ON DELETE CASCADE,
    tipo                VARCHAR(20)   NOT NULL CHECK (tipo IN ('UNICA', 'RECORRENTE')),
    forma               VARCHAR(20)   NOT NULL CHECK (forma IN ('INDEFINIDA', 'PIX', 'CARTAO', 'BOLETO')),
    ciclo               VARCHAR(20)   CHECK (ciclo IN ('MENSAL', 'TRIMESTRAL', 'SEMESTRAL', 'ANUAL')),
    descricao           VARCHAR(255),
    valor               NUMERIC(10,2) NOT NULL CHECK (valor > 0),
    -- UNICA: id do pagamento (pay_...). RECORRENTE: id da assinatura (sub_...).
    asaas_id            VARCHAR(40)   NOT NULL UNIQUE,
    -- Parcela "da vez": a próxima em aberto ou, se não houver, a última.
    asaas_pagamento_id  VARCHAR(40),
    vencimento          DATE,
    proximo_vencimento  DATE,
    data_fim            DATE,
    status              VARCHAR(20)   NOT NULL DEFAULT 'PENDENTE'
                        CHECK (status IN ('PENDENTE', 'VENCIDA', 'PAGA', 'ESTORNADA', 'ENCERRADA')),
    link_pagamento      VARCHAR(255),
    data_pagamento      DATE,
    atualizado_em       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    criado_em           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT recorrente_com_ciclo CHECK (tipo <> 'RECORRENTE' OR ciclo IS NOT NULL)
);
CREATE INDEX idx_cobrancas_aluno      ON cobrancas (aluno_id);
CREATE INDEX idx_cobrancas_vencimento ON cobrancas (status, vencimento);
ALTER TABLE cobrancas ENABLE ROW LEVEL SECURITY;
