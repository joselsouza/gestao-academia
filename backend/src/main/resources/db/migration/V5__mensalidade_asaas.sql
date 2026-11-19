-- Mensalidade ligada ao Asaas: cada mensalidade pode ter um link de pagamento (pagamento avulso
-- no Asaas). Quando o Asaas confirma o pagamento (webhook), a mensalidade vira PAGO sozinha.
ALTER TABLE mensalidades
    ADD COLUMN asaas_pagamento_id VARCHAR(40) UNIQUE,
    ADD COLUMN link_pagamento     VARCHAR(255);

-- Pagamento recebido por boleto (via Asaas)
ALTER TABLE mensalidades DROP CONSTRAINT IF EXISTS mensalidades_forma_pagamento_check;
ALTER TABLE mensalidades ADD CONSTRAINT mensalidades_forma_pagamento_check
    CHECK (forma_pagamento IN ('PIX', 'DINHEIRO', 'CARTAO', 'TRANSFERENCIA', 'BOLETO'));

-- Vencimento único da academia: todo dia 10 (vale para as próximas mensalidades geradas).
UPDATE alunos SET dia_vencimento = 10;
