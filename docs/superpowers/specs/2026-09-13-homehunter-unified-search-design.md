# Design Document: HomeHunter Unified Real Estate Search & Aggregator

**Date:** 2026-09-13  
**Status:** Approved  
**Target Platform:** Java 26 / Spring Boot 4.x / MySQL 8  

---

## 1. Overview & Objective

HomeHunter is a unified real estate search application designed to collect, aggregate, deduplicate, filter, and store property listings (houses and apartments) in Pernambuco (starting with Recife and surrounding areas) from four major real estate portals:
- ZapImóveis
- VivaReal
- Chaves na Mão
- ImovelWeb

The application is structured using **Hexagonal Architecture (Ports & Adapters)**, **Domain-Driven Design (DDD)** principles, and **Clean Code** standards.

---

## 2. Architecture & Package Structure

The project strictly isolates domain logic from infrastructure and framework dependencies.

```
br.com.brunofelix.homehunter
├── core/                                # Pure Domain & Business Logic (Zero Spring dependencies)
│   ├── domain/
│   │   ├── model/                      # Entities, Aggregates & Value Objects
│   │   │   ├── Property.java            # Aggregate Root
│   │   │   ├── PropertyId.java          # Value Object (Deterministic Natural Identity Hash)
│   │   │   ├── Address.java             # Value Object (State, City, Neighborhood, Street)
│   │   │   ├── Price.java               # Value Object (Currency & Amount)
│   │   │   ├── Area.java                # Value Object (m²)
│   │   │   ├── PropertyType.java        # Enum (CASA, APARTAMENTO)
│   │   │   ├── PropertySource.java      # Entity (Portal Enum, External ID, URL, Price, Timestamp)
│   │   │   └── SearchQuery.java         # Value Object for filter criteria
│   │   ├── port/
│   │   │   ├── dataprovider/           # Output Ports (Interfaces)
│   │   │   │   ├── PropertyRepositoryPort.java
│   │   │   │   └── PropertyCollectorPort.java
│   │   │   └── entrypoint/             # Input Ports (Use Case Interfaces)
│   │   │       ├── SearchPropertiesInputPort.java
│   │   │       └── SyncPropertiesInputPort.java
│   │   └── usecase/                    # Business Use Cases
│   │       ├── SearchPropertiesUseCase.java
│   │       └── SyncPropertiesUseCase.java
│
├── dataprovider/                        # Output Adapters (Infrastructure & External Integrations)
│   ├── database/                       # MySQL Persistence Adapter
│   │   ├── PropertyRepositoryAdapter.java
│   │   ├── entity/
│   │   │   ├── PropertyEntity.java
│   │   │   └── PropertySourceEntity.java
│   │   ├── mapper/
│   │   │   └── PropertyDatabaseMapper.java
│   │   └── repository/
│   │       └── SpringDataPropertyRepository.java
│   └── collector/                      # External Portal Scraping Adapters
│       ├── ZapImoveisCollectorAdapter.java
│       ├── VivaRealCollectorAdapter.java
│       ├── ChavesNaMaoCollectorAdapter.java
│       └── ImovelWebCollectorAdapter.java
│
└── entrypoint/                          # Input Adapters (REST Controllers & Schedulers)
    ├── rest/
    │   ├── PropertyController.java
    │   ├── dto/
    │   │   ├── PropertyResponseDto.java
    │   │   ├── PropertySourceResponseDto.java
    │   │   └── PropertySearchRequestDto.java
    │   └── mapper/
    │       └── PropertyRestMapper.java
    └── cron/
        └── PropertySyncScheduler.java
```

---

## 3. Domain Model & Natural Identity (Deduplication)

### 3.1 Aggregate Root: `Property`
- **`PropertyId`**: Deterministic Value Object created from a SHA-256 fingerprint of normalized property attributes:
  `SHA256("PE" + "|" + "RECIFE" + "|" + normalized(NEIGHBORHOOD) + "|" + TYPE + "|" + AREA_M2 + "|" + BEDROOMS)`
  *Benefit:* Serves as the primary key (`id`) in MySQL. Re-collecting an identical property across different portals automatically hits the same `PropertyId`, eliminating redundant rows and allowing seamless source merging.
- **`title`**: String
- **`type`**: `PropertyType` (`CASA`, `APARTAMENTO`)
- **`price`**: `Price` (Consolidated/latest price)
- **`area`**: `Area` (Usable area in m²)
- **`bedrooms`**: Integer
- **`address`**: `Address` (`state`, `city`, `neighborhood`, `street`)
- **`sources`**: `List<PropertySource>` (Portals where listing exists, URLs, individual portal prices, last sync time)
- **`createdAt` / `updatedAt`**: `LocalDateTime`

### 3.2 Deduplication Workflow (`SyncPropertiesUseCase`)
1. For each active `PropertyCollectorPort`:
   - Fetch properties matching default Pernambuco criteria.
2. For each fetched property candidate:
   - Compute its `PropertyId` deterministic hash.
   - Query `PropertyRepositoryPort.findById(propertyId)`.
   - **If exists:**
     - Update price/area if updated.
     - Add/update portal entry in `sources` list.
   - **If new:**
     - Create new `Property` aggregate and add initial portal in `sources`.
3. Save updated/created `Property` aggregate via `PropertyRepositoryPort`.

---

## 4. API & Interface Specifications

### 4.1 REST API (`/api/v1/properties`)

- **`GET /api/v1/properties`**
  - **Query Parameters**: `state` (default "PE"), `city` (default "Recife"), `neighborhood`, `type` (`CASA`/`APARTAMENTO`), `minPrice`, `maxPrice`, `minArea`, `maxArea`, `bedrooms`, `page`, `size`.
  - **Response**: Paginated list of unified properties with their active source URLs.

- **`GET /api/v1/properties/{id}`**
  - **Path Parameter**: `id` (PropertyId string)
  - **Response**: Complete property details including history and links to all portals (Zap, VivaReal, etc.).

- **`POST /api/v1/properties/sync`**
  - **Trigger**: Asynchronously starts the portal collection & unification batch job.
  - **Response**: `202 Accepted` with execution status message.

---

## 5. Persistence & MySQL Schema

### Table: `tb_property`
| Column | Type | Constraints |
|---|---|---|
| `id` | VARCHAR(64) | PRIMARY KEY (Deterministic Hash) |
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
| `collected_at` | DATETIME | NOT NULL |

---

## 6. Verification & Quality Plan

1. **Unit Testing (`core`)**:
   - Test domain entities and `PropertyId` hash generation consistency.
   - Test `SyncPropertiesUseCase` deduplication logic with Mockito for repositories and collectors.
2. **Integration Testing (`dataprovider` & `entrypoint`)**:
   - Database repository integration tests using Spring Boot Test & H2 / MySQL test profile.
   - Controller REST integration tests using `MockMvc`.
3. **Build & Code Verification**:
   - `./gradlew check` (compile Java 26, run tests).
