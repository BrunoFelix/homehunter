# HomeHunter

Backend de busca unificada de imóveis em Pernambuco. Coleta anúncios de portais imobiliários, consolida em um único registro, deduplica por identidade determinística (SHA-256) e expõe busca unificada via REST API.

Portais integrados (3 collectors):

- **ZapImóveis** — glue-api (curl subprocess)
- **VivaReal** — glue-api (curl subprocess)
- **Chaves na Mão** — API XHR ao vivo (JSoup)

## Stack

- Java 26 (toolchain) · Gradle 9 (wrapper)
- Spring Boot 4.1.1 (Web, Data JPA, Validation)
- Springdoc OpenAPI 2.8.5 · JSoup 1.18.3 · Lombok
- MySQL 8 (docker-compose) · H2 em testes e fallback local
- JUnit 5 + Mockito + Testcontainers

## Arquitetura (Hexagonal + DDD)

```
core/domain        Agregados e Value Objects (Property, PropertyId, Price, Address…), zero Spring/framework
core/application   Casos de uso (sync, search, get), models neutros e ports (InputPort/OutputPort)
dataprovider       Adapters dirigidos: database (JPA + Specification), collector (JSoup / curl + Anti-Corruption Layer)
entrypoint         Adapters dirigentes: rest (PropertyController + DTOs) e cron (PropertySyncScheduler)
```

Regra de dependência: as dependências sempre apontam para dentro. Não há Spring no `core`; a montagem de beans fica no **Composition Root** (`CoreBeanConfiguration`, incl. `ExecutorService` com `destroyMethod="shutdown"`).

## Pré-requisitos

- JDK 26 (toolchain)
- Docker (para MySQL) — opcional: rodar com fallback H2

## Setup local

```powershell
docker compose up -d                 # sobe MySQL 8 (db databaseHomehunter)
.\gradlew.bat bootJar                # builda o jar
java -jar build\libs\brunofelix-0.0.1-SNAPSHOT.jar --server.port=8080
```

Credenciais do banco: `databaseHomehunter` / `usernameHomehunter` / `passwordHomehunter` (espelhadas em `application.properties`).

Fallback sem MySQL (H2 em memória):

```powershell
java -jar build\libs\brunofelix-0.0.1-SNAPSHOT.jar --server.port=8080 `
  --spring.datasource.url=jdbc:h2:mem:livedb;DB_CLOSE_DELAY=-1 `
  --spring.datasource.driverClassName=org.h2.Driver `
  --spring.datasource.username=sa --spring.datasource.password= `
  --spring.jpa.hibernate.ddl-auto=create-drop `
  --spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect
```

O shell do ambiente é PowerShell: **não use `&&`**; use `cmd1; if ($?) { cmd2 }` para encadear comandos dependentes.

## Configuração dos collectors

| Propriedade | Default | Descrição |
|---|---|---|
| `app.collector.scope.cities` | `RECIFE` | Escopo geográfico padrão (filtro pré/pós-coleta) |
| `app.collector.zapimoveis.enabled` | `true` | Habilita o collector ZapImóveis |
| `app.collector.vivareal.enabled` | `true` | Habilita o collector VivaReal |
| `app.collector.chavesnamao.enabled` | `true` | Habilita o collector Chaves na Mão |
| `app.collector.max-pages` | `0` | **0 = coleta todas as páginas** declaradas pela API; >0 limita a um teto de segurança |
| `app.collector.cron` | `0 0 3 * * *` | Cron do sync automático |
| `app.collector.timeout` | `30s` | Timeout das requisições |
| `app.collector.politeness-delay` | `500ms` | Delay por página (±30% jitter) |
| `app.collector.pause-every-pages` | `20` | Pausa a cada N páginas (0 = desabilitado) |
| `app.collector.pause-duration` | `10s` | Duração da pausa entre lotes |

### Comportamento de coleta

- **Paginação completa**: cada collector percorre todas as páginas até o total declarado (`search.totalCount` / `metadata.totalPages`), sem cap fixo. Para PE/Recife no ZapImóveis isso significa ~244 páginas × 30 itens (~7300 anúncios).
- **Totais visíveis em log**: cada página loga `loading page X/Y...` (na 1ª ainda sem total conhecido).
- **Escopo geográfico honrado**: `CollectionScope` (estado/cidades) é aplicado como filtro pós-coleta em todos os collectors.
- **Throttling anti-DDoS**: delay por página (`politeness-delay`, ±30% de jitter) + pausa de `pause-duration` a cada `pause-every-pages` páginas.
- **Fallback de amostra**: apenas quando a coleta retorna 0 listagens ao vivo (anti-bot), injeta 1 amostra para robustez.

## Identidade, deduplicação e normalização

- `PropertyId` = SHA-256 determinístico de `STATE|CITY|NEIGHBORHOOD|TYPE|AREA|BEDROOMS` — PK no MySQL. O mesmo imóvel coletado em outro portal **funde fontes** (`tb_property_source`) em vez de duplicar `tb_property`.
- `announced_at` é normalizado para **UTC** e truncado à precisão de segundos (consistência entre portais).
- Preço é tratado como **dinheiro** (`BigDecimal`), nunca `double`.
- **Single-flight**: só uma sincronização por vez — a 2ª em andamento recebe `409`.

## API (base `/api/v1/properties`)

| Método | Endpoint | Response |
|---|---|---|
| GET | `/api/v1/properties` | 200 `PagedResultDto` (params: state, city, neighborhood, type, minPrice, maxPrice, minArea, maxArea, bedrooms, page, size) |
| GET | `/api/v1/properties/{id}` | 200 propriedade completa com todas as fontes (`sources`); 404 se não existir |
| POST | `/api/v1/properties/sync` | 202 `{status:enqueued}`; 409 se já estiver sincronizando (body opcional com filtros pré-armazenamento) |

Swagger UI: `/swagger-ui.html` · OpenAPI JSON: `/api-docs`.

## Comandos

```powershell
.\gradlew.bat build     # compila
.\gradlew.bat test      # roda todos os testes (~48)
.\gradlew.bat check     # check completo
.\gradlew.bat bootJar   # produz o jar executável
.\gradlew.bat bootRun   # sobe a aplicação
```

## Testes

- Perfil de teste usa H2 (`src/test/resources/application.properties`).
- Collectors são testados contra fixtures JSON dos portais (curl stub / `HttpServer`), cobrindo paginação completa, teto configurável de páginas, escopo geográfico e fallback de amostra.
- `PropertyControllerIntegrationTest` usa **standalone MockMvc** (mock de ports), pois o `spring-boot-test-autoconfigure` do Boot 4.x não expõe mais `@AutoConfigureMockMvc`.
- Para validar de verdade, suba a app e use `curl.exe --noproxy "*" http://127.0.0.1:8080/...`.

## Limitações conhecidas

- Scraping de portais terceiros pode ser bloqueado por anti-bot em momentos distintos (página 1 vazia → fallback para amostra). A coleta de todas as páginas gera volume grande de requests — monitore `app.collector.max-pages`.
- Filtro inválido (`minPrice > maxPrice`) e página excedente ainda retornam **200 vazio** (validação de 400 não implementada).

## Spec e plan

- Spec: `docs/superpowers/specs/2026-09-13-homehunter-unified-search-design.md`
- Plan: `docs/superpowers/plans/2026-09-13-homehunter-unified-search.md`