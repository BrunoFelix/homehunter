# Design Document: HomeHunter Unified Real Estate Search & Aggregator

**Date:** 2026-09-13  
**Status:** Approved  
**Target Platform:** Java 26 / Spring Boot 4.x / MySQL 8  

---

## 1. Overview & Objective

HomeHunter is a unified real estate search application that collects, aggregates, deduplicates, filters, and stores property listings (houses and apartments) in **Pernambuco** from four major portals:
- ZapImóveis
- VivaReal
- Chaves na Mão
- ImovelWeb

The application follows **Hexagonal Architecture (Ports & Adapters)**, **Domain-Driven Design (DDD)**, and **Clean Code** standards.

### 1.1 Escopo de Coleta e Filtros
- **Coleta**: configurable scope over cities/neighborhoods in Pernambuco (default: Recife). Repeated syncs refresh the store.
- **Filtros de busca** (sobre os dados já coletados): `state`, `city`, `neighborhood`, `type` (`CASA`/`APARTAMENTO`), `minPrice`/`maxPrice`, `minArea`/`maxArea`, `bedrooms` (exato), paginação.

### 1.2 Linguagem Ubíqua (Ubiquitous Language)
| Termo | Definição |
|---|---|
| **Property (Imóvel)** | Aggregate Root que representa o imóvel único já consolidado entre portais. |
| **CollectedProperty (Anúncio Coletado)** | Candidato cru recém-coletado de um portal, ainda não consolidado. |
| **PropertySource (Fonte do Imóvel)** | Registro de um anúncio do imóvel em um portal específico (URL, ID externo, preço, datas). |
| **Portal** | Portal imobiliário externo de onde os anúncios são coletados (`PortalName`). |
| **Sync (Sincronização)** | Processo de coleta dos portais + consolidação/deduplicação + persistência. |
| **Anúncio criado (`announcedAt`)** | Data de criação/publicação do anúncio no próprio portal. |
| **Coleta (`collectedAt`)** | Momento em que coletamos os dados do portal para nosso banco. |
| **Identidade Natural** | `PropertyId` determinístico derivado dos atributos naturais do imóvel. |

---

## 2. Principles & Architecture Rules

1. **Regra de Dependência (Dependency Rule)**: dependências apontam sempre *para dentro*. `domain` não conhece Spring, banco, HTTP ou adapters. `application` depende apenas de `domain`. `dataprovider`/`entrypoint` dependem dos ports de `core`. Nenhum tipo de framework vaza para o `core` (ex.: sem `Pageable`/`Page` do Spring no core — usa-se `PagedResult` próprio).
2. **Layers**: todo o `core` é código de negócio testável em memória, sem contexto Spring.
3. **Ports & Adapters**: `core` expõe interfaces (ports). Adapters implementam esses ports; a montagem do grafo de dependências ocorre em um único **Composition Root** na inicialização do Spring.
4. **Persistência ignorante de domínio**: os adapters traduzem entidades JPA ↔ agregações de domínio. `PropertyRepositoryPort` só lida com modelos de domínio.
5. **Anti-Corruption Layer (ACL)**: cada coletor traduz os DTOs específicos do portal para o modelo de domínio, isolando ontologias externas.
6. **Handlers de entrada finos**: controllers/scheduler delegam para ports `in`; não contêm regra de negócio.

---

## 3. Package Structure (Hexagonal)

```
br.com.brunofelix.homehunter
│
├── core/                                # Núcleo (Domain + Application) - Zero dependências externas
│   ├── domain/                          # ── DOMAIN LAYER ──
│   │   ├── model/
│   │   │   ├── Property.java            #    Aggregate Root (comportamento rico)
│   │   │   ├── PropertyId.java          #    Value Object (identidade natural determinística)
│   │   │   ├── PropertyType.java        #    Enum (CASA, APARTAMENTO)
│   │   │   ├── PortalName.java          #    Enum (ZAP, VIVAREAL, CHAVES_NA_MAO, IMOVELWEB)
│   │   │   ├── Price.java               #    Value Object (moeda BRL + valor)
│   │   │   ├── Area.java                #    Value Object (m²)
│   │   │   ├── Bedrooms.java            #    Value Object (quantidade de quartos)
│   │   │   ├── Address.java             #    Value Object (estado, cidade, bairro, logradouro?)
│   │   │   ├── PropertySource.java      #    Entidade interna do agregado Property
│   │   │   ├── CollectedProperty.java   #    Candidato cru coletado de um portal
│   │   │   └── PropertySearchCriteria.java  # Value Object (filtros + paginação)
│   │   ├── service/
│   │   │   └── PropertyDeduplicationService.java  # Regra de consolidação (função pura)
│   │   └── exception/
│   │       └── DomainException.java
│   │
│   └── application/                     # ── APPLICATION LAYER ──
│       ├── model/
│       │   ├── CollectionScope.java     #    Escopo de coleta (config)
│       │   ├── PagedResult.java         #    Modelo de paginação neutro (sem Spring)
│       │   └── SyncStatus.java          #    Enum (ENQUEUED, REJECTED_RUNNING)
│       ├── usecase/                     # Application/Use Case Services (orquestração)
│       │   ├── SyncPropertiesUseCase.java
│       │   ├── SyncPropertiesUseCaseImpl.java
│       │   ├── SearchPropertiesUseCase.java
│       │   └── SearchPropertiesUseCaseImpl.java
│       └── port/
│           ├── in/
│           │   ├── SyncPropertiesInputPort.java    # sync(CollectionScope) → SyncStatus
│           │   └── SearchPropertiesInputPort.java  # search(PropertySearchCriteria) → PagedResult
│           └── out/
│               ├── PropertyRepositoryPort.java
│               └── PropertyCollectorPort.java
│
├── dataprovider/                        # ── DRIVEN ADAPTERS ──
│   ├── database/
│   │   ├── PropertyRepositoryAdapter.java
│   │   ├── entity/
│   │   │   ├── PropertyEntity.java
│   │   │   └── PropertySourceEntity.java
│   │   ├── mapper/
│   │   │   └── PropertyDatabaseMapper.java        # Entity ↔ Property
│   │   └── repository/
│   │       └── SpringDataPropertyRepository.java
│   │
│   └── collector/
│       ├── anticorruption/
│       │   ├── PortalPropertyNormalizer.java      # Normalização (strings, valores, tipos)
│       │   └── PortalPropertyParser.java          # DTO externo → CollectedProperty
│       ├── ZapImoveisCollectorAdapter.java
│       ├── VivaRealCollectorAdapter.java
│       ├── ChavesNaMaoCollectorAdapter.java
│       └── ImovelWebCollectorAdapter.java
│
└── entrypoint/                          # ── DRIVING ADAPTERS ──
    ├── rest/
    │   ├── PropertyController.java
    │   ├── dto/
    │   │   ├── PropertyResponseDto.java
    │   │   ├── PropertySourceResponseDto.java
    │   │   ├── PagedResultDto.java
    │   │   ├── CollectionScopeRequestDto.java
    │   │   └── PropertySearchRequestDto.java
    │   └── mapper/
    │       └── PropertyRestMapper.java            # Property/PagedResult ↔ DTO
    └── cron/
        └── PropertySyncScheduler.java
```

---

## 4. Domain Model & Invariants

### 4.1 Aggregate Root: `Property` (comportamento rico)
- **`PropertyId`**: Value Object de **identidade natural determinística** e imutável. Derivado via SHA-256 do fingerprint canônico normalizado:
  `SHA256(uppercase(STATE) + "|" + uppercase(CITY) + "|" + normalize(NEIGHBORHOOD) + "|" + TYPE + "|" + AREA_M2 + "|" + BEDROOMS)`.
  Serve como PK no MySQL. Recoletar o mesmo imóvel em outro portal gera o mesmo `PropertyId`, permitindo fusão de fontes sem linhas duplicadas.
- **`title`**: String
- **`type`**: `PropertyType` (`CASA`, `APARTAMENTO`)
- **`price`**: `Price` (valor consolidado — ver regra em 4.3)
- **`area`**: `Area` (área útil em m²)
- **`bedrooms`**: `Bedrooms`
- **`address`**: `Address` (`state`, `city` obrigatórios; `neighborhood`, `street`)
- **`sources`**: `List<PropertySource>` (agregado composto)
- **`createdAt` / `updatedAt`**: `LocalDateTime` (timestamps de persistência, internos)

**Comportamento**:
- `Property.createFrom(CollectedProperty)` — fábrica para novos imóveis (instancia 1ª `PropertySource`).
- `Property.merge(CollectedProperty)` — adiciona/atualiza `PropertySource` e atualiza `price`/`area` quando aplicável.
- `Property.sources()` / `Property.consolidatedPrice()` — leituras imutáveis.

### 4.2 `CollectedProperty` (candidato cru)
Representa um anúncio bruto recém-coletado de um portal, antes da consolidação:
- Atributos naturais (`type`, `area`, `bedrooms`, `address`) + `portalName`, `externalId`, `url`, `portalPrice`, `announcedAt` (nullable), `title`.
- Value Object imutável produzido pela ACL.

### 4.3 Invariantes e Regras Determinísticas
- `Price.value > 0`; `Area.value > 0`; `Bedrooms.value >= 0`.
- `state` e `city` obrigatórios; `neighborhood` e `street` opcionais.
- `PropertyId` imutável, derivado apenas de atributos naturais normalizados.
- `PropertySource` exige `portalName`, `externalId` e `url` não nulos.
- Violações lançam `DomainException`; objetos nunca nascem em estado inválido.
- **Preço consolidado**: o `price` do agrupador é o do `PropertySource` com `collectedAt` mais recente; em empate, o de `announcedAt` mais recente; novo empate, o menor valor (determinístico).
- **`announcedAt` indisponível** → `null` permitido; nas ordenações de consolidação, `null` fica por último.

### 4.4 Domain Service: `PropertyDeduplicationService` (função pura, sem I/O)
1. Normaliza atributos (estado/cidade em caixa alta, bairro em caixa e sem acentos, valores arredondados, tipo padronizado).
2. Gera o `PropertyId` canônico a partir de um `CollectedProperty`.
3. Dado um `CollectedProperty` e o `Optional<Property>` existente:
   - **Não existe** → `Property.createFrom(...)`.
   - **Existe** → `Property.merge(...)`, retornando decisão (novo/atualizado).

### 4.5 Trade-offs da Identidade Natural (documentados)
- **Unidades idênticas no mesmo prédio** (mesmo bairro, área, quartos, tipo) colapsam em um único `Property` mesmo sendo apartamentos diferentes. Mitigação futura: incluir logradouro/número ou nome do empreendimento no fingerprint quando disponível.
- **Variação de área** entre portais pode separar candidatos que são o mesmo imóvel; o normalizador arredonda área (ex.: casa decimal) para reduzir falsos negativos.
- **`neighborhood` ausente** degrada a identidade para nível de cidade (risco de over-merge); recomendado permitir coleta priorizando anúncios com bairro.

---

## 5. Application Layer (Use Cases)

Application Services orquestram; regras de negócio vivem em `domain`.

### 5.1 `SyncPropertiesUseCase`
Ciclo: coleta → deduplica → persiste.
1. **Single-flight**: apenas uma sincronização por vez (guard no `SyncPropertiesUseCaseImpl`; triggers do cron e do controller enfileiram no mesmo executor single-threaded).
2. Para cada `PropertyCollectorPort` **ativo** (config), executa `collect(CollectionScope)`.
3. Para cada `CollectedProperty`, `PropertyDeduplicationService` decide criar ou fundir.
4. Persiste agregados afetados via `PropertyRepositoryPort` (transação por agregado).
5. **Tolerância a falhas**: falha de um portal não derruba o job; resultados por portal são logados (sucesso/falha, contagens). O job como um todo falha apenas se o repositório falhar.

### 5.2 `SearchPropertiesUseCase`
1. Recebe `PropertySearchCriteria` (estado, cidade, bairro, tipo, faixas de preço/área, `bedrooms` exato, `page`, `size`).
2. Consulta `PropertyRepositoryPort.search(criteria)` paginado.
3. Retorna `PagedResult<Property>` neutro (sem tipos do Spring) ao adaptador de entrada.

### 5.3 Ports (assinaturas)
- **Driving (`core.application.port.in`)**:
  - `SyncPropertiesInputPort.sync(CollectionScope): SyncStatus` (`ENQUEUED` ou `REJECTED_RUNNING`).
  - `SearchPropertiesInputPort.search(PropertySearchCriteria): PagedResult<Property>`.
- **Driven (`core.application.port.out`)**:
  - `PropertyCollectorPort.collect(CollectionScope): List<CollectedProperty>`.
  - `PropertyRepositoryPort.findById(PropertyId): Optional<Property>`.
  - `PropertyRepositoryPort.search(PropertySearchCriteria): PagedResult<Property>`.
  - `PropertyRepositoryPort.save(Property): Property`.

---

## 6. API & Entrypoints (Driving Adapters)

### 6.1 REST API (`/api/v1/properties`) — documentada via OpenAPI/Swagger (springdoc)
- **`GET /api/v1/properties`**
  - **Query Parameters**: `state` (default `PE`), `city` (default `Recife`), `neighborhood`, `type` (`CASA`/`APARTAMENTO`), `minPrice`, `maxPrice`, `minArea`, `maxArea`, `bedrooms`, `page` (default 0), `size` (default 20, max 100).
  - **Response 200**: `PagedResultDto` com imóveis unificados e links das fontes ativas.
  - **Response 400**: filtros inválidos (ex.: `minPrice > maxPrice`, página excedente).
- **`GET /api/v1/properties/{id}`**
  - **Path Parameter**: `id` (`PropertyId`).
  - **Response 200**: detalhes completos incl. todas as fontes (`portalName`, `url`, preço no portal, `announcedAt`, `collectedAt`).
  - **Response 404**: imóvel não encontrado.
- **`POST /api/v1/properties/sync`**
  - **Request Body** (opcional): escopo de coleta + filtros pré-armazenamento (`SyncRequestDto` mapeado para `SyncRequest`).
  - **Filtros pré-armazenamento**: `type`, `minPrice`, `maxPrice`, `minArea`, `maxArea`, `bedrooms`, `neighborhood` — aplicados **após** coleta e **antes** de persistir. Se vazio/null, todos os anúncios coletados são armazenados. Se preenchido, apenas os que correspondem aos filtros são persistidos.
  - **Response 202**: `{ "status": "enqueued", "message": "Portal synchronization batch started" }` — sem histórico/estado persistido de execução (YAGNI; logs internos servem de observabilidade).
  - **Response 409**: sincronização já em andamento (single-flight).

### 6.2 Cron (`entrypoint.cron`)
- `PropertySyncScheduler`: dispara `SyncPropertiesUseCase` via `@Scheduled` com cron configurável (`app.collector.cron`). Reentrância evitada pelo guard single-flight.

---

## 7. Persistence & MySQL Schema (Driven Adapter)

### Table: `tb_property`
| Column | Type | Constraints |
|---|---|---|
| `id` | VARCHAR(64) | PRIMARY KEY (deterministic `PropertyId`) |
| `title` | VARCHAR(255) | NOT NULL |
| `type` | VARCHAR(20) | NOT NULL |
| `price` | DECIMAL(12,2) | NOT NULL |
| `area` | DECIMAL(8,2) | NOT NULL |
| `bedrooms` | INT | NOT NULL |
| `state` | VARCHAR(2) | NOT NULL INDEX |
| `city` | VARCHAR(100) | NOT NULL INDEX |
| `neighborhood` | VARCHAR(100) | NOT NULL INDEX |
| `created_at` | DATETIME | NOT NULL |
| `updated_at` | DATETIME | NOT NULL |

### Table: `tb_property_source`
| Column | Type | Constraints |
|---|---|---|
| `id` | BIGINT | PRIMARY KEY AUTO_INCREMENT |
| `property_id` | VARCHAR(64) | FOREIGN KEY → `tb_property(id)` |
| `portal_name` | VARCHAR(50) | NOT NULL (ZAP, VIVAREAL, CHAVES_NA_MAO, IMOVELWEB) |
| `external_id` | VARCHAR(100) | NOT NULL |
| `url` | TEXT | NOT NULL |
| `price` | DECIMAL(12,2) | NOT NULL |
| `announced_at` | DATETIME | NULL (data de criação/publicação do anúncio no portal) |
| `collected_at` | DATETIME | NOT NULL (data da coleta em nosso banco) |

**Constraints/chaves**:
- `UNIQUE(portal_name, external_id)` em `tb_property_source` — impede fontes duplicadas em syncs concorrentes.
- Índices em `state`, `city`, `neighborhood`, `type`, `price` para os filtros comuns.

**Consistência e timezone**:
- `PropertyRepositoryAdapter` persiste um agregado (Property + sources) de forma **atômica** (`@Transactional`).
- Timestamps: `announcedAt`/`collectedAt` em **UTC**; serialização ISO-8601 com offset nas respostas da API.

---

## 8. Configuração (`application.properties`)

| Chave | Valor default | Descrição |
|---|---|---|
| `app.collector.cron` | `0 0 3 * * *` | Cron da sincronização periódica |
| `app.collector.scope.cities` | `RECIFE` | Cidades/estado cobertos (Pernambuco) |
| `app.collector.scope.neighborhoods` | vazio (todos) | Bairros opcionais |
| `app.collector.zapimoveis.enabled` | `true` | Habilita/desabilita portal |
| `app.collector.vivareal.enabled` | `true` | idem |
| `app.collector.chavesnamao.enabled` | `true` | idem |
| `app.collector.imovelweb.enabled` | `true` | idem |
| `app.collector.timeout` | `30s` | Timeout HTTP por portal |
| `app.collector.politeness-delay` | `500ms` | Atraso entre requisições por portal (boas práticas) |

> Responsabilidade dos adapters `collector`: timeouts configuráveis, headers de navegação real e tratamento individual de falha (não propagar para os demais portais).

**Banco local (docker-compose)**: `docker compose up -d` sobe MySQL 8 com `MYSQL_DATABASE=databaseHomehunter`, `MYSQL_USER=usernameHomehunter` / `MYSQL_PASSWORD=passwordHomehunter`. `application.properties` aponta para esses valores (`spring.datasource.url=jdbc:mysql://localhost:3306/databaseHomehunter...`). Fallback para H2 em memória via override de propriedades (`--spring.datasource.url=jdbc:h2:mem:...`) — o build inclui `runtimeOnly 'com.h2database:h2'` para viabilizar execução local sem MySQL.

---

## 9. Verification & Quality Plan

1. **Unit (`core`)** — sem Spring:
   - Fábricas/construtores e invariantes (`DomainException`).
   - `PropertyId` determinístico: mesmo fingerprint → mesmo ID; campos normalizados.
   - `PropertyDeduplicationService` (criar vs. fundir; atualização de price/area/sources; desempate determinístico).
   - Use cases com Mockito (single-flight; tolerância a falha de portal).
2. **Integration (`dataprovider` & `entrypoint`)**:
   - `PropertyRepositoryAdapter` (Testcontainers MySQL / H2 em perfil de teste).
   - Controllers REST (MockMvc) cobrindo contratos HTTP (200/202/404/409/400).
   - Mappers (`PropertyDatabaseMapper`, `PropertyRestMapper`).
3. **Build & Artifact**:
   - `./gradlew check` (compilação Java 26 + todos os testes).

### 9.1 Validação "ao vivo" (executada em 2026-09-13)
Contratos **validados contra instância real** (MySQL via docker-compose + app em `:8080`):
- `GET /api/v1/properties` (defaults e com filtros `type`, `bedrooms`, `minPrice`, `page`, `size`) → **200** com `PagedResultDto` (`content/page/size/totalElements/totalPages`) e `PropertyResponseDto` contendo `id`, `title`, `type`, `price`, `area`, `bedrooms`, `state`, `city`, `neighborhood` e `sources[]`.
- `GET /api/v1/properties/{id}` → **200** com `sources[].portalName/externalId/url/price/announcedAt/collectedAt` (dates ISO-8601) e **404** para id inexistente.
- `POST /api/v1/properties/sync` (body `{}`) → **202** `{status:enqueued}`; novo POST durante execução → **409** `{status:rejected}` (single-flight OK).
- Persistência confirmada: 2 properties / 3 `tb_property_source` no MySQL; deduplicação observada (2 portais fundidos em 1 agrupador com 2 fontes).

**Achados / limitações (risco para o batch real):**
- **Todos os 4 portais bloqueiam scraping direto**: ImovelWeb respondeu **403**; Zap/VivaReal/Chaves na Mão retornaram **0 listagens** ("anti-bot engaged") — o fallback injeta dados de amostra. Necessário contra-medidas (User-Agent real, headers/JS rendering, proxy/rotacionamento) antes de produção.
- **Queda silenciosa do JVM**: durante scrape do Zap (após ~40s de sync) o processo encerrou sem exceção no log (stderr não capturado no teste). Hipótese principal: stall de rede no fetch do portal; investigar com stderr capturado e timeout menor.