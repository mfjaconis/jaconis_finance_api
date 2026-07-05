# Etapa 4 — Autenticação: register, login, Argon2 e JWT

| Item | Detalhe |
|------|---------|
| **Objetivo** | Endpoints públicos de registro e login; rotas protegidas exigem JWT; senha com Argon2; seed de dados iniciais no registro |
| **Pré-requisitos** | Etapas 0–3 concluídas |
| **Fonte da verdade** | [`doc/domain-and-api.yaml`](./domain-and-api.yaml) e [`doc/tasks.md`](./tasks.md) (seção Etapa 4) |
| **Decisão de segurança** | A spec cita BCrypt; **neste projeto usamos Argon2** via `Argon2PasswordEncoder` do Spring Security |

---

## Estado atual do repositório

Itens **já prontos** antes de iniciar esta etapa:

- [x] Entidade `User` + `UserRepository` + migration `V1__users.sql`
- [x] DTOs: `RegisterRequest`, `LoginRequest`, `UserResponse`, `TokenResponse`
- [x] `UserMapper` (conversão entity ↔ response)
- [x] Tratamento global de erros (`ApiExceptionHandler`, incluindo `InvalidCredentialsException` → 401)
- [x] Dependências JJWT no `pom.xml` (`jjwt-api`, `jjwt-impl`, `jjwt-jackson` 0.12.6)
- [x] Propriedades JWT em `application.properties` (`app.jwt.secret`, `app.jwt.expiration-ms`)
- [x] Migration `V2__categories_and_financial_responsibles.sql` (tabelas `categories` e `financial_responsible`)

Itens **a implementar** nesta etapa:

- [ ] Entidades mínimas `Category` e `FinancialResponsible` + repositories
- [ ] `AuthService` (register, login, seed)
- [ ] `JwtService` (gerar e validar token)
- [ ] `JwtAuthenticationFilter` + `SecurityConfig`
- [ ] `AuthController` (`POST /api/v1/auth/register`, `POST /api/v1/auth/login`)
- [ ] `SecurityUtils.getCurrentUserId()`

---

## Conceitos para aprender

| Conceito | Por que importa |
|----------|-----------------|
| **Spring Security Filter Chain** | Intercepta requests antes do controller; é onde o JWT é validado |
| **Sessão stateless** | API REST não guarda sessão no servidor; cada request traz o token |
| **JWT** (header.payload.signature) | Token auto-contido com `subject` = userId e expiração |
| **PasswordEncoder / Argon2** | Hash irreversível da senha; nunca persistir texto plano |
| **SecurityContext** | Onde o Spring guarda o usuário autenticado após o filtro JWT |

---

## Tarefas (checklist)

### 1. Dependências e configuração

- [x] JJWT já adicionado ao `pom.xml`
- [x] Propriedades JWT já em `application.properties`
- [ ] Confirmar que `app.jwt.secret` usa variável de ambiente `JWT_SECRET` em produção (nunca commitar segredo real)

### 2. Entidades e repositories (mínimo para seed)

Criar entidades JPA alinhadas à migration `V2`:

| Entidade | Tabela | Campos principais |
|----------|--------|-------------------|
| `Category` | `categories` | `id`, `userId`, `name`, `description`, `createdAt`, `updatedAt` |
| `FinancialResponsible` | `financial_responsible` | `id`, `userId`, `name`, `createdAt`, `updatedAt` |

**Observação:** o nome da tabela no banco é `financial_responsible` (singular). Use `@Table(name = "financial_responsible")` na entidade; o endpoint REST será `/api/v1/financial-responsibles` (etapa 7).

Repositories:

```java
// CategoryRepository
List<Category> findAllByUserId(UUID userId);

// FinancialResponsibleRepository
List<FinancialResponsible> findAllByUserId(UUID userId);
```

> CRUD completo desses recursos vem nas etapas 6–7. Aqui basta o necessário para persistir o seed no register.

### 3. PasswordEncoder (Argon2)

Criar bean em `config/SecurityConfig.java` (ou classe dedicada):

```java
@Bean
public PasswordEncoder passwordEncoder() {
    return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
}
```

Documentar no código: **Argon2** foi escolhido em vez de BCrypt (spec original) por maior resistência a ataques com GPU.

### 4. JwtService

Arquivo: `service/JwtService.java`

Responsabilidades:

- Gerar token com `subject` = `userId` (UUID como string)
- Validar assinatura e expiração
- Extrair `userId` do token

Propriedades injetadas:

- `@Value("${app.jwt.secret}")` — chave de assinatura (Base64)
- `@Value("${app.jwt.expiration-ms}")` — tempo de vida do token (padrão: 24h)

Exemplo de claims mínimas:

```java
Jwts.builder()
    .subject(userId.toString())
    .issuedAt(new Date())
    .expiration(new Date(System.currentTimeMillis() + expirationMs))
    .signWith(secretKey)
    .compact();
```

### 5. AuthService

Arquivo: `service/AuthService.java`

#### Register

1. Verificar se email já existe → `BusinessRuleException` ou exceção dedicada (email duplicado)
2. Hash da senha com `passwordEncoder.encode(password)`
3. Salvar `User` via `UserRepository`
4. **Seed** — criar para o novo usuário:
   - 6 categorias: **Casa**, **Educação**, **Lazer**, **Transporte**, **Saúde**, **Outros**
   - 1 `FinancialResponsible` com nome **"Titular"**
5. Retornar `TokenResponse` (token + `UserResponse`)

Use `@Transactional` no register para garantir atomicidade (user + seed).

#### Login

1. Buscar user por email → se ausente, `InvalidCredentialsException`
2. `passwordEncoder.matches(rawPassword, user.getPasswordHash())` → se falhar, `InvalidCredentialsException`
3. Gerar JWT e retornar `TokenResponse`

### 6. JwtAuthenticationFilter

Arquivo: `config/JwtAuthenticationFilter extends OncePerRequestFilter`

Fluxo:

```
Request com header Authorization: Bearer <token>
    │
    ▼
Extrair token do header
    │
    ▼
JwtService valida e extrai userId
    │
    ▼
Criar Authentication e popular SecurityContext
    │
    ▼
chain.doFilter() — segue para o controller
```

Se token ausente/inválido em rota protegida → Spring Security retorna **401**.

### 7. SecurityConfig

Arquivo: `config/SecurityConfig.java`

Configuração mínima:

```java
http
    .csrf(csrf -> csrf.disable())
    .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
    .authorizeHttpRequests(auth -> auth
        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login").permitAll()
        .requestMatchers("/api/v1/**").authenticated()
        .anyRequest().permitAll()
    )
    .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
```

Rotas públicas: apenas `POST /api/v1/auth/register` e `POST /api/v1/auth/login`.

Demais `/api/v1/**`: autenticadas.

### 8. AuthController

Arquivo: `controller/AuthController.java`

| Método | Path | Request | Response | Status |
|--------|------|---------|----------|--------|
| POST | `/api/v1/auth/register` | `RegisterRequest` | `TokenResponse` | `201 Created` |
| POST | `/api/v1/auth/login` | `LoginRequest` | `TokenResponse` | `200 OK` |

Usar `@Valid` nos `@RequestBody`.

### 9. SecurityUtils

Arquivo: `service/SecurityUtils.java` (ou `config/`)

```java
public static UUID getCurrentUserId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    // extrair UUID do principal / details definidos no filtro JWT
}
```

Será usado em todas as etapas seguintes (CRUD) para obter o `userId` do token — **nunca** do body da requisição.

---

## Arquivos a criar/alterar

```
src/main/java/com/jaconis/finance_api/
├── config/
│   ├── SecurityConfig.java
│   └── JwtAuthenticationFilter.java
├── controller/
│   └── AuthController.java
├── domain/entity/
│   ├── Category.java
│   └── FinancialResponsible.java
├── repository/
│   ├── CategoryRepository.java
│   └── FinancialResponsibleRepository.java
└── service/
    ├── AuthService.java
    ├── JwtService.java
    └── SecurityUtils.java
```

Arquivos já existentes (não recriar):

- `dto/request/RegisterRequest.java`, `LoginRequest.java`
- `dto/response/TokenResponse.java`, `UserResponse.java`
- `service/UserMapper.java`
- `exception/InvalidCredentialsException.java`
- `db/migration/V2__categories_and_financial_responsibles.sql`

---

## Contratos da API

### POST /api/v1/auth/register

**Request:**

```json
{
  "email": "usuario@exemplo.com",
  "password": "senha123"
}
```

**Response (201):**

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "user": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "email": "usuario@exemplo.com",
    "createdAt": "2026-07-05T12:00:00Z"
  }
}
```

### POST /api/v1/auth/login

**Request:**

```json
{
  "email": "usuario@exemplo.com",
  "password": "senha123"
}
```

**Response (200):** mesmo formato de `TokenResponse`.

### Erros esperados

| Cenário | Status | Exceção |
|---------|--------|---------|
| Payload inválido (email malformado, senha curta) | 400 | `MethodArgumentNotValidException` |
| Email já cadastrado | 409 | `BusinessRuleException` |
| Credenciais inválidas no login | 401 | `InvalidCredentialsException` |
| Token ausente/inválido em rota protegida | 401 | Spring Security |

---

## Como validar

### 1. Subir ambiente

```powershell
docker compose up -d
.\mvnw.cmd spring-boot:run
```

A API roda na porta **3000** (configurada em `application.properties`).

### 2. Registrar usuário

```powershell
Invoke-RestMethod -Method Post `
  -Uri http://localhost:3000/api/v1/auth/register `
  -ContentType "application/json" `
  -Body '{"email":"a@b.com","password":"senha123"}'
```

### 3. Login

```powershell
Invoke-RestMethod -Method Post `
  -Uri http://localhost:3000/api/v1/auth/login `
  -ContentType "application/json" `
  -Body '{"email":"a@b.com","password":"senha123"}'
```

### 4. Verificar seed no banco

```powershell
docker compose exec postgres psql -U postgres -d finance-api -c "SELECT name FROM categories;"
docker compose exec postgres psql -U postgres -d finance-api -c "SELECT name FROM financial_responsible;"
```

Esperado: 6 categorias + 1 registro "Titular" por usuário registrado.

### 5. Testar proteção JWT

Chamar qualquer rota `/api/v1/**` (exceto auth) **sem** header → **401**.

Com header:

```powershell
$headers = @{ Authorization = "Bearer <token>" }
Invoke-RestMethod -Uri http://localhost:3000/api/v1/categories -Headers $headers
```

(Endpoint de categories só existirá na etapa 6; até lá, basta confirmar 401 sem token.)

### 6. Testes automatizados

```powershell
.\mvnw.cmd test
```

---

## Definition of Done

- [ ] `POST /api/v1/auth/register` cria user + 6 categorias + 1 financial responsible "Titular"
- [ ] `POST /api/v1/auth/login` retorna JWT válido
- [ ] Senha persistida como hash Argon2 (coluna `password_hash`, nunca texto plano)
- [ ] Rotas `/api/v1/**` (exceto auth) exigem `Authorization: Bearer <token>`
- [ ] `SecurityUtils.getCurrentUserId()` disponível para services futuros
- [ ] Payload inválido retorna 400 estruturado; credenciais inválidas retornam 401
- [ ] `.\mvnw.cmd test` continua verde
- [ ] App sobe com `ddl-auto=validate` sem erro de schema

---

## Erros comuns

| Problema | Causa provável | Solução |
|----------|----------------|---------|
| Register retorna 401 | Rotas auth não liberadas no `SecurityConfig` | Adicionar `permitAll()` para `/api/v1/auth/**` |
| `Connection refused` ao subir app | PostgreSQL não está rodando | `docker compose up -d` |
| JWT inválido imediatamente | Segredo diferente entre geração e validação; ou `sub` não é UUID | Conferir `app.jwt.secret` e formato do subject |
| Schema validation failed | Entidade JPA não bate com migration V2 | Conferir `@Table`, `@Column(name=...)` vs SQL |
| Seed parcial (user sem categorias) | Register sem `@Transactional` | Envolver register em transação única |
| Segredo JWT no código | Hardcode em vez de properties/env | Usar `application.properties` + `JWT_SECRET` |

---

## Perguntas de revisão

1. Por que JWT stateless em vez de sessão HTTP no servidor?
2. O que impede um usuário de alterar o `userId` no body da requisição?
3. Por que Argon2 é preferível ao BCrypt hoje?
4. Por que o `subject` do JWT deve ser o `userId` e não o email?
5. O que acontece se o register falhar no meio do seed?

---

## Próxima etapa

**Etapa 5** — Primeiro teste unitário (`AuthServiceTest` com Mockito): email duplicado, senha incorreta, register com seed.

Consulte [`doc/tasks.md`](./tasks.md) para o plano completo.
