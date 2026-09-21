# Guia de Testes no Insomnia - Ticket Service & Mensageria RabbitMQ

Este guia orienta a execução da suíte de testes completa do microserviço **`helpdesk-ticket-service`**, incluindo o novo atributo **`ticketEnabled`**, a operação de **exclusão lógica (soft delete)**, os filtros de listagem (**`ativados`**, **`desativados`** e **`todos`**) e a integração assíncrona com o **RabbitMQ** ([`insomnia-ticket-collection.json`](file:///c:/WS-JAVA/helpdesk-application/helpdesk-ticket-service/insomnia-ticket-collection.json)).

---

## 1. Pré-requisitos do Ambiente

Para executar os testes com persistência e mensageria ativas, certifique-se de que os containers Docker necessários estão em execução:

### 1.1 Banco de Dados PostgreSQL (`ticket_db`)
Caso ainda não tenha criado a base `ticket_db` no container do Postgres:
```bash
docker exec -it helpdesk-postgres psql -U postgres -c "CREATE DATABASE ticket_db;"
```

### 1.2 Broker RabbitMQ com Management UI
Inicie o RabbitMQ com a interface de gerenciamento ativa:
```bash
docker run -d --name helpdesk-rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:3.13-management
```
- **Porta AMQP (Mensageria)**: `5672`
- **Interface / API de Gerenciamento**: `http://localhost:15672` (usuário: `guest` / senha: `guest`)

### 1.3 Inicializar os Microserviços
- **User Service (Porta 8081)**:
  ```bash
  cd c:\WS-JAVA\helpdesk-application\helpdesk-user-service
  .\mvnw.cmd spring-boot:run
  ```
- **Ticket Service (Porta 8082)**:
  ```bash
  cd c:\WS-JAVA\helpdesk-application\helpdesk-ticket-service
  .\mvnw.cmd spring-boot:run
  ```

---

## 2. Como Importar a Coleção no Insomnia

1. Abra o **Insomnia**.
2. Clique em **Create** > **Import** (ou vá em **Preferences** > **Data** > **Import Data** > **From File**).
3. Selecione o arquivo:
   ```text
   c:\WS-JAVA\helpdesk-application\helpdesk-ticket-service\insomnia-ticket-collection.json
   ```
4. A coleção **`Helpdesk - Ticket & Messaging Service`** será criada contendo 9 pastas organizadas.

---

## 3. Automação Inteligente (Response Chaining)

A coleção utiliza **Insomnia Template Tags (`Response => Body Attribute`)**:
1. **Tokens JWT**: Ao executar as requisições da pasta `00 - Obtenção de Tokens`, os tokens de **ADMIN**, **TECHNICIAN** e **CLIENT** são capturados automaticamente para todas as chamadas autorizadas.
2. **ID do Chamado (`ticketId`)**: Ao executar a requisição `1.1 - Criar Chamado como CLIENT`, o `id` gerado pelo banco de dados é capturado dinamicamente para as consultas, atualizações (`PUT`), atribuição (`PATCH /assign`), alteração de status (`PATCH /status`) e exclusão lógica (`DELETE`).

---

## 4. Matriz dos Casos de Teste

### Pasta 00: Obtenção de Tokens JWT (`helpdesk-user-service`)

| # | Requisição | Método | Endpoint | Status | Descrição |
|---|---|:---:|---|:---:|---|
| **0.1** | **Obter Token ADMIN** | `POST` | `http://localhost:8081/auth/login` | `200 OK` | Autentica com `admin@helpdesk.com` / `admin123`. |
| **0.2** | **Obter Token TECHNICIAN** | `POST` | `http://localhost:8081/auth/login` | `200 OK` | Autentica com `tatiana.tecnica@helpdesk.com` / `password123`. |
| **0.3** | **Obter Token CLIENT** | `POST` | `http://localhost:8081/auth/login` | `200 OK` | Autentica com `carlos.cliente@helpdesk.com` / `password123`. |

---

### Pasta 01: Criação de Chamados (`POST /tickets`)

> **Regra**: O chamado é criado com `ticketEnabled = true` e status inicial `OPEN`.
> **Evento RabbitMQ**: Dispara `TicketCreatedEvent` na Exchange **`ticket.events`** com a routing key **`ticket.created`**.

| # | Requisição | Método | Endpoint | Status | Validação / Mensageria |
|---|---|:---:|---|:---:|---|
| **1.1** | **Criar Chamado como CLIENT** | `POST` | `/tickets` | `201 Created` | Cria chamado com `ticketEnabled: true`. Publica evento `ticket.created` no RabbitMQ. |
| **1.2** | **Criar Chamado como ADMIN** | `POST` | `/tickets` | `201 Created` | Valida permissão do perfil Administrador para abertura de chamados. |
| **1.3** | **Criar Chamado como TECHNICIAN** | `POST` | `/tickets` | `403 Forbidden` | Bloqueia tentativa de criação por técnico (apenas CLIENT ou ADMIN). |
| **1.4** | **Criar Chamado sem Autenticação** | `POST` | `/tickets` | `403 Forbidden` | Bloqueio de requisição anônima pelo Spring Security. |
| **1.5** | **Criar com Dados Inválidos** | `POST` | `/tickets` | `400 Bad Request` | Valida constraints Bean Validation. |

---

### Pasta 02: Consulta e Listagem (`GET /tickets`)

> **Filtros de Situação (`enabledFilter`)**:
> - **Padrão**: `ativados` (quando o parâmetro não for informado).
> - **`ativados`**: Retorna apenas chamados com `ticketEnabled = true`.
> - **`desativados`**: Retorna apenas chamados com exclusão lógica (`ticketEnabled = false`).
> - **`todos`**: Retorna todos os chamados, independentemente do status de ativação.

| # | Requisição | Método | Endpoint | Status | Validação |
|---|---|:---:|---|:---:|---|
| **2.1** | **Listar Chamados (Padrão: ativados)** | `GET` | `/tickets?page=0&size=10&sort=createdAt,desc` | `200 OK` | Retorna página filtrando apenas os chamados ativados. |
| **2.2** | **Listar Explicitamente Ativados** | `GET` | `/tickets?enabledFilter=ativados` | `200 OK` | Filtra por chamados ativos (`ticketEnabled: true`). |
| **2.3** | **Listar Desativados** | `GET` | `/tickets?enabledFilter=desativados` | `200 OK` | Filtra chamados desativados logicamente (`ticketEnabled: false`). |
| **2.4** | **Listar Todos** | `GET` | `/tickets?enabledFilter=todos` | `200 OK` | Retorna todos os chamados (sem filtro de exclusão lógica). |
| **2.5** | **Filtrar por Status (OPEN)** | `GET` | `/tickets?status=OPEN` | `200 OK` | Filtra pela Specification de status. |
| **2.6** | **Filtrar por Prioridade (HIGH)** | `GET` | `/tickets?priority=HIGH` | `200 OK` | Filtra por prioridade (`LOW`, `MEDIUM`, `HIGH`, `CRITICAL`). |
| **2.7** | **Filtrar por Categoria (NETWORK)** | `GET` | `/tickets?category=NETWORK` | `200 OK` | Filtra por categoria (`HARDWARE`, `SOFTWARE`, `NETWORK`). |
| **2.8** | **Filtrar por Cliente (`customerId`)** | `GET` | `/tickets?customerId=2` | `200 OK` | Filtra chamados pertencentes a um cliente. |
| **2.9** | **Filtrar por Técnico (`technicianId`)** | `GET` | `/tickets?technicianId=3` | `200 OK` | Filtra chamados atribuídos a um técnico. |
| **2.10** | **Buscar Chamado por ID** | `GET` | `/tickets/{id}` | `200 OK` | Exibe dados completos incluindo o booleano `ticketEnabled`. |
| **2.11** | **Buscar ID Inexistente** | `GET` | `/tickets/99999` | `404 Not Found` | Valida `ResourceNotFoundException`. |
| **2.12** | **Listar por Cliente (Rota Direta)** | `GET` | `/tickets/customer/2` | `200 OK` | Retorna lista de chamados de um cliente específico. |

---

### Pasta 03: Atribuição de Técnico (`PATCH /tickets/{id}/assign`)

> **Eventos RabbitMQ**: 
> 1. Dispara `TicketAssignedEvent` com routing key **`ticket.assigned`**.
> 2. Se o chamado estava em `OPEN`, transita automaticamente para `IN_PROGRESS` e dispara também `TicketStatusChangedEvent` com routing key **`ticket.status-changed`**!

| # | Requisição | Método | Endpoint | Status | Validação / Mensageria |
|---|---|:---:|---|:---:|---|
| **3.1** | **Atribuir como TECHNICIAN** | `PATCH` | `/tickets/{id}/assign` | `200 OK` | Vincula técnico (`technicianId: 3`), muda status para `IN_PROGRESS` e publica eventos `ticket.assigned` e `ticket.status-changed`. |
| **3.2** | **Atribuir como ADMIN** | `PATCH` | `/tickets/{id}/assign` | `200 OK` | Valida permissão do Administrador para delegar chamado. |
| **3.3** | **Atribuir como CLIENT** | `PATCH` | `/tickets/{id}/assign` | `403 Forbidden` | Bloqueia cliente de atribuir técnicos. |
| **3.4** | **Atribuir em ID Inexistente** | `PATCH` | `/tickets/99999/assign` | `404 Not Found` | Validação de recurso inexistente. |
| **3.5** | **Payload com Técnico Nulo** | `PATCH` | `/tickets/{id}/assign` | `400 Bad Request` | Valida obrigatoriedade do `technicianId`. |

---

### Pasta 04: Atualização Cadastral (`PUT /tickets/{id}`)

| # | Requisição | Método | Endpoint | Status | Validação |
|---|---|:---:|---|:---:|---|
| **4.1** | **Atualizar Dados do Chamado** | `PUT` | `/tickets/{id}` | `200 OK` | Atualiza título, descrição, categoria e prioridade. |
| **4.2** | **Atualizar com Dados Inválidos** | `PUT` | `/tickets/{id}` | `400 Bad Request` | Valida `@Size` e campos obrigatórios. |
| **4.3** | **Atualizar Chamado Inexistente** | `PUT` | `/tickets/99999` | `404 Not Found` | Valida ID inexistente. |

---

### Pasta 05: Mudança de Status (`PATCH /tickets/{id}/status`)

> **Evento RabbitMQ**: Dispara `TicketStatusChangedEvent` na Exchange **`ticket.events`** com routing key **`ticket.status-changed`** contendo `oldStatus` e `newStatus`.

| # | Requisição | Método | Endpoint | Status | Validação / Mensageria |
|---|---|:---:|---|:---:|---|
| **5.1** | **Mudar para WAITING** | `PATCH` | `/tickets/{id}/status` | `200 OK` | Transita status para `WAITING` e publica evento `ticket.status-changed`. |
| **5.2** | **Mudar para RESOLVED** | `PATCH` | `/tickets/{id}/status` | `200 OK` | Transita status para `RESOLVED` e publica evento `ticket.status-changed`. |
| **5.3** | **Mudar Status como CLIENT** | `PATCH` | `/tickets/{id}/status` | `403 Forbidden` | Clientes não têm permissão para mudar status via endpoint. |
| **5.4** | **Status Nulo** | `PATCH` | `/tickets/{id}/status` | `400 Bad Request` | Validação Bean Validation. |

---

### Pasta 06: Exclusão Lógica e Desativação (`DELETE /tickets/{id}`)

> **Nova Regra de Delete**: A exclusão lógica define **`ticketEnabled = false`** **sem alterar o status atual do chamado**!

| # | Requisição | Método | Endpoint | Status | Validação / Regra |
|---|---|:---:|---|:---:|---|
| **6.1** | **Exclusão Lógica do Chamado** | `DELETE` | `/tickets/{id}` | `204 No Content` | Desativa o chamado (`ticketEnabled: false`) mantendo o status atual. |
| **6.2** | **Verificar Status Mantido** | `GET` | `/tickets/{id}` | `200 OK` | Confirma `ticketEnabled: false` e status original preservado. |
| **6.3** | **Verificar Exclusão da Lista Padrão** | `GET` | `/tickets?enabledFilter=ativados` | `200 OK` | Confirma que o chamado desativado NÃO aparece na lista padrão. |
| **6.4** | **Verificar Presença em Desativados** | `GET` | `/tickets?enabledFilter=desativados` | `200 OK` | Confirma que o chamado desativado APARECE ao filtrar por desativados. |
| **6.5** | **Verificar Presença em Todos** | `GET` | `/tickets?enabledFilter=todos` | `200 OK` | Confirma que o chamado APARECE na listagem de todos os registros. |
| **6.6** | **Excluir ID Inexistente** | `DELETE` | `/tickets/99999` | `404 Not Found` | Validação de ID inexistente. |

---

### Pasta 07: Monitoramento da Mensageria (RabbitMQ Management API)

| # | Requisição | Método | Endpoint | Status | Objetivo |
|---|---|:---:|---|:---:|---|
| **7.1** | **Exchange 'ticket.events'** | `GET` | `http://localhost:15672/api/exchanges/%2f/ticket.events` | `200 OK` | Inspeciona a Topic Exchange (`durable: true`). |
| **7.2** | **Bindings da Exchange** | `GET` | `http://localhost:15672/api/exchanges/%2f/ticket.events/bindings/source` | `200 OK` | Lista as rotas/bindings ativas conectadas à exchange. |
| **7.3** | **Listar Filas Ativas** | `GET` | `http://localhost:15672/api/queues` | `200 OK` | Monitora filas consumidoras e contagem de mensagens. |
| **7.4** | **Visão Geral do Cluster** | `GET` | `http://localhost:15672/api/overview` | `200 OK` | Taxas de mensagens e saúde do broker. |

---

### Pasta 08: Documentação e OpenAPI

| # | Requisição | Método | Endpoint | Status |
|---|---|:---:|---|:---:|
| **8.1** | **OpenAPI JSON Spec** | `GET` | `/v3/api-docs` | `200 OK` |
| **8.2** | **Swagger UI HTML** | `GET` | `/swagger-ui/index.html` | `200 OK` |

---

## 5. Ordem Recomendada de Execução no Insomnia

1. Execute **`0.1 - Obter Token ADMIN`**, **`0.2 - Obter Token TECHNICIAN`** e **`0.3 - Obter Token CLIENT`**.
2. Execute **`1.1 - Criar Chamado como CLIENT`** (o `ticketId` gerado será propagado automaticamente).
3. Execute **`3.1 - Atribuir Técnico como TECHNICIAN`** (o status mudará para `IN_PROGRESS`).
4. Execute **`4.1 - Atualizar Dados do Chamado`**.
5. Execute **`5.1 - Alterar Status para WAITING`** e depois **`5.2 - Alterar Status para RESOLVED`**.
6. Execute a exclusão lógica **`6.1 - Exclusão Lógica do Chamado`** (`DELETE /tickets/{id}`).
7. Execute **`6.2 - Verificar Chamado Desativado com Status Mantido`** para confirmar que `ticketEnabled = false` e o status continua como `RESOLVED`.
8. Execute **`6.3`**, **`6.4`** e **`6.5`** para verificar o comportamento dos filtros `ativados`, `desativados` e `todos`.

