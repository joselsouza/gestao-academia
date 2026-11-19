-- Miniatura da foto (~96px, poucos KB) usada nas listas, no lugar da foto completa.
-- As miniaturas dos alunos já cadastrados são geradas pelo back-end na subida (MiniaturaBackfill).
ALTER TABLE alunos ADD COLUMN foto_miniatura TEXT;
