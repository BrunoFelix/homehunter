# HomeHunter — Instruções do Projeto

Backend de busca unificada de imóveis (Pernambuco) que coleta, consolida, deduplica e expõe anúncios de 4 portais (ZapImóveis, VivaReal, Chaves na Mão, ImovelWeb).

## Stack

- Java 26 (toolchain) / Gradle 9 (wrapper `gradlew.bat`)
- Spring Boot 4.1.1 (Web, Data JPA, Validation)
- Springdoc OpenAPI 2.8.5, JSoup 1.18.3, Lombok
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
- `core/application` — casos de uso (`SyncPropertiesUseCase`, `SearchPropertiesUseCase`), models neutros (`PagedResult`, `CollectionScope`, `SyncStatus`) e ports (`*InputPort`/`*OutputPort`).
- `dataprovider` — adapters dirigidos: `database` (JPA + Specification) e `collector` (JSoup + Anti-Corruption Layer `PortalPropertyParser`/`PortalPropertyNormalizer`).
- `entrypoint` — adaptadores dirigentes: `rest` (PropertyController + DTOs + mappers) e `cron` (PropertySyncScheduler).

Regra de dependência: dependências apontam sempre para dentro. Nada de Spring no `core`. Montagem de beans no Composition Root.

## Identidade e deduplicação

- `PropertyId` = SHA-256 determinístico de `STATE|CITY|NEIGHBORHOOD|TYPE|AREA|BEDROOMS` — PK no MySQL. Recolete o mesmo imóvel em outro portal e o agregador funde fontes (`tb_property_source`), sem duplicar `tb_property`.
- Single-flight: só uma sincronização por vez (409 se já estiver rodando).

## API (base `/api/v1/properties`)

| Método | Endpoint | Response |
|---|---|---|
| GET | `/api/v1/properties` | 200 `PagedResultDto` (params: state, city, neighborhood, type, minPrice, maxPrice, minArea, maxArea, bedrooms, page, size) |
| GET | `/api/v1/properties/{id}` | 200 propriedade completa; 404 se não existir |
| POST | `/api/v1/properties/sync` | 202 `{status:enqueued}`; 409 se já estiver sincronizando (body opcional com filtros pré-armazenamento) |

Swagger UI: `/swagger-ui.html` · OpenAPI JSON: `/api-docs`.

## Notas de testes

- Perfil de teste usa H2 (`src/test/resources/application.properties`).
- `PropertyControllerIntegrationTest` usa **standalone MockMvc** (mock de ports), pois o `spring-boot-test-autoconfigure` do Boot 4.x não expõe mais `@AutoConfigureMockMvc`.
- Validar endpoints de verdade: subir a app e usar `curl`/terminal. Cuidado com proxy: `curl.exe --noproxy "*" http://127.0.0.1:8080/...`.

## Limitações conhecidas

- **Scraping dos portais é bloqueado por anti-bot** (Zap/VivaReal/Chaves na Mão retornam 0 listagens; ImovelWeb responde 403). Os collectors caem em dados de amostra. Para produção: contramedidas anti-bot (User-Agent real, JS rendering, proxy).
- Filtro inválido (`minPrice > maxPrice`) e página excedente retornam **200 vazio** (o spec previa 400 — validação ainda não implementada).

## Spec e plan

- Spec: `docs/superpowers/specs/2026-09-13-homehunter-unified-search-design.md`
- Plan: `docs/superpowers/plans/2026-09-13-homehunter-unified-search.md`
- Regra do repositório: **toda alteração pedida também deve atualizar spec e plan**.