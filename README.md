# Gestão de Academia

Sistema web para administrar academias de artes marciais: cadastro de alunos, planos e
mensalidades, cobrança automática, presença por reconhecimento facial e acompanhamento de
graduação por faixa. Funciona no computador, no tablet e no celular.

Esta é uma versão genérica, sem marca, de um sistema que desenvolvi e que está em produção em
uma academia de Jiu-Jitsu. Cada academia é uma instalação separada do mesmo código, com site,
back-end e banco de dados próprios.

## Funcionalidades

| Área | O que tem |
|---|---|
| **Painel** | Resumo da academia: alunos ativos, presenças, mensalidades e inadimplência |
| **Alunos** | Cadastro com foto, documentos, faixa, plano e responsável (alunos menores) |
| **Planos** | Planos de mensalidade, bolsistas e plano automático pela faixa do aluno |
| **Financeiro** | Mensalidades geradas todo mês, inadimplentes e cobranças avulsas via [Asaas](https://www.asaas.com) (Pix, boleto e cartão), com webhook de pagamento |
| **Presença** | Reconhecimento facial no navegador, chamada manual e histórico |
| **Graduação** | Alunos aptos a graduar por frequência e tempo mínimo, regras por faixa e histórico |
| **Portal do aluno** | Login próprio do aluno para ver perfil, mensalidades e pagar |
| **Notificações** | Web Push para avisos de novas cobranças e pagamentos |
| **Usuários** | Perfis de administrador e instrutor |

### Reconhecimento facial com privacidade

A identificação roda no próprio aparelho com [face-api.js](https://github.com/vladmandic/face-api)
(TensorFlow.js), dentro de um Web Worker para a tela não travar. As fotos não saem do aparelho
para identificação: só os vetores numéricos do rosto vão ao servidor, que compara pela distância
euclidiana.

## Tecnologias

- **Back-end:** Java 17, Spring Boot, Spring Security (JWT), Spring Data JPA, Flyway, PostgreSQL
- **Front-end:** React 19, React Router, Vite, face-api.js
- **Integrações:** Asaas (cobranças e webhooks), Web Push (VAPID)

## Estrutura

```
gestao-academia/
├── backend/                 # API Spring Boot
│   └── src/main/
│       ├── java/com/academia/gestao/   # aluno, cobranca, financeiro, graduacao, presenca, portal, push...
│       └── resources/db/migration/     # migrations Flyway (o banco é criado sozinho)
└── frontend/                # site React (Vite)
    ├── academias/           # nome, logo e cores de cada academia (veja academias/README.md)
    ├── public/models/       # modelos do reconhecimento facial
    └── src/
```

## Como rodar

### Back-end

Requisitos: Java 17 e um banco PostgreSQL.

```bash
cd backend
cp .env.example .env     # preencha banco, APP_JWT_SECRET e APP_ADMIN_PASSWORD
./mvnw spring-boot:run   # sobe em http://localhost:8080
```

Na primeira subida o Flyway cria as tabelas e o administrador definido no `.env` é cadastrado.

### Front-end

Requisitos: Node.js 22.

```bash
cd frontend
cp .env.example .env
npm install
npm run dev              # http://localhost:5173
```

## Nova academia

Copie `frontend/academias/exemplo/` para `frontend/academias/<id>/`, troque as imagens e
preencha o `academia.json`. O passo a passo completo (banco, back-end, site e Asaas) está em
[frontend/academias/README.md](frontend/academias/README.md).

---

Feito por [José Neto](https://github.com/gagoraro).
