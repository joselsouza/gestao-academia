-- =====================================================================
-- Gestão de Academia - Sistema de Gestão
-- Schema inicial (PostgreSQL / Supabase)
-- Executado automaticamente pelo Flyway na subida do back-end.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Usuários do sistema (administradores / instrutores)
-- ---------------------------------------------------------------------
CREATE TABLE usuarios (
    id          BIGSERIAL PRIMARY KEY,
    nome        VARCHAR(120) NOT NULL,
    email       VARCHAR(160) NOT NULL UNIQUE,
    senha_hash  VARCHAR(100) NOT NULL,
    perfil      VARCHAR(20)  NOT NULL DEFAULT 'ADMIN' CHECK (perfil IN ('ADMIN', 'INSTRUTOR')),
    ativo       BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_em   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------
-- Planos de mensalidade
--   MENSALIDADE -> aluno pagante
--   BOLSISTA    -> isento de mensalidade
-- ---------------------------------------------------------------------
CREATE TABLE planos (
    id         BIGSERIAL PRIMARY KEY,
    nome       VARCHAR(80)   NOT NULL,
    tipo       VARCHAR(20)   NOT NULL CHECK (tipo IN ('MENSALIDADE', 'BOLSISTA')),
    valor      NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK (valor >= 0),
    descricao  VARCHAR(255),
    ativo      BOOLEAN       NOT NULL DEFAULT TRUE,
    criado_em  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT bolsista_sem_valor CHECK (tipo <> 'BOLSISTA' OR valor = 0)
);

-- ---------------------------------------------------------------------
-- Alunos
-- ---------------------------------------------------------------------
CREATE TABLE alunos (
    id                       BIGSERIAL PRIMARY KEY,
    nome                     VARCHAR(120) NOT NULL,
    telefone                 VARCHAR(20),
    foto                     TEXT,          -- data URL (JPEG redimensionado no front)
    face_descriptor          FLOAT8[],      -- vetor facial (128 dimensões) para o reconhecimento
    consentimento_biometria  BOOLEAN     NOT NULL DEFAULT FALSE, -- LGPD: dado biométrico é dado sensível
    faixa                    VARCHAR(20) NOT NULL DEFAULT 'BRANCA',
    graus                    INTEGER     NOT NULL DEFAULT 0 CHECK (graus BETWEEN 0 AND 6),
    plano_id                 BIGINT REFERENCES planos(id),
    dia_vencimento           INTEGER     NOT NULL DEFAULT 10 CHECK (dia_vencimento BETWEEN 1 AND 28),
    data_inicio              DATE        NOT NULL DEFAULT CURRENT_DATE,
    data_ultima_graduacao    DATE,
    ativo                    BOOLEAN     NOT NULL DEFAULT TRUE,
    criado_em                TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT faixa_valida CHECK (faixa IN (
        'BRANCA', 'CINZA', 'AMARELA', 'LARANJA', 'VERDE',
        'AZUL', 'ROXA', 'MARROM', 'PRETA'))
);
CREATE INDEX idx_alunos_nome  ON alunos (lower(nome));
CREATE INDEX idx_alunos_plano ON alunos (plano_id);

-- ---------------------------------------------------------------------
-- Financeiro: mensalidades (uma por aluno por competência/mês)
-- ---------------------------------------------------------------------
CREATE TABLE mensalidades (
    id               BIGSERIAL PRIMARY KEY,
    aluno_id         BIGINT        NOT NULL REFERENCES alunos(id) ON DELETE CASCADE,
    competencia      DATE          NOT NULL,  -- sempre o dia 1 do mês de referência
    valor            NUMERIC(10,2) NOT NULL CHECK (valor >= 0),
    vencimento       DATE          NOT NULL,
    status           VARCHAR(20)   NOT NULL DEFAULT 'PENDENTE' CHECK (status IN ('PENDENTE', 'PAGO', 'CANCELADO')),
    data_pagamento   DATE,
    forma_pagamento  VARCHAR(20) CHECK (forma_pagamento IN ('PIX', 'DINHEIRO', 'CARTAO', 'TRANSFERENCIA')),
    observacao       VARCHAR(255),
    criado_em        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT mensalidade_unica UNIQUE (aluno_id, competencia)
);
CREATE INDEX idx_mensalidades_competencia ON mensalidades (competencia);
CREATE INDEX idx_mensalidades_status      ON mensalidades (status, vencimento);

-- ---------------------------------------------------------------------
-- Presenças (um registro por aluno por dia de treino)
-- ---------------------------------------------------------------------
CREATE TABLE presencas (
    id          BIGSERIAL PRIMARY KEY,
    aluno_id    BIGINT      NOT NULL REFERENCES alunos(id) ON DELETE CASCADE,
    data        DATE        NOT NULL,
    origem      VARCHAR(30) NOT NULL CHECK (origem IN ('MANUAL', 'RECONHECIMENTO_FACIAL')),
    confianca   NUMERIC(5,4),  -- 0..1, apenas para reconhecimento facial
    criado_em   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT presenca_unica UNIQUE (aluno_id, data)
);
CREATE INDEX idx_presencas_data ON presencas (data);

-- ---------------------------------------------------------------------
-- Graduação: regras (frequência mínima por faixa) e histórico
-- ---------------------------------------------------------------------
CREATE TABLE regras_graduacao (
    id                 BIGSERIAL PRIMARY KEY,
    faixa              VARCHAR(20) NOT NULL UNIQUE,
    presencas_minimas  INTEGER     NOT NULL CHECK (presencas_minimas >= 0),
    meses_minimos      INTEGER     NOT NULL DEFAULT 0 CHECK (meses_minimos >= 0),
    atualizado_em      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE graduacoes (
    id              BIGSERIAL PRIMARY KEY,
    aluno_id        BIGINT      NOT NULL REFERENCES alunos(id) ON DELETE CASCADE,
    faixa_anterior  VARCHAR(20) NOT NULL,
    graus_anterior  INTEGER     NOT NULL,
    faixa_nova      VARCHAR(20) NOT NULL,
    graus_nova      INTEGER     NOT NULL,
    data            DATE        NOT NULL DEFAULT CURRENT_DATE,
    presencas_no_periodo INTEGER NOT NULL DEFAULT 0,
    observacao      VARCHAR(255),
    criado_em       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_graduacoes_aluno ON graduacoes (aluno_id, data DESC);

-- ---------------------------------------------------------------------
-- Keep-alive: ping diário para o projeto Supabase não ser pausado
-- ---------------------------------------------------------------------
CREATE TABLE keep_alive (
    id         BIGSERIAL PRIMARY KEY,
    origem     VARCHAR(40) NOT NULL DEFAULT 'backend',
    pinged_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Função chamável pela API REST do Supabase (chave publishable) por um cron
-- externo (GitHub Actions). Só insere um ping e mantém os 60 mais recentes.
CREATE OR REPLACE FUNCTION public.keep_alive_ping(p_origem TEXT DEFAULT 'rest')
RETURNS TIMESTAMPTZ
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_agora TIMESTAMPTZ := now();
BEGIN
    INSERT INTO keep_alive (origem, pinged_at) VALUES (left(coalesce(p_origem, 'rest'), 40), v_agora);
    DELETE FROM keep_alive WHERE id NOT IN (SELECT id FROM keep_alive ORDER BY pinged_at DESC LIMIT 60);
    RETURN v_agora;
END;
$$;

-- ---------------------------------------------------------------------
-- Segurança: RLS ligado em TODAS as tabelas, sem policies.
-- Assim a chave publishable (anon) NÃO lê nem grava nada via API REST.
-- O back-end Java conecta como dono das tabelas e não é afetado.
-- ---------------------------------------------------------------------
ALTER TABLE usuarios          ENABLE ROW LEVEL SECURITY;
ALTER TABLE planos            ENABLE ROW LEVEL SECURITY;
ALTER TABLE alunos            ENABLE ROW LEVEL SECURITY;
ALTER TABLE mensalidades      ENABLE ROW LEVEL SECURITY;
ALTER TABLE presencas         ENABLE ROW LEVEL SECURITY;
ALTER TABLE regras_graduacao  ENABLE ROW LEVEL SECURITY;
ALTER TABLE graduacoes        ENABLE ROW LEVEL SECURITY;
ALTER TABLE keep_alive        ENABLE ROW LEVEL SECURITY;

-- A única coisa liberada para anon é executar o ping.
REVOKE ALL ON FUNCTION public.keep_alive_ping(TEXT) FROM PUBLIC;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        EXECUTE 'GRANT EXECUTE ON FUNCTION public.keep_alive_ping(TEXT) TO anon, authenticated';
    END IF;
END;
$$;

-- ---------------------------------------------------------------------
-- Dados iniciais
-- ---------------------------------------------------------------------
INSERT INTO planos (nome, tipo, valor, descricao) VALUES
    ('Mensal Adulto', 'MENSALIDADE', 150.00, 'Plano mensal padrão'),
    ('Bolsista',      'BOLSISTA',      0.00, 'Aluno isento de mensalidade');

-- Frequência mínima (nº de treinos desde a última graduação) e tempo mínimo
INSERT INTO regras_graduacao (faixa, presencas_minimas, meses_minimos) VALUES
    ('BRANCA',  40,  4),
    ('CINZA',   40,  4),
    ('AMARELA', 40,  4),
    ('LARANJA', 40,  4),
    ('VERDE',   40,  4),
    ('AZUL',    80,  8),
    ('ROXA',    80,  8),
    ('MARROM',  80,  8),
    ('PRETA',  120, 12);
