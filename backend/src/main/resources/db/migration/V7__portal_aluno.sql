-- Portal do aluno: o aluno entra com CPF + senha.
-- Sem linha nesta tabela (ou com senha_hash nula), vale a senha padrão da academia
-- (app.portal-aluno.senha-padrao). Ao trocar a senha no portal, o hash fica guardado aqui.
CREATE TABLE aluno_acessos (
    aluno_id           BIGINT PRIMARY KEY REFERENCES alunos(id) ON DELETE CASCADE,
    senha_hash         VARCHAR(100),
    senha_alterada_em  TIMESTAMPTZ,
    ultimo_login       TIMESTAMPTZ
);
ALTER TABLE aluno_acessos ENABLE ROW LEVEL SECURITY;

-- Login do portal procura o aluno pelo CPF.
CREATE INDEX idx_alunos_cpf ON alunos (cpf);
