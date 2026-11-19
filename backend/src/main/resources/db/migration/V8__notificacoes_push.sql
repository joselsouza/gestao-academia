-- Notificações push (Web Push / VAPID).

-- Par de chaves VAPID desta instalação: gerado sozinho na primeira subida (uma linha só).
CREATE TABLE push_chaves (
    id         SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    publica    VARCHAR(120) NOT NULL,   -- ponto P-256 não comprimido, base64url
    privada    VARCHAR(80)  NOT NULL,   -- escalar d de 32 bytes, base64url
    criado_em  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Cada aparelho que ativou as notificações. Pertence a um usuário da equipe OU a um aluno.
CREATE TABLE push_inscricoes (
    id           BIGSERIAL PRIMARY KEY,
    endpoint     TEXT         NOT NULL UNIQUE,
    p256dh       VARCHAR(120) NOT NULL,
    auth         VARCHAR(60)  NOT NULL,
    usuario_id   BIGINT REFERENCES usuarios(id) ON DELETE CASCADE,
    aluno_id     BIGINT REFERENCES alunos(id) ON DELETE CASCADE,
    criado_em    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    ultimo_envio TIMESTAMPTZ,
    CHECK ((usuario_id IS NULL) <> (aluno_id IS NULL))
);
CREATE INDEX idx_push_inscricoes_aluno ON push_inscricoes (aluno_id);

-- Avisos já enviados (evita repetir quando o Asaas manda o mesmo evento mais de uma vez).
CREATE TABLE push_avisos_enviados (
    chave      VARCHAR(120) PRIMARY KEY,
    enviado_em TIMESTAMPTZ  NOT NULL DEFAULT now()
);

ALTER TABLE push_chaves          ENABLE ROW LEVEL SECURITY;
ALTER TABLE push_inscricoes      ENABLE ROW LEVEL SECURITY;
ALTER TABLE push_avisos_enviados ENABLE ROW LEVEL SECURITY;
