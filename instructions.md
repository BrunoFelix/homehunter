# HomeHunter — Instruções do Projeto

Backend de busca unificada de imóveis (Pernambuco) que coleta, consolida, deduplica e expõe anúncios de 3 portais (ZapImóveis, VivaReal, Chaves na Mão).

## Stack

- Java 26 (toolchain) / Gradle 9 (wrapper `gradlew.bat`)
- Spring Boot 4.1.1 (Web, Data JPA, Validation)
- Springdoc OpenAPI 2.8.5, JSoup 1.18.3, Lombok, Testcontainers
- MySQL 8 (via `docker-compose.yaml`), H2 em testes e fallback local
- JUnit 5 + Mockito

## Setup local

```powershell
docker compose up -d            # sobe MySQL 8 (db databaseHomehunter)
.\gradlew.bat bootJar           # builda jar
java -jar build\libs\brunofelix-0.0.1-SNAPSHOT.jar --server.port=8080
```

Credenciais do banco vêm do `docker-compose.yaml` e são espelhadas em `src/main/resources/application.properties`:
`databaseHomehunter` / `usernameHomehunter` / `passwordHomehunter`.

Fallback sem MySQL (H2 em memória) após o bootJar:

```powershell
java -jar build\libs\brunofelix-0.0.1-SNAPSHOT.jar --server.port=8080 `
  --spring.datasource.url=jdbc:h2:mem:livedb;DB_CLOSE_DELAY=-1 `
  --spring.datasource.driverClassName=org.h2.Driver `
  --spring.datasource.username=sa --spring.datasource.password= `
  --spring.jpa.hibernate.ddl-auto=create-drop `
  --spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect
```

## Comandos

```powershell
.\gradlew.bat build            # compila
.\gradlew.bat test             # roda todos os testes
.\gradlew.bat check            # check completo
.\gradlew.bat bootJar          # produz o jar executável
.\gradlew.bat bootRun          # sobe a aplicação
```

O shell do ambiente é PowerShell: **não use `&&`**; use `cmd1; if ($?) { cmd2 }` para encadear comandos dependentes.

## Arquitetura (Hexagonal + DDD)

- `core/domain` — agregados e Value Objects (Property, PropertyId, Price, Address...), zero dependências de framework.
- `core/application` — casos de uso (`SyncPropertiesUseCase`, `SearchPropertiesUseCase`, `GetPropertyUseCase`), models neutros (`PagedResult`, `CollectionScope`, `SyncStatus`) e ports (`*InputPort`/`*OutputPort`).
- `dataprovider` — adapters dirigidos: `database` (JPA + Specification) e `collector` (`GlueApiCollectorSupport` + `SystemCurlRunner` para Zap/VivaReal, JSoup para Chaves na Mão + Anti-Corruption Layer `PortalPropertyNormalizer`).
- `entrypoint` — adaptadores dirigentes: `rest` (PropertyController + DTOs + mappers) e `cron` (PropertySyncScheduler).

Regra de dependência: dependências apontam sempre para dentro. Nada de Spring no `core`. Montagem de beans no Composition Root (`CoreBeanConfiguration`, incl. `ExecutorService syncExecutor` com `destroyMethod="shutdown"`).

## Coleta

- **Paginação completa**: os collectors percorrem todas as páginas até o total declarado pela API (`search.totalCount` / `metadata.totalPages`) — não há mais cap fixo de 10 páginas. Ex.: PE/Recife no ZapImóveis ≈ 244 páginas × 30 itens (~7300 anúncios).
- Cada página loga em `info`: `loading page X/Y...` (a 1ª ainda sem total conhecido). Extras: HTTP não-200, estrutura inesperada e cap configurado logam `warn`.
- `app.collector.max-pages` (default `0`) = teto de segurança opcional; `>0` limita a coleta.
- Escopo geográfico (`CollectionScope` state/cities) é honrado pós-coleta via `CollectionScopeFilter` em **todos** os collectors.
- **Throttling anti-DDoS**: `app.collector.politeness-delay` (default `500ms`) aplica um delay entre **cada página** com jitter de ±30% (evita padrão periódico); a cada `app.collector.pause-every-pages` páginas (default `20`) ainda dorme `app.collector.pause-duration` (default `10s`) como freio macro.
- Fallback de amostra (1 imóvel) **somente** quando a coleta retorna 0 listagens ao vivo (anti-bot).

## Configuração dos collectors (`application.properties`)

| Propriedade | Default |
|---|---|
| `app.collector.zapimoveis.enabled` / `vivareal.enabled` / `chavesnamao.enabled` | `true` |
| `app.collector.max-pages` | `0` (coleta tudo) |
| `app.collector.scope.cities` | `RECIFE` |
| `app.collector.cron` | `0 0 3 * * *` |
| `app.collector.timeout` / `politeness-delay` | `30s` / `500ms` | Delay por página (±30% jitter) |
| `app.collector.pause-every-pages` | `20` | Pausa a cada N páginas (0 = desabilitado) |
| `app.collector.pause-duration` | `10s` | Duração da pausa entre lotes |

## Identidade, deduplicação e normalização

- `PropertyId` = SHA-256 determinístico de `STATE|CITY|NEIGHBORHOOD|TYPE|AREA|BEDROOMS` — PK no MySQL. Recoleto o mesmo imóvel em outro portal e o agregador funde fontes (`tb_property_source`), sem duplicar `tb_property`.
- `announced_at` normalizado para **UTC** e truncado à precisão de segundos no `PropertyDatabaseMapper` (consistência entre portais).
- Preço tratado como **dinheiro** (`BigDecimal`), nunca `double` (`parsePrice` com guarda de `NumberFormatException`).
- Single-flight: só uma sincronização por vez (409 se já estiver rodando).

## API (base `/api/v1/properties`)

| Método | Endpoint | Response |
|---|---|---|
| GET | `/api/v1/properties` | 200 `PagedResultDto` (params: state, city, neighborhood, type, minPrice, maxPrice, minArea, maxArea, bedrooms, page, size) |
| GET | `/api/v1/properties/{id}` | 200 propriedade completa; 404 se não existir |
| POST | `/api/v1/properties/sync` | 202 `{status:enqueued}`; 409 se já estiver sincronizando (body opcional com filtros pré-armazenamento) |

Swagger UI: `/swagger-ui.html` · OpenAPI JSON: `/api-docs`.

## Limitações conhecidas

- **Scraping de portais terceiros** funciona ao vivo hoje (Zap/VivaReal via glue-api, Chaves na Mão via XHR), mas **anti-bot pode zerar** a coleta em momentos distintos — nesse caso o collector loga e cai no fallback de amostra (1 imóvel). Coletar todas as páginas gera volume grande de requests; use `app.collector.max-pages` para conter.
- Filtro inválido (`minPrice > maxPrice`) e página excedente retornam **200 vazio** (o spec previa 400 — validação ainda não implementada).

## Testes

- Perfil de teste usa H2 (`src/test/resources/application.properties`); ~48 testes.
- Collectors testados com fixtures JSON reais (curl stub / `HttpServer`): paginação completa até `totalPages`, teto configurável (`maxPages`), escopo geográfico, fallback de amostra, parsing de moeda.
- `PropertyControllerIntegrationTest` usa **standalone MockMvc** (mock de ports), pois o `spring-boot-test-autoconfigure` do Boot 4.x não expõe mais `@AutoConfigureMockMvc`.

## Spec e plan

- Spec: `docs/superpowers/specs/2026-09-13-homehunter-unified-search-design.md`
- Plan: `docs/superpowers/plans/2026-09-13-homehunter-unified-search.md`
- Regra do repositório: **toda alteração pedida também deve atualizar spec e plan**.