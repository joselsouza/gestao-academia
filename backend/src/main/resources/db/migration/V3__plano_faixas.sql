-- Vínculo plano ↔ faixa: ao cadastrar (ou graduar) um aluno numa faixa, o plano da faixa é
-- escolhido automaticamente. Um plano pode cobrir várias faixas (ex.: "FAIXA BRANCA / AZUL"),
-- mas cada faixa pertence a no máximo um plano (a faixa é a chave primária).
CREATE TABLE plano_faixas (
    faixa     VARCHAR(20) PRIMARY KEY CHECK (faixa IN (
        'BRANCA', 'CINZA', 'AMARELA', 'LARANJA', 'VERDE', 'AZUL', 'ROXA', 'MARROM', 'PRETA')),
    plano_id  BIGINT NOT NULL REFERENCES planos(id) ON DELETE CASCADE
);
CREATE INDEX idx_plano_faixas_plano ON plano_faixas (plano_id);
ALTER TABLE plano_faixas ENABLE ROW LEVEL SECURITY;

-- Vincula automaticamente os planos já existentes cujo nome começa com "FAIXA" e cita a faixa
-- (ex.: "FAIXA BRANCA / AZUL" → BRANCA e AZUL; "FAIXA PRETA (PROFESSOR)" → PRETA).
-- Se duas faixas iguais aparecerem em planos diferentes, fica o plano mais antigo.
INSERT INTO plano_faixas (faixa, plano_id)
SELECT DISTINCT ON (f.faixa) f.faixa, p.id
FROM (VALUES ('BRANCA'), ('CINZA'), ('AMARELA'), ('LARANJA'), ('VERDE'),
             ('AZUL'), ('ROXA'), ('MARROM'), ('PRETA')) AS f(faixa)
JOIN planos p
  ON p.ativo
 AND upper(trim(p.nome)) LIKE 'FAIXA%'
 AND upper(p.nome) ~ ('\m' || f.faixa || '\M')
ORDER BY f.faixa, p.id;
