# Design Document: HomeHunter Unified Real Estate Search & Aggregator

**Date:** 2026-09-13  
**Status:** Approved  
**Target Platform:** Java 26 / Spring Boot 4.x / MySQL 8  

---

## 1. Overview & Objective

HomeHunter is a unified real estate search application designed to collect, aggregate, deduplicate, filter, and store property listings (houses and apartments) in Pernambuco (starting with Recife) from four major real estate portals:
- ZapImóveis
- VivaReal
- Chaves na Mão
- ImovelWeb

The application is built on **Hexagonal Architecture (Ports & Adapters)**, **Domain-Driven Design (DDD)**, and **Clean Code** standards.

### 1.1 Linguagem Ubíqua (Ubiquitous Language)
| Termo | Definição |
|---|---|
| **Property (Imóvel)** | Aggregate Root que representa o imóvel único já consolidado entre portais. |
| **PropertySource (Fonte do Imóvel)** | Registro de um anúncio do imóvel em um portal específico (URL, ID externo, preço, datas). |
| **Portal** | Portal imobiliário externo de onde os anúncios são coletados (`PortalName`). |
| **Sync (Sincronização)** | Processo de coleta dos portais + consolidação/deduplicação + persistência. |
| **Anúncio criado (`announcedAt`)** | Data de criação/publicação do anúncio no próprio portal. |
| **Coleta (`collectedAt`)** | Momento em que coletamos os dados do portal para nosso banco. |

---

## 2. Principles & Architecture Rules

1. **Regra de Dependência (Dependency Rule)**: as dependências apontam sempre *para dentro*. `domain` não conhece Spring, banco, HTTP ou adapters. `application` depende apenas de `domain`. `dataprovider` e `entrypoint` dependem dos ports de `core`. Nenhum fluxo de dados atravessa camadas intermediárias.
2. **Layers**: todo o `core` é código de negócio testável em memória, sem contexto Spring.
3. **Ports & Adapters**: `core` expõe interfaces (ports). Adapters (`dataprovider`/`entrypoint`) implementam esses ports. A montagem do grafo de dependências acontece em um único **Composition Root** na inicialização do Spring.
4. **Persistência ignorante de domínio**: os adapters traduzem entidades JPA ↔ agregações de domínio. `PropertyRepositoryPort` só lida com modelos de domínio.
5. **Anti-Corruption Layer (ACL)**: cada coletor traduz os DTOs específicos do portal para o modelo de domínio, isolando ontologias externas.

---

## 3. Package Structure (Hexagonal)

```
br.com.brunofelix.homehunter
│
├── core/                                # Núcleo (Domain + Application) - Zero dependências externas
│   ├── domain/                          # ── DOMAIN LAYER ──
│   │   ├── model/                       # Entidades, Value Objects (imutáveis), Enum
│   │   │   ├── Property.java            #    Aggregate Root
│   │   │   ├── PropertyId.java          #    Value Object (identidade natural determinística)
│   │   │   ├── PropertyType.java        #    Enum (CASA, APARTAMENTO)
│   │   │   ├── PortalName.java          #    Enum (ZAP, VIVAREAL, CHAVES_NA_MAO, IMOVELWEB)
│   │   │   ├── Price.java               #    Value Object (moeda BRL + valor)
│   │   │   ├── Area.java                #    Value Object (m²)
│   │   │   ├── Bedrooms.java            #    Value Object (quantidade de quartos)
│   │   │   ├── Address.java             #    Value Object (estado, cidade, bairro, logradouro?)
│   │   │   ├── PropertySource.java      #    Entidade interna do agregado Property
│   │   │   └── PropertySearchCriteria.java  # Value Object (filtros de busca)
│   │   ├── service/                     # ── DOMAIN SERVICES ──
│   │   │   └── PropertyDeduplicationService.java  # Regra de consolidação/deduplicação
│   │   └── exception/                   # Exceções de domínio
│   │       └── DomainException.java
│   │
│   └── application/                     # ── APPLICATION LAYER ──
│       ├── usecase/                     # Application/Use Case Services (orquestração)
│       │   ├── SyncPropertiesUseCase.java         # Coleta → deduplica → persiste
│       │   ├── SyncPropertiesUseCaseImpl.java
│       │   ├── SearchPropertiesUseCase.java       # Consulta com filtros
│       │   └── SearchPropertiesUseCaseImpl.java
│       └── port/                        # ── PORTS (interfaces) ──
│           ├── in/                      # Driving Ports (consumidos pelos entrypoints)
│           │   ├── SyncPropertiesInputPort.java
│           │   └── SearchPropertiesInputPort.java
│           └── out/                     # Driven Ports (implementados pelos dataproviders)
│               ├── PropertyRepositoryPort.java
│               └── PropertyCollectorPort.java
│
├── dataprovider/                        # ── DRIVEN ADAPTERS (infraestrutura externa) ──
│   ├── database/                        # Persistência MySQL (Spring Data JPA)
│   │   ├── PropertyRepositoryAdapter.java        # Implementa PropertyRepositoryPort
│   │   ├── entity/
│   │   │   ├── PropertyEntity.java
│   │   │   └── PropertySourceEntity.java
│   │   ├── mapper/
│   │   │   └── PropertyDatabaseMapper.java       # Entity ↔ Agregado de domínio
│   │   └── repository/
│   │       └── SpringDataPropertyRepository.java # Spring Data (detalhe de implementação)
│   │
│   └── collector/                       # Coleta de portais externos + ACL
│       ├── anticorruption/              # Anti-Corruption Layer
│       │   ├── PortalPropertyNormalizer.java     # Normalização (strings, valores)
│       │   └── PortalPropertyParser.java         # DTO externo → Property (parcial)
│       ├── ZapImoveisCollectorAdapter.java       # Implementa PropertyCollectorPort
│       ├── VivaRealCollectorAdapter.java
│       ├── ChavesNaMaoCollectorAdapter.java
│       └── ImovelWebCollectorAdapter.java
│
└── entrypoint/                          # ── DRIVING ADAPTERS (entrada do sistema) ──
    ├── rest/                            # Controllers REST (Spring Web)
    │   ├── PropertyController.java
    │   ├── dto/
    │   │   ├── PropertyResponseDto.java
    │   │   ├── PropertySourceResponseDto.java
    │   │   └── PropertySearchRequestDto.java
    │   └── mapper/
    │       └── PropertyRestMapper.java            # Modelo de domínio ↔ DTO
    └── cron/                            # Agendamentos (@Scheduled)
        └── PropertySyncScheduler.java
```

---

## 4. Domain Model & Invariants

### 4.1 Aggregate Root: `Property`
- **`PropertyId`**: Value Object de **identidade natural determinística**, imutável. Derivado via SHA-256 do fingerprint canônico e normalizado do imóvel:
  `SHA256(STATE + "|" + CITY + "|" + normalized(NEIGHBORHOOD) + "|" + TYPE + "|" + AREA_M2 + "|" + BEDROOMS)`
  Serve como PK no MySQL. Recoletar o mesmo imóvel em outro portal gera o mesmo `PropertyId`, permitindo fusão de fontes sem duplicar linhas.
- **`title`**: String
- **`type`**: `PropertyType` (`CASA`, `APARTAMENTO`)
- **`price`**: `Price` (valor consolidado / mais recente)
- **`area`**: `Area` (área útil em m²)
- **`bedrooms`**: `Bedrooms`
- **`address`**: `Address` (`state`, `city`, `neighborhood`, `street` opcional)
- **`sources`**: `List<PropertySource>` (agregado composto; anúncios por portal)
- **`createdAt` / `updatedAt`**: `LocalDateTime` (timestamps internos do banco)

### 4.2 Invariantes de Domínio (aplicados em fábricas/construtores)
- `Price.value > 0`
- `Area.value > 0`
- `Bedrooms.value >= 0`
- `state` e `city` obrigatórios; `street` opcional
- `PropertyId` imutável e derivado apenas de atributos naturais
- `PropertySource` exige `portalName`, `externalId` e `url` não nulos
- Violações lançam `DomainException`; objetos nunca nascem em estado inválido

### 4.3 Domain Service: `PropertyDeduplicationService`
Encapsula a regra de consolidação (comportamento entre agregados/valores):
1. Normaliza atributos (bairro em caixa, valores arredondados, tipo padronizado).
2. Gera/valida o `PropertyId` canônico.
3. Dado um anúncio coletado, decide se **funde** em uma `Property` existente (atualiza `price`/`area` e adiciona/atualiza `PropertySource`) ou **cria** uma nova `Property`.

---

## 5. Application Layer (Use Cases)

Application Services orquestram, sem conter regras de negócio (essas vivem em `domain`).

### 5.1 `SyncPropertiesUseCase`
1. Para cada `PropertyCollectorPort` ativo, busca anúncios do escopo (Pernambuco).
2. Para cada anúncio, usa `PropertyDeduplicationService` para deduplicar/consolidar.
3. Persiste agregados afetados via `PropertyRepositoryPort` (transação atômica por agregado).
> Tolerância a falhas: se um portal falha, os demais continuam sem derrubar o job.

### 5.2 `SearchPropertiesUseCase`
1. Recebe `PropertySearchCriteria` (estado, cidade, bairro, tipo, faixa de preço, faixa de área, quartos).
2. Consulta `PropertyRepositoryPort.search(criteria)` paginado.
3. Retorna agregações de domínio para o adaptador de entrada.

### 5.3 Ports
- **Driving (`core.application.port.in`)**: `SyncPropertiesInputPort`, `SearchPropertiesInputPort` — contratos consumidos por `entrypoint`.
- **Driven (`core.application.port.out`)**: `PropertyRepositoryPort`, `PropertyCollectorPort` — contratos implementados por `dataprovider`.

---

## 6. API & Interface Specifications (Driving Adapters)

### 6.1 REST API (`/api/v1/properties`)
- **`GET /api/v1/properties`**
  - **Query Parameters**: `state` (default "PE"), `city` (default "Recife"), `neighborhood`, `type` (`CASA`/`APARTAMENTO`), `minPrice`, `maxPrice`, `minArea`, `maxArea`, `bedrooms`, `page`, `size`.
  - **Response**: lista paginada de imóveis unificados com links das fontes ativas.
- **`GET /api/v1/properties/{id}`**
  - **Path Parameter**: `id` (`PropertyId`)
  - **Response**: detalhes completos incluindo todas as fontes (portal, URL, preço no portal, `announcedAt`, `collectedAt`).
- **`POST /api/v1/properties/sync`**
  - **Trigger**: inicia o job de sincronização/coleta de forma assíncrona.
  - **Response**: `202 Accepted` com status de execução.

### 6.2 Cron (`entrypoint.cron`)
- `PropertySyncScheduler`: dispara `SyncPropertiesUseCase` via `@Scheduled` com cron configurável em `application.properties`.

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
| `property_id` | VARCHAR(64) | FOREIGN KEY -> `tb_property(id)` |
| `portal_name` | VARCHAR(50) | NOT NULL (ZAP, VIVAREAL, CHAVES_NA_MAO, IMOVELWEB) |
| `external_id` | VARCHAR(100) | NOT NULL |
| `url` | TEXT | NOT NULL |
| `price` | DECIMAL(12,2) | NOT NULL |
| `announced_at` | DATETIME | NULL (data de criação/publicação do anúncio no portal) |
| `collected_at` | DATETIME | NOT NULL (data da coleta em nosso banco) |

> Invariante de persistência: `tb_property_source` e `tb_property` são persistidas atomicamente pelo `PropertyRepositoryAdapter` (um aggregate = uma transação).

---

## 8. Verification & Quality Plan

1. **Unit Testing (`core`)** — testado sem Spring:
   - Fábricas/construtores e invariantes (`DomainException`s).
   - `PropertyId` determinístico: mesmo fingerprint → mesmo ID.
   - `PropertyDeduplicationService` (fusão vs. criação, atualização de price/area/sources).
   - Use cases com Mockito (repos e collectors mockados).
2. **Integration Testing (`dataprovider` & `entrypoint`)**:
   - `PropertyRepositoryAdapter` (H2/MySQL de teste, Testcontainers).
   - Controllers REST (MockMvc).
   - Mappers (`PropertyDatabaseMapper`, `PropertyRestMapper`).
3. **Build & Verification**:
   - `./gradlew check` (compilação Java 26 + todos os testes).