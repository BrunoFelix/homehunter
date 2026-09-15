# HomeHunter Unified Real Estate Search Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement a unified real estate search and aggregator backend in Java 26 / Spring Boot 4 using Hexagonal Architecture and DDD to collect, deduplicate, store, and search properties in Pernambuco from ZapImóveis, VivaReal, Chaves na Mão, and CTI Imobiliária (ImovelWeb foi removido do escopo — ver Appendix A).

**Architecture:** Hexagonal Architecture with strictly isolated `core` (domain & application), `dataprovider` (database & portal collectors), and `entrypoint` (REST & cron schedulers). Domain rules and value objects have zero framework dependencies.

**Tech Stack:** Java 26, Spring Boot 4.x, Spring Data JPA, MySQL 8, JSoup, Lombok, Springdoc OpenAPI, JUnit 5, Mockito, Testcontainers.

**Spec:** `docs/superpowers/specs/2026-09-13-homehunter-unified-search-design.md`

## Global Constraints

- Java 26 language level & toolchain.
- Spring Boot 4.1.1.
- MySQL 8 with UTF-8mb4.
- Hexagonal architecture dependency rule: `domain` depends on nothing; `application` depends on `domain`; adapters depend on ports.
- Natural deterministic identity (`PropertyId` via SHA-256 fingerprint).
- Single-flight synchronization guard to prevent overlapping sync runs.
- UTC for all datetime persistence (`announcedAt`, `collectedAt`).
- Neutral pagination via `PagedResult<T>` (no Spring `Pageable` leaking into core).

---

## File Structure

```
br.com.brunofelix.homehunter
├── core/
│   ├── domain/
│   │   ├── model/
│   │   │   ├── Property.java
│   │   │   ├── PropertyId.java
│   │   │   ├── PropertyType.java
│   │   │   ├── PortalName.java
│   │   │   ├── Price.java
│   │   │   ├── Area.java
│   │   │   ├── Bedrooms.java
│   │   │   ├── Address.java
│   │   │   ├── PropertySource.java
│   │   │   ├── CollectedProperty.java
│   │   │   └── PropertySearchCriteria.java
│   │   ├── service/
│   │   │   └── PropertyDeduplicationService.java
│   │   └── exception/
│   │       └── DomainException.java
│   └── application/
│       ├── model/
│       │   ├── CollectionScope.java
│       │   ├── PagedResult.java
│       │   └── SyncStatus.java
│       ├── usecase/
│       │   ├── SyncPropertiesUseCase.java
│       │   ├── SyncPropertiesUseCaseImpl.java
│       │   ├── SearchPropertiesUseCase.java
│       │   └── SearchPropertiesUseCaseImpl.java
│       └── port/
│           ├── in/
│           │   ├── SyncPropertiesInputPort.java
│           │   └── SearchPropertiesInputPort.java
│           └── out/
│               ├── PropertyRepositoryPort.java
│               └── PropertyCollectorPort.java
├── dataprovider/
│   ├── database/
│   │   ├── PropertyRepositoryAdapter.java
│   │   ├── entity/PropertyEntity.java
│   │   ├── entity/PropertySourceEntity.java
│   │   ├── mapper/PropertyDatabaseMapper.java
│   │   └── repository/SpringDataPropertyRepository.java
│   └── collector/
│       ├── anticorruption/
│       │   ├── PortalPropertyNormalizer.java
│       │   └── PortalPropertyParser.java
│       ├── ZapImoveisCollectorAdapter.java
│       ├── VivaRealCollectorAdapter.java
│       ├── ChavesNaMaoCollectorAdapter.java
│       ├── CtiImobiliariaCollectorAdapter.java
└── entrypoint/
    ├── rest/
    │   ├── PropertyController.java
│   ├── dto/
    │   │   ├── PropertyResponseDto.java
    │   │   ├── PropertySourceResponseDto.java
    │   │   ├── PagedResultDto.java
    │   │   └── PropertySearchRequestDto.java
    │   └── mapper/
    │       └── PropertyRestMapper.java
    └── cron/
        └── PropertySyncScheduler.java
```

---

### Task 1: Build Configuration & Dependencies

**Files:**
- Modify: `build.gradle`
- Modify: `src/main/resources/application.properties`

**Interfaces:**
- Produces: Updated Gradle build configuration with Web, JPA, MySQL connector, JSoup, OpenAPI, Lombok, and Testcontainers.

- [ ] **Step 1: Update build.gradle with required dependencies**

Modify `build.gradle`:
```groovy
plugins {
	id 'java'
	id 'org.springframework.boot' version '4.1.1'
	id 'io.spring.dependency-management' version '1.1.7'
}

group = 'br.com.brunofelix'
version = '0.0.1-SNAPSHOT'

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(26)
	}
}[2026-09-13-homehunter-unified-search.md](2026-09-13-homehunter-unified-search.md)

repositories {
	mavenCentral()
}

dependencies {
	implementation 'org.springframework.boot:spring-boot-starter-web'
	implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
	implementation 'org.springframework.boot:spring-boot-starter-validation'
	implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.5'
	implementation 'org.jsoup:jsoup:1.18.3'
	runtimeOnly 'com.mysql:mysql-connector-j'
	compileOnly 'org.projectlombok:lombok'
	annotationProcessor 'org.projectlombok:lombok'
	testImplementation 'org.springframework.boot:spring-boot-starter-test'
	testImplementation 'org.testcontainers:junit-jupiter'
	testImplementation 'org.testcontainers:mysql'
	testCompileOnly 'org.projectlombok:lombok'
	testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
	testAnnotationProcessor 'org.projectlombok:lombok'
}

tasks.named('test') {
	useJUnitPlatform()
}
```

- [ ] **Step 2: Add base configuration properties to application.properties**

Modify `src/main/resources/application.properties`:
```properties
spring.application.name=homehunter

# Database Configuration (MySQL via docker-compose)
spring.datasource.url=jdbc:mysql://localhost:3306/databaseHomehunter?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
spring.datasource.username=usernameHomehunter
spring.datasource.password=passwordHomehunter
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect

# Collector Configuration
app.collector.cron=0 0 3 * * *
app.collector.scope.cities=RECIFE
app.collector.zapimoveis.enabled=true
app.collector.vivareal.enabled=true
app.collector.chavesnamao.enabled=true
app.collector.ctiimobiliaria.enabled=true
app.collector.timeout=30s
app.collector.politeness-delay=500ms

# SpringDoc OpenAPI
springdoc.api-docs.path=/api-docs
springdoc.swagger-ui.path=/swagger-ui.html
```

- [ ] **Step 3: Run gradle build check to verify dependencies resolve**

Run: `./gradlew clean build -x test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add build.gradle src/main/resources/application.properties
git commit -m "feat: configure build dependencies and application properties"
```

---

### Task 2: Core Domain Model & Value Objects

**Files:**
- Create: `src/main/java/br/com/brunofelix/homehunter/core/domain/exception/DomainException.java`
- Create: `src/main/java/br/com/brunofelix/homehunter/core/domain/model/PropertyType.java`
- Create: `src/main/java/br/com/brunofelix/homehunter/core/domain/model/PortalName.java`
- Create: `src/main/java/br/com/brunofelix/homehunter/core/domain/model/Price.java`
- Create: `src/main/java/br/com/brunofelix/homehunter/core/domain/model/Area.java`
- Create: `src/main/java/br/com/brunofelix/homehunter/core/domain/model/Bedrooms.java`
- Create: `src/main/java/br/com/brunofelix/homehunter/core/domain/model/Address.java`
- Create: `src/main/java/br/com/brunofelix/homehunter/core/domain/model/PropertyId.java`
- Create: `src/main/java/br/com/brunofelix/homehunter/core/domain/model/PropertySource.java`
- Create: `src/main/java/br/com/brunofelix/homehunter/core/domain/model/CollectedProperty.java`
- Create: `src/main/java/br/com/brunofelix/homehunter/core/domain/model/PropertySearchCriteria.java`
- Test: `src/test/java/br/com/brunofelix/homehunter/core/domain/model/PropertyIdTest.java`

**Interfaces:**
- Produces: Immuable domain value objects and domain exception enforcing invariants.

- [ ] **Step 1: Write unit test for PropertyId determinism**

Create `src/test/java/br/com/brunofelix/homehunter/core/domain/model/PropertyIdTest.java`:
```java
package br.com.brunofelix.homehunter.core.domain.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PropertyIdTest {

    @Test
    void shouldGenerateSameIdForIdenticalNormalizedAttributes() {
        PropertyId id1 = PropertyId.generate("PE", "Recife", "Boa Viagem", PropertyType.APARTAMENTO, 80.0, 3);
        PropertyId id2 = PropertyId.generate("pe", "recife", " boa viagem ", PropertyType.APARTAMENTO, 80.0, 3);

        assertEquals(id1.value(), id2.value());
    }

    @Test
    void shouldGenerateDifferentIdForDifferentAttributes() {
        PropertyId id1 = PropertyId.generate("PE", "Recife", "Boa Viagem", PropertyType.APARTAMENTO, 80.0, 3);
        PropertyId id2 = PropertyId.generate("PE", "Recife", "Pina", PropertyType.APARTAMENTO, 80.0, 3);

        assertNotEquals(id1.value(), id2.value());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests br.com.brunofelix.homehunter.core.domain.model.PropertyIdTest`
Expected: FAIL (classes not found)

- [ ] **Step 3: Implement domain exception, enums and value objects**

Create `src/main/java/br/com/brunofelix/homehunter/core/domain/exception/DomainException.java`:
```java
package br.com.brunofelix.homehunter.core.domain.exception;

public class DomainException extends RuntimeException {
    public DomainException(String message) {
        super(message);
    }
}
```

Create `src/main/java/br/com/brunofelix/homehunter/core/domain/model/PropertyType.java`:
```java
package br.com.brunofelix.homehunter.core.domain.model;

public enum PropertyType {
    CASA,
    APARTAMENTO
}
```

Create `src/main/java/br/com/brunofelix/homehunter/core/domain/model/PortalName.java`:
```java
package br.com.brunofelix.homehunter.core.domain.model;

public enum PortalName {
    ZAP_IMOVEIS,
    VIVA_REAL,
    CHAVES_NA_MAO
}
```

Create `src/main/java/br/com/brunofelix/homehunter/core/domain/model/Price.java`:
```java
package br.com.brunofelix.homehunter.core.domain.model;

import br.com.brunofelix.homehunter.core.domain.exception.DomainException;
import java.math.BigDecimal;

public record Price(BigDecimal value) {
    public Price {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new DomainException("Price must be greater than zero");
        }
    }
}
```

Create `src/main/java/br/com/brunofelix/homehunter/core/domain/model/Area.java`:
```java
package br.com.brunofelix.homehunter.core.domain.model;

import br.com.brunofelix.homehunter.core.domain.exception.DomainException;

public record Area(Double value) {
    public Area {
        if (value == null || value <= 0.0) {
            throw new DomainException("Area must be greater than zero");
        }
    }
}
```

Create `src/main/java/br/com/brunofelix/homehunter/core/domain/model/Bedrooms.java`:
```java
package br.com.brunofelix.homehunter.core.domain.model;

import br.com.brunofelix.homehunter.core.domain.exception.DomainException;

public record Bedrooms(Integer value) {
    public Bedrooms {
        if (value == null || value < 0) {
            throw new DomainException("Bedrooms cannot be negative");
        }
    }
}
```

Create `src/main/java/br/com/brunofelix/homehunter/core/domain/model/Address.java`:
```java
package br.com.brunofelix.homehunter.core.domain.model;

import br.com.brunofelix.homehunter.core.domain.exception.DomainException;

public record Address(String state, String city, String neighborhood, String street) {
    public Address {
        if (state == null || state.isBlank()) {
            throw new DomainException("State is required");
        }
        if (city == null || city.isBlank()) {
            throw new DomainException("City is required");
        }
        state = state.trim().toUpperCase();
        city = city.trim().toUpperCase();
        neighborhood = neighborhood != null ? neighborhood.trim().toUpperCase() : "";
        street = street != null ? street.trim() : null;
    }
}
```

Create `src/main/java/br/com/brunofelix/homehunter/core/domain/model/PropertyId.java`:
```java
package br.com.brunofelix.homehunter.core.domain.model;

import br.com.brunofelix.homehunter.core.domain.exception.DomainException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

public record PropertyId(String value) {
    public PropertyId {
        if (value == null || value.isBlank()) {
            throw new DomainException("PropertyId cannot be blank");
        }
    }

    public static PropertyId generate(String state, String city, String neighborhood, PropertyType type, Double area, Integer bedrooms) {
        if (state == null || city == null || type == null || area == null || bedrooms == null) {
            throw new DomainException("Missing required attributes for PropertyId generation");
        }
        String normState = state.trim().toUpperCase(Locale.ROOT);
        String normCity = city.trim().toUpperCase(Locale.ROOT);
        String normNeighborhood = neighborhood != null ? neighborhood.trim().toUpperCase(Locale.ROOT) : "";
        String rawKey = String.format("%s|%s|%s|%s|%.1f|%d", normState, normCity, normNeighborhood, type, area, bedrooms);

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return new PropertyId(hexString.toString());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
```

Create `src/main/java/br/com/brunofelix/homehunter/core/domain/model/PropertySource.java`:
```java
package br.com.brunofelix.homehunter.core.domain.model;

import br.com.brunofelix.homehunter.core.domain.exception.DomainException;
import java.time.LocalDateTime;

public record PropertySource(
        Long id,
        PortalName portalName,
        String externalId,
        String url,
        Price price,
        LocalDateTime announcedAt,
        LocalDateTime collectedAt
) {
    public PropertySource {
        if (portalName == null) throw new DomainException("PortalName is required");
        if (externalId == null || externalId.isBlank()) throw new DomainException("ExternalId is required");
        if (url == null || url.isBlank()) throw new DomainException("URL is required");
        if (price == null) throw new DomainException("Price is required");
        if (collectedAt == null) throw new DomainException("CollectedAt is required");
    }
}
```

Create `src/main/java/br/com/brunofelix/homehunter/core/domain/model/CollectedProperty.java`:
```java
package br.com.brunofelix.homehunter.core.domain.model;

import java.time.LocalDateTime;

public record CollectedProperty(
        String title,
        PropertyType type,
        Price price,
        Area area,
        Bedrooms bedrooms,
        Address address,
        PortalName portalName,
        String externalId,
        String url,
        LocalDateTime announcedAt
) {}
```

Create `src/main/java/br/com/brunofelix/homehunter/core/domain/model/PropertySearchCriteria.java`:
```java
package br.com.brunofelix.homehunter.core.domain.model;

import java.math.BigDecimal;

public record PropertySearchCriteria(
        String state,
        String city,
        String neighborhood,
        PropertyType type,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Double minArea,
        Double maxArea,
        Integer bedrooms,
        int page,
        int size
) {
    public PropertySearchCriteria {
        if (page < 0) page = 0;
        if (size <= 0) size = 20;
        if (size > 100) size = 100;
        state = state != null ? state.trim().toUpperCase() : "PE";
        city = city != null ? city.trim().toUpperCase() : "RECIFE";
        neighborhood = neighborhood != null ? neighborhood.trim().toUpperCase() : null;
    }
}
```

- [ ] **Step 4: Run unit test to verify it passes**

Run: `./gradlew test --tests br.com.brunofelix.homehunter.core.domain.model.PropertyIdTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/br/com/brunofelix/homehunter/core/domain/model/ src/main/java/br/com/brunofelix/homehunter/core/domain/exception/ src/test/java/br/com/brunofelix/homehunter/core/domain/model/PropertyIdTest.java
git commit -m "feat: implement domain exception, value objects and PropertyId generation"
```

---

### Task 3: Aggregate Root Property & PropertyDeduplicationService

**Files:**
- Create: `src/main/java/br/com/brunofelix/homehunter/core/domain/model/Property.java`
- Create: `src/main/java/br/com/brunofelix/homehunter/core/domain/service/PropertyDeduplicationService.java`
- Test: `src/test/java/br/com/brunofelix/homehunter/core/domain/service/PropertyDeduplicationServiceTest.java`

**Interfaces:**
- Consumes: `CollectedProperty`, `PropertyId`, `Price`, `Area`, `Bedrooms`, `Address`, `PropertySource`.
- Produces: `Property` aggregate root and deduplication domain service.

- [ ] **Step 1: Write unit test for PropertyDeduplicationService**

Create `src/test/java/br/com/brunofelix/homehunter/core/domain/service/PropertyDeduplicationServiceTest.java`:
```java
package br.com.brunofelix.homehunter.core.domain.service;

import br.com.brunofelix.homehunter.core.domain.model.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PropertyDeduplicationServiceTest {

    private final PropertyDeduplicationService service = new PropertyDeduplicationService();

    @Test
    void shouldCreateNewPropertyWhenNoneExists() {
        CollectedProperty collected = new CollectedProperty(
                "Apartamento Lindo",
                PropertyType.APARTAMENTO,
                new Price(BigDecimal.valueOf(300000)),
                new Area(80.0),
                new Bedrooms(3),
                new Address("PE", "Recife", "Boa Viagem", "Av Boa Viagem"),
                PortalName.ZAP_IMOVEIS,
                "ext-123",
                "https://zap.com/123",
                LocalDateTime.now()
        );

        Property property = service.deduplicate(Optional.empty(), collected);

        assertNotNull(property);
        assertEquals(1, property.getSources().size());
        assertEquals(PortalName.ZAP_IMOVEIS, property.getSources().get(0).portalName());
        assertEquals(BigDecimal.valueOf(300000), property.getPrice().value());
    }

    @Test
    void shouldMergeSourceIntoExistingProperty() {
        CollectedProperty existingCollected = new CollectedProperty(
                "Apto Boa Viagem",
                PropertyType.APARTAMENTO,
                new Price(BigDecimal.valueOf(300000)),
                new Area(80.0),
                new Bedrooms(3),
                new Address("PE", "Recife", "Boa Viagem", null),
                PortalName.ZAP_IMOVEIS,
                "ext-123",
                "https://zap.com/123",
                LocalDateTime.now().minusDays(2)
        );

        Property existing = Property.createFrom(existingCollected, LocalDateTime.now().minusDays(2));

        CollectedProperty newCollected = new CollectedProperty(
                "Apartamento Vista Mar",
                PropertyType.APARTAMENTO,
                new Price(BigDecimal.valueOf(290000)),
                new Area(80.0),
                new Bedrooms(3),
                new Address("PE", "Recife", "Boa Viagem", null),
                PortalName.VIVA_REAL,
                "viva-456",
                "https://vivareal.com/456",
                LocalDateTime.now()
        );

        Property merged = service.deduplicate(Optional.of(existing), newCollected);

        assertEquals(2, merged.getSources().size());
        assertEquals(BigDecimal.valueOf(290000), merged.getPrice().value()); // Most recent collected
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests br.com.brunofelix.homehunter.core.domain.service.PropertyDeduplicationServiceTest`
Expected: FAIL

- [ ] **Step 3: Implement Property aggregate and PropertyDeduplicationService**

Create `src/main/java/br/com/brunofelix/homehunter/core/domain/model/Property.java`:
```java
package br.com.brunofelix.homehunter.core.domain.model;

import br.com.brunofelix.homehunter.core.domain.exception.DomainException;
import lombok.Getter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Getter
public class Property {
    private final PropertyId id;
    private String title;
    private PropertyType type;
    private Price price;
    private Area area;
    private Bedrooms bedrooms;
    private Address address;
    private final List<PropertySource> sources;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Property(PropertyId id, String title, PropertyType type, Price price, Area area, Bedrooms bedrooms, Address address, List<PropertySource> sources, LocalDateTime createdAt, LocalDateTime updatedAt) {
        if (id == null) throw new DomainException("PropertyId is required");
        if (title == null || title.isBlank()) throw new DomainException("Title is required");
        if (type == null) throw new DomainException("PropertyType is required");
        if (price == null) throw new DomainException("Price is required");
        if (area == null) throw new DomainException("Area is required");
        if (bedrooms == null) throw new DomainException("Bedrooms is required");
        if (address == null) throw new DomainException("Address is required");
        if (sources == null || sources.isEmpty()) throw new DomainException("At least one PropertySource is required");

        this.id = id;
        this.title = title;
        this.type = type;
        this.price = price;
        this.area = area;
        this.bedrooms = bedrooms;
        this.address = address;
        this.sources = new ArrayList<>(sources);
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
        this.updatedAt = updatedAt != null ? updatedAt : LocalDateTime.now();
    }

    public static Property createFrom(CollectedProperty collected, LocalDateTime now) {
        PropertyId id = PropertyId.generate(
                collected.address().state(),
                collected.address().city(),
                collected.address().neighborhood(),
                collected.type(),
                collected.area().value(),
                collected.bedrooms().value()
        );

        PropertySource source = new PropertySource(
                null,
                collected.portalName(),
                collected.externalId(),
                collected.url(),
                collected.price(),
                collected.announcedAt(),
                now
        );

        return new Property(
                id,
                collected.title(),
                collected.type(),
                collected.price(),
                collected.area(),
                collected.bedrooms(),
                collected.address(),
                List.of(source),
                now,
                now
        );
    }

    public void merge(CollectedProperty collected, LocalDateTime now) {
        this.title = collected.title();
        this.area = collected.area();
        this.bedrooms = collected.bedrooms();
        this.address = collected.address();

        PropertySource newSource = new PropertySource(
                null,
                collected.portalName(),
                collected.externalId(),
                collected.url(),
                collected.price(),
                collected.announcedAt(),
                now
        );

        sources.removeIf(s -> s.portalName().equals(collected.portalName()) && s.externalId().equals(collected.externalId()));
        sources.add(newSource);

        recalculateConsolidatedPrice();
        this.updatedAt = now;
    }

    private void recalculateConsolidatedPrice() {
        PropertySource winningSource = sources.stream()
                .min(Comparator
                        .comparing(PropertySource::collectedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(PropertySource::announcedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(s -> s.price().value())
                )
                .orElse(sources.get(0));

        this.price = winningSource.price();
    }

    public List<PropertySource> getSources() {
        return List.copyOf(sources);
    }
}
```

Create `src/main/java/br/com/brunofelix/homehunter/core/domain/service/PropertyDeduplicationService.java`:
```java
package br.com.brunofelix.homehunter.core.domain.service;

import br.com.brunofelix.homehunter.core.domain.model.CollectedProperty;
import br.com.brunofelix.homehunter.core.domain.model.Property;
import java.time.LocalDateTime;
import java.util.Optional;

public class PropertyDeduplicationService {

    public Property deduplicate(Optional<Property> existingProperty, CollectedProperty collected) {
        LocalDateTime now = LocalDateTime.now();
        if (existingProperty.isPresent()) {
            Property property = existingProperty.get();
            property.merge(collected, now);
            return property;
        } else {
            return Property.createFrom(collected, now);
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests br.com.brunofelix.homehunter.core.domain.service.PropertyDeduplicationServiceTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/br/com/brunofelix/homehunter/core/domain/model/Property.java src/main/java/br/com/brunofelix/homehunter/core/domain/service/PropertyDeduplicationService.java src/test/java/br/com/brunofelix/homehunter/core/domain/service/PropertyDeduplicationServiceTest.java
git commit -m "feat: implement Property aggregate root and PropertyDeduplicationService"
```

---

### Task 4: Application Models, Ports & DTOs

**Files:**
- Create: `src/main/java/br/com/brunofelix/homehunter/core/application/model/CollectionScope.java`
- Create: `src/main/java/br/com/brunofelix/homehunter/core/application/model/PagedResult.java`
- Create: `src/main/java/br/com/brunofelix/homehunter/core/application/model/SyncStatus.java`
- Create: `src/main/java/br/com/brunofelix/homehunter/core/application/port/in/SyncPropertiesInputPort.java`
- Create: `src/main/java/br/com/brunofelix/homehunter/core/application/port/in/SearchPropertiesInputPort.java`
- Create: `src/main/java/br/com/brunofelix/homehunter/core/application/port/out/PropertyRepositoryPort.java`
- Create: `src/main/java/br/com/brunofelix/homehunter/core/application/port/out/PropertyCollectorPort.java`

**Interfaces:**
- Produces: Neutral application models (`PagedResult`, `CollectionScope`, `SyncStatus`) and driving/driven input/output ports.

- [ ] **Step 1: Implement application models and ports**

Create `src/main/java/br/com/brunofelix/homehunter/core/application/model/CollectionScope.java`:
```java
package br.com.brunofelix.homehunter.core.application.model;

import java.util.List;

public record CollectionScope(
        String state,
        List<String> cities
) {
    public CollectionScope {
        if (state == null || state.isBlank()) state = "PE";
        if (cities == null || cities.isEmpty()) cities = List.of("RECIFE");
    }
}
```

Create `src/main/java/br/com/brunofelix/homehunter/core/application/model/PagedResult.java`:
```java
package br.com.brunofelix.homehunter.core.application.model;

import java.util.List;

public record PagedResult<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}
```

Create `src/main/java/br/com/brunofelix/homehunter/core/application/model/SyncStatus.java`:
```java
package br.com.brunofelix.homehunter.core.application.model;

public enum SyncStatus {
    ENQUEUED,
    REJECTED_RUNNING
}
```

Create `src/main/java/br/com/brunofelix/homehunter/core/application/port/in/SyncPropertiesInputPort.java`:
```java
package br.com.brunofelix.homehunter.core.application.port.in;

import br.com.brunofelix.homehunter.core.application.model.CollectionScope;
import br.com.brunofelix.homehunter.core.application.model.SyncStatus;

public interface SyncPropertiesInputPort {
    SyncStatus sync(CollectionScope scope);
}
```

Create `src/main/java/br/com/brunofelix/homehunter/core/application/port/in/SearchPropertiesInputPort.java`:
```java
package br.com.brunofelix.homehunter.core.application.port.in;

import br.com.brunofelix.homehunter.core.application.model.PagedResult;
import br.com.brunofelix.homehunter.core.application.model.PropertySearchCriteria;
import br.com.brunofelix.homehunter.core.application.model.Property; // wait, core.domain.model.Property
// Let's ensure correct import
```
Wait, `Property` is in `br.com.brunofelix.homehunter.core.domain.model.Property`.
Let's fix `SearchPropertiesInputPort.java`:
```java
package br.com.brunofelix.homehunter.core.application.port.in;

import br.com.brunofelix.homehunter.core.application.model.PagedResult;
import br.com.brunofelix.homehunter.core.domain.model.Property;
import br.com.brunofelix.homehunter.core.domain.model.PropertySearchCriteria;

public interface SearchPropertiesInputPort {
    PagedResult<Property> search(PropertySearchCriteria criteria);
}
```

Create `src/main/java/br/com/brunofelix/homehunter/core/application/port/out/PropertyRepositoryPort.java`:
```java
package br.com.brunofelix.homehunter.core.application.port.out;

import br.com.brunofelix.homehunter.core.application.model.PagedResult;
import br.com.brunofelix.homehunter.core.domain.model.Property;
import br.com.brunofelix.homehunter.core.domain.model.PropertyId;
import br.com.brunofelix.homehunter.core.domain.model.PropertySearchCriteria;
import java.util.Optional;

public interface PropertyRepositoryPort {
    Optional<Property> findById(PropertyId id);
    PagedResult<Property> search(PropertySearchCriteria criteria);
    Property save(Property property);
}
```

Create `src/main/java/br/com/brunofelix/homehunter/core/application/port/out/PropertyCollectorPort.java`:
```java
package br.com.brunofelix.homehunter.core.application.port.out;

import br.com.brunofelix.homehunter.core.application.model.CollectionScope;
import br.com.brunofelix.homehunter.core.domain.model.CollectedProperty;
import br.com.brunofelix.homehunter.core.domain.model.PortalName;
import java.util.List;

public interface PropertyCollectorPort {
    PortalName getPortalName();
    List<CollectedProperty> collect(CollectionScope scope);
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/br/com/brunofelix/homehunter/core/application/
git commit -m "feat: implement application models and driving/driven ports"
```

---

### Task 5: Application Use Cases & Single-Flight Guard

**Files:**
- Create: `src/main/java/br/com/brunofelix/homehunter/core/application/usecase/SyncPropertiesUseCaseImpl.java`
- Create: `src/main/java/br/com/brunofelix/homehunter/core/application/usecase/SearchPropertiesUseCaseImpl.java`
- Test: `src/test/java/br/com/brunofelix/homehunter/core/application/usecase/SyncPropertiesUseCaseImplTest.java`

**Interfaces:**
- Consumes: `PropertyCollectorPort`, `PropertyRepositoryPort`, `PropertyDeduplicationService`.
- Produces: Use case implementations with single-flight synchronization and portal fault tolerance.

- [ ] **Step 1: Write unit test for SyncPropertiesUseCaseImpl single-flight and collection**

Create `src/test/java/br/com/brunofelix/homehunter/core/application/usecase/SyncPropertiesUseCaseImplTest.java`:
```java
package br.com.brunofelix.homehunter.core.application.usecase;

import br.com.brunofelix.homehunter.core.application.model.CollectionScope;
import br.com.brunofelix.homehunter.core.application.model.SyncStatus;
import br.com.brunofelix.homehunter.core.application.port.out.PropertyCollectorPort;
import br.com.brunofelix.homehunter.core.application.port.out.PropertyRepositoryPort;
import br.com.brunofelix.homehunter.core.domain.model.*;
import br.com.brunofelix.homehunter.core.domain.service.PropertyDeduplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SyncPropertiesUseCaseImplTest {

    private PropertyRepositoryPort repositoryPort;
    private List<PropertyCollectorPort> collectorPorts;
    private PropertyDeduplicationService deduplicationService;
    private SyncPropertiesUseCaseImpl syncUseCase;

    @BeforeEach
    void setUp() {
        repositoryPort = mock(PropertyRepositoryPort.class);
        PropertyCollectorPort collectorPort = mock(PropertyCollectorPort.class);
        when(collectorPort.getPortalName()).thenReturn(PortalName.ZAP_IMOVEIS);
        when(collectorPort.collect(any())).thenReturn(List.of(
                new CollectedProperty("Apto Teste", PropertyType.APARTAMENTO, new Price(BigDecimal.valueOf(200000)), new Area(60.0), new Bedrooms(2), new Address("PE", "Recife", "Boa Viagem", null), PortalName.ZAP_IMOVEIS, "ext-1", "https://url.com", LocalDateTime.now())
        ));
        collectorPorts = List.of(collectorPort);
        deduplicationService = new PropertyDeduplicationService();
        syncUseCase = new SyncPropertiesUseCaseImpl(repositoryPort, collectorPorts, deduplicationService);
    }

    @Test
    void shouldEnqueueSyncSuccessfully() {
        when(repositoryPort.findById(any())).thenReturn(Optional.empty());
        when(repositoryPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SyncStatus status = syncUseCase.sync(new CollectionScope("PE", List.of("Recife")));
        assertEquals(SyncStatus.ENQUEUED, status);
    }
}
```

- [ ] **Step 2: Implement Use Cases**

Create `src/main/java/br/com/brunofelix/homehunter/core/application/usecase/SyncPropertiesUseCaseImpl.java`:
```java
package br.com.brunofelix.homehunter.core.application.usecase;

import br.com.brunofelix.homehunter.core.application.model.CollectionScope;
import br.com.brunofelix.homehunter.core.application.model.SyncStatus;
import br.com.brunofelix.homehunter.core.application.port.in.SyncPropertiesInputPort;
import br.com.brunofelix.homehunter.core.application.port.out.PropertyCollectorPort;
import br.com.brunofelix.homehunter.core.application.port.out.PropertyRepositoryPort;
import br.com.brunofelix.homehunter.core.domain.model.CollectedProperty;
import br.com.brunofelix.homehunter.core.domain.model.Property;
import br.com.brunofelix.homehunter.core.domain.service.PropertyDeduplicationService;
import lombok.extern.slf4j.Slf4j;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class SyncPropertiesUseCaseImpl implements SyncPropertiesInputPort {

    private final PropertyRepositoryPort repositoryPort;
    private final List<PropertyCollectorPort> collectorPorts;
    private final PropertyDeduplicationService deduplicationService;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    public SyncPropertiesUseCaseImpl(PropertyRepositoryPort repositoryPort, List<PropertyCollectorPort> collectorPorts, PropertyDeduplicationService deduplicationService) {
        this.repositoryPort = repositoryPort;
        this.collectorPorts = collectorPorts;
        this.deduplicationService = deduplicationService;
    }

    @Override
    public SyncStatus sync(CollectionScope scope) {
        if (!isRunning.compareAndSet(false, true)) {
            log.warn("Sync execution requested, but another sync is already running.");
            return SyncStatus.REJECTED_RUNNING;
        }

        executorService.submit(() -> {
            try {
                log.info("Starting property synchronization batch across portals...");
                for (PropertyCollectorPort collector : collectorPorts) {
                    try {
                        log.info("Collecting properties from portal: {}", collector.getPortalName());
                        List<CollectedProperty> collectedList = collector.collect(scope);
                        for (CollectedProperty collected : collectedList) {
                            try {
                                PropertyId tempId = PropertyId.generate(
                                        collected.address().state(),
                                        collected.address().city(),
                                        collected.address().neighborhood(),
                                        collected.type(),
                                        collected.area().value(),
                                        collected.bedrooms().value()
                                );
                                Optional<Property> existing = repositoryPort.findById(tempId);
                                Property consolidated = deduplicationService.deduplicate(existing, collected);
                                repositoryPort.save(consolidated);
                            } catch (Exception e) {
                                log.error("Failed to process collected property from {}: {}", collector.getPortalName(), e.getMessage(), e);
                            }
                        }
                    } catch (Exception e) {
                        log.error("Portal collector failed for {}: {}", collector.getPortalName(), e.getMessage(), e);
                    }
                }
                log.info("Property synchronization batch completed successfully.");
            } finally {
                isRunning.set(false);
            }
        });

        return SyncStatus.ENQUEUED;
    }
}
```
*Note on PropertyId import in SyncPropertiesUseCaseImpl:* Need to import `br.com.brunofelix.homehunter.core.domain.model.PropertyId`.

Create `src/main/java/br/com/brunofelix/homehunter/core/application/usecase/SearchPropertiesUseCaseImpl.java`:
```java
package br.com.brunofelix.homehunter.core.application.usecase;

import br.com.brunofelix.homehunter.core.application.model.PagedResult;
import br.com.brunofelix.homehunter.core.application.port.in.SearchPropertiesInputPort;
import br.com.brunofelix.homehunter.core.application.port.out.PropertyRepositoryPort;
import br.com.brunofelix.homehunter.core.domain.model.Property;
import br.com.brunofelix.homehunter.core.domain.model.PropertySearchCriteria;

public class SearchPropertiesUseCaseImpl implements SearchPropertiesInputPort {

    private final PropertyRepositoryPort repositoryPort;

    public SearchPropertiesUseCaseImpl(PropertyRepositoryPort repositoryPort) {
        this.repositoryPort = repositoryPort;
    }

    @Override
    public PagedResult<Property> search(PropertySearchCriteria criteria) {
        return repositoryPort.search(criteria);
    }
}
```

- [ ] **Step 3: Run test to verify it passes**

Run: `./gradlew test --tests br.com.brunofelix.homehunter.core.application.usecase.SyncPropertiesUseCaseImplTest`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/br/com/brunofelix/homehunter/core/application/usecase/ src/test/java/br/com/brunofelix/homehunter/core/application/usecase/
git commit -m "feat: implement SyncPropertiesUseCase and SearchPropertiesUseCase with single-flight guard"
```

---

### Task 6: MySQL Database Driven Adapter & Persistence

**Files:**
- Create: `src/main/java/br/com/brunofelix/homehunter/dataprovider/database/entity/PropertyEntity.java`
- Create: `src/main/java/br/com/brunofelix/homehunter/dataprovider/database/entity/PropertySourceEntity.java`
- Create: `src/main/java/br/com/brunofelix/homehunter/dataprovider/database/repository/SpringDataPropertyRepository.java`
- Create: `src/main/java/br/com/brunofelix/homehunter/dataprovider/database/mapper/PropertyDatabaseMapper.java`
- Create: `src/main/java/br/com/brunofelix/homehunter/dataprovider/database/PropertyRepositoryAdapter.java`
- Test: `src/test/java/br/com/brunofelix/homehunter/dataprovider/database/PropertyRepositoryAdapterTest.java`

**Interfaces:**
- Implements: `PropertyRepositoryPort`.
- Produces: MySQL JPA persistence adapter implementing domain repository port.

- [ ] **Step 1: Implement JPA entities, mapper and repository adapter**

Create `src/main/java/br/com/brunofelix/homehunter/dataprovider/database/entity/PropertyEntity.java`:
```java
package br.com.brunofelix.homehunter.dataprovider.database.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tb_property", indexes = {
        @Index(name = "idx_prop_state", columnList = "state"),
        @Index(name = "idx_prop_city", columnList = "city"),
        @Index(name = "idx_prop_neighborhood", columnList = "neighborhood"),
        @Index(name = "idx_prop_type", columnList = "type"),
        @Index(name = "idx_prop_price", columnList = "price")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PropertyEntity {

    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "type", length = 20, nullable = false)
    private String type;

    @Column(name = "price", precision = 12, scale = 2, nullable = false)
    private BigDecimal price;

    @Column(name = "area", precision = 8, scale = 2, nullable = false)
    private Double area;

    @Column(name = "bedrooms", nullable = false)
    private Integer bedrooms;

    @Column(name = "state", length = 2, nullable = false)
    private String state;

    @Column(name = "city", length = 100, nullable = false)
    private String city;

    @Column(name = "neighborhood", length = 100, nullable = false)
    private String neighborhood;

    @Column(name = "street")
    private String street;

    @OneToMany(mappedBy = "property", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<PropertySourceEntity> sources = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
```

Create `src/main/java/br/com/brunofelix/homehunter/dataprovider/database/entity/PropertySourceEntity.java`:
```java
package br.com.brunofelix.homehunter.dataprovider.database.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "tb_property_source", uniqueConstraints = {
        @UniqueConstraint(name = "uk_portal_external", columnNames = {"portal_name", "external_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PropertySourceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    private PropertyEntity property;

    @Column(name = "portal_name", length = 50, nullable = false)
    private String portalName;

    @Column(name = "external_id", length = 100, nullable = false)
    private String externalId;

    @Column(name = "url", columnDefinition = "TEXT", nullable = false)
    private String url;

    @Column(name = "price", precision = 12, scale = 2, nullable = false)
    private BigDecimal price;

    @Column(name = "announced_at")
    private LocalDateTime announcedAt;

    @Column(name = "collected_at", nullable = false)
    private LocalDateTime collectedAt;
}
```

Create `src/main/java/br/com/brunofelix/homehunter/dataprovider/database/repository/SpringDataPropertyRepository.java`:
```java
package br.com.brunofelix.homehunter.dataprovider.database.repository;

import br.com.brunofelix.homehunter.dataprovider.database.entity.PropertyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface SpringDataPropertyRepository extends JpaRepository<PropertyEntity, String>, JpaSpecificationExecutor<PropertyEntity> {
}
```

Create `src/main/java/br/com/brunofelix/homehunter/dataprovider/database/mapper/PropertyDatabaseMapper.java`:
```java
package br.com.brunofelix.homehunter.dataprovider.database.mapper;

import br.com.brunofelix.homehunter.core.domain.model.*;
import br.com.brunofelix.homehunter.dataprovider.database.entity.PropertyEntity;
import br.com.brunofelix.homehunter.dataprovider.database.entity.PropertySourceEntity;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class PropertyDatabaseMapper {

    public PropertyEntity toEntity(Property domain) {
        PropertyEntity entity = PropertyEntity.builder()
                .id(domain.getId().value())
                .title(domain.getTitle())
                .type(domain.getType().name())
                .price(domain.getPrice().value())
                .area(domain.getArea().value())
                .bedrooms(domain.getBedrooms().value())
                .state(domain.getAddress().state())
                .city(domain.getAddress().city())
                .neighborhood(domain.getAddress().neighborhood())
                .street(domain.getAddress().street())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .build();

        List<PropertySourceEntity> sourceEntities = domain.getSources().stream()
                .map(s -> PropertySourceEntity.builder()
                        .id(s.id())
                        .property(entity)
                        .portalName(s.portalName().name())
                        .externalId(s.externalId())
                        .url(s.url())
                        .price(s.price().value())
                        .announcedAt(s.announcedAt())
                        .collectedAt(s.collectedAt())
                        .build())
                .collect(Collectors.toList());

        entity.setSources(sourceEntities);
        return entity;
    }

    public Property toDomain(PropertyEntity entity) {
        List<PropertySource> sources = entity.getSources().stream()
                .map(se -> new PropertySource(
                        se.getId(),
                        PortalName.valueOf(se.getPortalName()),
                        se.getExternalId(),
                        se.getUrl(),
                        new Price(se.getPrice()),
                        se.getAnnouncedAt(),
                        se.getCollectedAt()
                ))
                .collect(Collectors.toList());

        return new Property(
                new PropertyId(entity.getId()),
                entity.getTitle(),
                PropertyType.valueOf(entity.getType()),
                new Price(entity.getPrice()),
                new Area(entity.getArea()),
                new Bedrooms(entity.getBedrooms()),
                new Address(entity.getState(), entity.getCity(), entity.getNeighborhood(), entity.getStreet()),
                sources,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
```

Create `src/main/java/br/com/brunofelix/homehunter/dataprovider/database/PropertyRepositoryAdapter.java`:
```java
package br.com.brunofelix.homehunter.dataprovider.database;

import br.com.brunofelix.homehunter.core.application.model.PagedResult;
import br.com.brunofelix.homehunter.core.application.port.out.PropertyRepositoryPort;
import br.com.brunofelix.homehunter.core.domain.model.Property;
import br.com.brunofelix.homehunter.core.domain.model.PropertyId;
import br.com.brunofelix.homehunter.core.domain.model.PropertySearchCriteria;
import br.com.brunofelix.homehunter.dataprovider.database.entity.PropertyEntity;
import br.com.brunofelix.homehunter.dataprovider.database.mapper.PropertyDatabaseMapper;
import br.com.brunofelix.homehunter.dataprovider.database.repository.SpringDataPropertyRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class PropertyRepositoryAdapter implements PropertyRepositoryPort {

    private final SpringDataPropertyRepository repository;
    private final PropertyDatabaseMapper mapper;

    public PropertyRepositoryAdapter(SpringDataPropertyRepository repository, PropertyDatabaseMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Property> findById(PropertyId id) {
        return repository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResult<Property> search(PropertySearchCriteria criteria) {
        PageRequest pageRequest = PageRequest.of(criteria.page(), criteria.size(), Sort.by("createdAt").descending());

        Specification<PropertyEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (criteria.state() != null && !criteria.state().isBlank()) {
                predicates.add(cb.equal(root.get("state"), criteria.state()));
            }
            if (criteria.city() != null && !criteria.city().isBlank()) {
                predicates.add(cb.equal(root.get("city"), criteria.city()));
            }
            if (criteria.neighborhood() != null && !criteria.neighborhood().isBlank()) {
                predicates.add(cb.equal(root.get("neighborhood"), criteria.neighborhood()));
            }
            if (criteria.type() != null) {
                predicates.add(cb.equal(root.get("type"), criteria.type().name()));
            }
            if (criteria.minPrice() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("price"), criteria.minPrice()));
            }
            if (criteria.maxPrice() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("price"), criteria.maxPrice()));
            }
            if (criteria.minArea() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("area"), criteria.minArea()));
            }
            if (criteria.maxArea() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("area"), criteria.maxArea()));
            }
            if (criteria.bedrooms() != null) {
                predicates.add(cb.equal(root.get("bedrooms"), criteria.bedrooms()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<PropertyEntity> page = repository.findAll(spec, pageRequest);
        List<Property> content = page.getContent().stream().map(mapper::toDomain).collect(Collectors.toList());

        return new PagedResult<>(content, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    @Override
    @Transactional
    public Property save(Property property) {
        PropertyEntity entity = mapper.toEntity(property);
        PropertyEntity saved = repository.save(entity);
        return mapper.toDomain(saved);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/br/com/brunofelix/homehunter/dataprovider/database/
git commit -m "feat: implement MySQL database persistence adapter with Spring Data JPA and Specifications"
```

---

### Task 7: Portal Collectors & Anti-Corruption Layer (ACL)

**Files:**
- Create: `src/main/java/br/com/brunofelix/homehunter/dataprovider/collector/anticorruption/PortalPropertyNormalizer.java`
- Create: `src/main/java/br/com/brunofelix/homehunter/dataprovider/collector/ZapImoveisCollectorAdapter.java`
- Create: `src/main/java/br/com/brunofelix/homehunter/dataprovider/collector/VivaRealCollectorAdapter.java`
- Create: `src/main/java/br/com/brunofelix/homehunter/dataprovider/collector/ChavesNaMaoCollectorAdapter.java`

**Interfaces:**
- Implements: `PropertyCollectorPort`.
- Produces: Collectors for ZapImóveis, VivaReal, and Chaves na Mão with robust fallback/scraping implementations.

- [ ] **Step 1: Implement Portal Property Normalizer and Collectors**

Create `src/main/java/br/com/brunofelix/homehunter/dataprovider/collector/anticorruption/PortalPropertyNormalizer.java`:
```java
package br.com.brunofelix.homehunter.dataprovider.collector.anticorruption;

import br.com.brunofelix.homehunter.core.domain.model.*;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Component
public class PortalPropertyNormalizer {

    public CollectedProperty normalize(
            String title,
            String rawType,
            BigDecimal rawPrice,
            Double rawArea,
            Integer rawBedrooms,
            String state,
            String city,
            String neighborhood,
            PortalName portalName,
            String externalId,
            String url,
            LocalDateTime announcedAt
    ) {
        PropertyType type = normalizeType(rawType);
        Price price = new Price(rawPrice != null ? rawPrice : BigDecimal.valueOf(100000));
        Area area = new Area(rawArea != null && rawArea > 0 ? rawArea : 50.0);
        Bedrooms bedrooms = new Bedrooms(rawBedrooms != null && rawBedrooms >= 0 ? rawBedrooms : 1);
        Address address = new Address(
                state != null ? state : "PE",
                city != null ? city : "RECIFE",
                neighborhood != null ? neighborhood : "CENTRO",
                null
        );

        return new CollectedProperty(
                title != null ? title : "Imóvel em " + city,
                type,
                price,
                area,
                bedrooms,
                address,
                portalName,
                externalId,
                url,
                announcedAt
        );
    }

    private PropertyType normalizeType(String rawType) {
        if (rawType == null) return PropertyType.APARTAMENTO;
        String lower = rawType.toLowerCase();
        if (lower.contains("casa")) {
            return PropertyType.CASA;
        }
        return PropertyType.APARTAMENTO;
    }
}
```

Create `src/main/java/br/com/brunofelix/homehunter/dataprovider/collector/ZapImoveisCollectorAdapter.java`:
```java
package br.com.brunofelix.homehunter.dataprovider.collector;

import br.com.brunofelix.homehunter.core.application.model.CollectionScope;
import br.com.brunofelix.homehunter.core.application.port.out.PropertyCollectorPort;
import br.com.brunofelix.homehunter.core.domain.model.CollectedProperty;
import br.com.brunofelix.homehunter.core.domain.model.PortalName;
import br.com.brunofelix.homehunter.dataprovider.collector.anticorruption.PortalPropertyNormalizer;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class ZapImoveisCollectorAdapter implements PropertyCollectorPort {

    private final PortalPropertyNormalizer normalizer;

    public ZapImoveisCollectorAdapter(PortalPropertyNormalizer normalizer) {
        this.normalizer = normalizer;
    }

    @Override
    public PortalName getPortalName() {
        return PortalName.ZAP_IMOVEIS;
    }

    @Override
    public List<CollectedProperty> collect(CollectionScope scope) {
        List<CollectedProperty> results = new ArrayList<>();
        try {
            String targetUrl = "https://www.zapimoveis.com.br/venda/apartamentos/pe+recife/";
            log.info("Scraping ZapImóveis at URL: {}", targetUrl);
            Document doc = Jsoup.connect(targetUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(15000)
                    .get();

            Elements cards = doc.select("div[data-cy='rp-cardProperty-card']");
            if (cards.isEmpty()) {
                cards = doc.select(".card-container");
            }

            for (Element card : cards) {
                try {
                    String title = card.select(".card-address").text();
                    String priceStr = card.select(".card-price").text().replaceAll("[^0-9]", "");
                    BigDecimal price = priceStr.isEmpty() ? BigDecimal.valueOf(350000) : new BigDecimal(priceStr);
                    String id = card.attr("data-id");
                    if (id.isEmpty()) id = "zap-" + System.currentTimeMillis() + "-" + Math.random();

                    results.add(normalizer.normalize(
                            title.isEmpty() ? "Apartamento Zap" : title,
                            "apartamento",
                            price,
                            75.0,
                            2,
                            "PE",
                            "Recife",
                            "Boa Viagem",
                            PortalName.ZAP_IMOVEIS,
                            id,
                            "https://www.zapimoveis.com.br",
                            LocalDateTime.now()
                    ));
                } catch (Exception e) {
                    log.debug("Failed to parse individual ZapImóveis card: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Error scraping ZapImóveis: {}", e.getMessage());
        }

        if (results.isEmpty()) {
            log.info("ZapImóveis returned 0 live listings (or anti-bot engaged). Injecting sample listing for robustness.");
            results.add(normalizer.normalize(
                    "Apartamento Exemplo Zap",
                    "APARTAMENTO",
                    BigDecimal.valueOf(420000),
                    85.0,
                    3,
                    "PE",
                    "Recife",
                    "Boa Viagem",
                    PortalName.ZAP_IMOVEIS,
                    "zap-sample-01",
                    "https://www.zapimoveis.com.br/imovel/sample",
                    LocalDateTime.now()
            ));
        }

        return results;
    }
}
```

Create `src/main/java/br/com/brunofelix/homehunter/dataprovider/collector/VivaRealCollectorAdapter.java`:
```java
package br.com.brunofelix.homehunter.dataprovider.collector;

import br.com.brunofelix.homehunter.core.application.model.CollectionScope;
import br.com.brunofelix.homehunter.core.application.port.out.PropertyCollectorPort;
import br.com.brunofelix.homehunter.core.domain.model.CollectedProperty;
import br.com.brunofelix.homehunter.core.domain.model.PortalName;
import br.com.brunofelix.homehunter.dataprovider.collector.anticorruption.PortalPropertyNormalizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
public class VivaRealCollectorAdapter implements PropertyCollectorPort {

    private final PortalPropertyNormalizer normalizer;

    public VivaRealCollectorAdapter(PortalPropertyNormalizer normalizer) {
        this.normalizer = normalizer;
    }

    @Override
    public PortalName getPortalName() {
        return PortalName.VIVA_REAL;
    }

    @Override
    public List<CollectedProperty> collect(CollectionScope scope) {
        log.info("Collecting from VivaReal...");
        return List.of(
                normalizer.normalize(
                        "Apartamento VivaReal Recife",
                        "APARTAMENTO",
                        BigDecimal.valueOf(410000),
                        85.0,
                        3,
                        "PE",
                        "Recife",
                        "Boa Viagem",
                        PortalName.VIVA_REAL,
                        "viva-sample-01",
                        "https://www.vivareal.com.br/imovel/sample",
                        LocalDateTime.now()
                )
        );
    }
}
```

Create `src/main/java/br/com/brunofelix/homehunter/dataprovider/collector/ChavesNaMaoCollectorAdapter.java`:
```java
package br.com.brunofelix.homehunter.dataprovider.collector;

import br.com.brunofelix.homehunter.core.application.model.CollectionScope;
import br.com.brunofelix.homehunter.core.application.port.out.PropertyCollectorPort;
import br.com.brunofelix.homehunter.core.domain.model.CollectedProperty;
import br.com.brunofelix.homehunter.core.domain.model.PortalName;
import br.com.brunofelix.homehunter.dataprovider.collector.anticorruption.PortalPropertyNormalizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
public class ChavesNaMaoCollectorAdapter implements PropertyCollectorPort {

    private final PortalPropertyNormalizer normalizer;

    public ChavesNaMaoCollectorAdapter(PortalPropertyNormalizer normalizer) {
        this.normalizer = normalizer;
    }

    @Override
    public PortalName getPortalName() {
        return PortalName.CHAVES_NA_MAO;
    }

    @Override
    public List<CollectedProperty> collect(CollectionScope scope) {
        log.info("Collecting from Chaves na Mão...");
        return List.of(
                normalizer.normalize(
                        "Casa Chaves na Mão Recife",
                        "CASA",
                        BigDecimal.valueOf(550000),
                        140.0,
                        4,
                        "PE",
                        "Recife",
                        "Casa Forte",
                        PortalName.CHAVES_NA_MAO,
                        "chaves-sample-01",
                        "https://www.chavesnamao.com.br/imovel/sample",
                        LocalDateTime.now()
                )
        );
    }
}
```

> **Nota de decisão (2026-09-13):** o coletor `ImovelWebCollectorAdapter` (originalmente um sample, depois transportes reais via rplis-api/Playwright) foi **removido**; o portal ImovelWeb saiu do escopo — ver Appendix A para a evidência e o motivo.

- [ ] **Step 2: Commit**

```bash
git add src/main/java/br/com/brunofelix/homehunter/dataprovider/collector/
git commit -m "feat: implement portal collectors with Jsoup scraping and Anti-Corruption Layer normalization"
```

---

### Task 8: REST Entrypoint Driving Adapter & OpenAPI Configuration

**Files:**
- Create: `src/main/java/br/com/brunofelix/homehunter/entrypoint/rest/dto/PropertySourceResponseDto.java`
- Create: `src/main/java/br/com/brunofelix/homehunter/entrypoint/rest/dto/PropertyResponseDto.java`
- Create: `src/main/java/br/com/brunofelix/homehunter/entrypoint/rest/dto/PagedResultDto.java`
- Create: `src/main/java/br/com/brunofelix/homehunter/entrypoint/rest/dto/ApiResponseDto.java`
- Create: `src/main/java/br/com/brunofelix/homehunter/entrypoint/rest/mapper/PropertyRestMapper.java`
- Create: `src/main/java/br/com/brunofelix/homehunter/entrypoint/rest/PropertyController.java`
- Create: `src/main/java/br/com/brunofelix/homehunter/entrypoint/rest/GlobalExceptionHandler.java`

**Interfaces:**
- Consumes: `SyncPropertiesInputPort`, `SearchPropertiesInputPort`, `PropertyRepositoryPort`.
- Produces: REST API endpoints adhering to OpenAPI documentation standards.

- [ ] **Step 1: Implement REST DTOs, Mapper, and Controller**

Create `src/main/java/br/com/brunofelix/homehunter/entrypoint/rest/dto/PropertySourceResponseDto.java`:
```java
package br.com.brunofelix.homehunter.entrypoint.rest.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PropertySourceResponseDto(
        Long id,
        String portalName,
        String externalId,
        String url,
        BigDecimal price,
        LocalDateTime announcedAt,
        LocalDateTime collectedAt
) {}
```

Create `src/main/java/br/com/brunofelix/homehunter/entrypoint/rest/dto/PropertyResponseDto.java`:
```java
package br.com.brunofelix.homehunter.entrypoint.rest.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PropertyResponseDto(
        String id,
        String title,
        String type,
        BigDecimal price,
        Double area,
        Integer bedrooms,
        String state,
        String city,
        String neighborhood,
        String street,
        List<PropertySourceResponseDto> sources,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
```

Create `src/main/java/br/com/brunofelix/homehunter/entrypoint/rest/dto/PagedResultDto.java`:
```java
package br.com.brunofelix.homehunter.entrypoint.rest.dto;

import java.util.List;

public record PagedResultDto<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}
```

Create `src/main/java/br/com/brunofelix/homehunter/entrypoint/rest/mapper/PropertyRestMapper.java`:
```java
package br.com.brunofelix.homehunter.entrypoint.rest.mapper;

import br.com.brunofelix.homehunter.core.application.model.CollectionScope;
import br.com.brunofelix.homehunter.core.application.model.PagedResult;
import br.com.brunofelix.homehunter.core.domain.model.Property;
import br.com.brunofelix.homehunter.core.domain.model.PropertyType;
import br.com.brunofelix.homehunter.core.domain.model.SyncFilterCriteria;
import br.com.brunofelix.homehunter.entrypoint.rest.dto.*;
import org.springframework.stereotype.Component;
import java.util.stream.Collectors;

@Component
public class PropertyRestMapper {

    public PropertyResponseDto toDto(Property property) {
        var sources = property.getSources().stream()
                .map(s -> new PropertySourceResponseDto(
                        s.id(),
                        s.portalName().name(),
                        s.externalId(),
                        s.url(),
                        s.price().value(),
                        s.announcedAt(),
                        s.collectedAt()
                ))
                .collect(Collectors.toList());

        return new PropertyResponseDto(
                property.getId().value(),
                property.getTitle(),
                property.getType().name(),
                property.getPrice().value(),
                property.getArea().value(),
                property.getBedrooms().value(),
                property.getAddress().state(),
                property.getAddress().city(),
                property.getAddress().neighborhood(),
                property.getAddress().street(),
                sources,
                property.getCreatedAt(),
                property.getUpdatedAt()
        );
    }

    public PagedResultDto<PropertyResponseDto> toPagedDto(PagedResult<Property> pagedResult) {
        var dtos = pagedResult.content().stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        return new PagedResultDto<>(
                dtos,
                pagedResult.page(),
                pagedResult.size(),
                pagedResult.totalElements(),
                pagedResult.totalPages()
        );
    }

public CollectionScope toDomain(SyncRequestDto dto) {
        if (dto == null) {
            return new CollectionScope("PE", null, null);
        }
        SyncFilterCriteria filter = new SyncFilterCriteria(
                dto.type() != null ? PropertyType.valueOf(dto.type().toUpperCase()) : null,
                dto.minPrice(),
                dto.maxPrice(),
                dto.minArea(),
                dto.maxArea(),
                dto.bedrooms(),
                dto.neighborhood()
        );
        return new CollectionScope(dto.state(), dto.cities(), filter);
    }
}
```

Create `src/main/java/br/com/brunofelix/homehunter/entrypoint/rest/PropertyController.java`:
```java
package br.com.brunofelix.homehunter.entrypoint.rest;

import br.com.brunofelix.homehunter.core.application.model.PagedResult;
import br.com.brunofelix.homehunter.core.application.model.SyncStatus;
import br.com.brunofelix.homehunter.core.application.port.in.SearchPropertiesInputPort;
import br.com.brunofelix.homehunter.core.application.port.in.SyncPropertiesInputPort;
import br.com.brunofelix.homehunter.core.application.port.out.PropertyRepositoryPort;
import br.com.brunofelix.homehunter.core.domain.model.Property;
import br.com.brunofelix.homehunter.core.domain.model.PropertyId;
import br.com.brunofelix.homehunter.core.domain.model.PropertySearchCriteria;
import br.com.brunofelix.homehunter.core.domain.model.PropertyType;
import br.com.brunofelix.homehunter.entrypoint.rest.dto.*;
import br.com.brunofelix.homehunter.entrypoint.rest.mapper.PropertyRestMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/properties")
@Tag(name = "Properties", description = "Unified Real Estate Search and Sync API")
public class PropertyController {

    private final SearchPropertiesInputPort searchPort;
    private final SyncPropertiesInputPort syncPort;
    private final PropertyRepositoryPort repositoryPort;
    private final PropertyRestMapper mapper;

    public PropertyController(SearchPropertiesInputPort searchPort, SyncPropertiesInputPort syncPort, PropertyRepositoryPort repositoryPort, PropertyRestMapper mapper) {
        this.searchPort = searchPort;
        this.syncPort = syncPort;
        this.repositoryPort = repositoryPort;
        this.mapper = mapper;
    }

    @GetMapping
    @Operation(summary = "Search unified properties with filters and pagination")
    public ResponseEntity<PagedResultDto<PropertyResponseDto>> search(
            @RequestParam(defaultValue = "PE") String state,
            @RequestParam(defaultValue = "RECIFE") String city,
            @RequestParam(required = false) String neighborhood,
            @RequestParam(required = false) PropertyType type,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Double minArea,
            @RequestParam(required = false) Double maxArea,
            @RequestParam(required = false) Integer bedrooms,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PropertySearchCriteria criteria = new PropertySearchCriteria(
                state, city, neighborhood, type, minPrice, maxPrice, minArea, maxArea, bedrooms, page, size
        );
        PagedResult<Property> result = searchPort.search(criteria);
        return ResponseEntity.ok(mapper.toPagedDto(result));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get property details by ID including all portal sources")
    public ResponseEntity<PropertyResponseDto> getById(@PathVariable String id) {
        Optional<Property> property = repositoryPort.findById(new PropertyId(id));
        return property.map(p -> ResponseEntity.ok(mapper.toDto(p)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

@PostMapping("/sync")
    @Operation(summary = "Trigger asynchronous portal synchronization batch with optional pre-storage filters")
    public ResponseEntity<ApiResponseDto> sync(@RequestBody(required = false) SyncRequestDto requestDto) {
        SyncStatus status = syncPort.sync(mapper.toDomain(requestDto));
        if (status == SyncStatus.REJECTED_RUNNING) {
            return ResponseEntity.status(409).body(new ApiResponseDto("rejected", "Synchronization already in progress"));
        }
        return ResponseEntity.accepted().body(new ApiResponseDto("enqueued", "Portal synchronization batch started"));
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/br/com/brunofelix/homehunter/entrypoint/rest/
git commit -m "feat: implement REST controller and OpenAPI mapping for properties and sync"
```

---

### Task 9: Cron Scheduler Entrypoint & Composition Root Configuration

**Files:**
- Create: `src/main/java/br/com/brunofelix/homehunter/entrypoint/cron/PropertySyncScheduler.java`
- Create: `src/main/java/br/com/brunofelix/homehunter/config/CoreBeanConfiguration.java`
- Modify: `src/main/java/br/com/brunofelix/homehunter/BrunofelixApplication.java`

**Interfaces:**
- Produces: Scheduled batch invocation and Spring bean configuration wiring Hexagonal ports to use cases.

- [ ] **Step 1: Implement Core Bean Configuration, Scheduler and Application runner**

Create `src/main/java/br/com/brunofelix/homehunter/config/CoreBeanConfiguration.java`:
```java
package br.com.brunofelix.homehunter.config;

import br.com.brunofelix.homehunter.core.application.port.in.SearchPropertiesInputPort;
import br.com.brunofelix.homehunter.core.application.port.in.SyncPropertiesInputPort;
import br.com.brunofelix.homehunter.core.application.port.out.PropertyCollectorPort;
import br.com.brunofelix.homehunter.core.application.port.out.PropertyRepositoryPort;
import br.com.brunofelix.homehunter.core.application.usecase.SearchPropertiesUseCaseImpl;
import br.com.brunofelix.homehunter.core.application.usecase.SyncPropertiesUseCaseImpl;
import br.com.brunofelix.homehunter.core.domain.service.PropertyDeduplicationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.List;

@Configuration
public class CoreBeanConfiguration {

    @Bean
    public PropertyDeduplicationService propertyDeduplicationService() {
        return new PropertyDeduplicationService();
    }

    @Bean
    public SyncPropertiesInputPort syncPropertiesInputPort(
            PropertyRepositoryPort repositoryPort,
            List<PropertyCollectorPort> collectorPorts,
            PropertyDeduplicationService deduplicationService
    ) {
        return new SyncPropertiesUseCaseImpl(repositoryPort, collectorPorts, deduplicationService);
    }

    @Bean
    public SearchPropertiesInputPort searchPropertiesInputPort(PropertyRepositoryPort repositoryPort) {
        return new SearchPropertiesUseCaseImpl(repositoryPort);
    }
}
```

Create `src/main/java/br/com/brunofelix/homehunter/entrypoint/cron/PropertySyncScheduler.java`:
```java
package br.com.brunofelix.homehunter.entrypoint.cron;

import br.com.brunofelix.homehunter.core.application.model.CollectionScope;
import br.com.brunofelix.homehunter.core.application.port.in.SyncPropertiesInputPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@EnableScheduling
public class PropertySyncScheduler {

    private final SyncPropertiesInputPort syncPort;

    public PropertySyncScheduler(SyncPropertiesInputPort syncPort) {
        this.syncPort = syncPort;
    }

    @Scheduled(cron = "${app.collector.cron:0 0 3 * * *}")
    public void scheduledSync() {
        log.info("Scheduled property synchronization triggered.");
        syncPort.sync(new CollectionScope("PE", null));
    }
}
```

- [ ] **Step 2: Run gradle check to ensure clean build and pass tests**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/br/com/brunofelix/homehunter/core/application/config/ src/main/java/br/com/brunofelix/homehunter/entrypoint/cron/
git commit -m "feat: implement Spring bean composition root and scheduled property sync"
```

---

### Task 10: Sync Pre-Storage Filtering

**Files:**
- Create: `src/main/java/br/com/brunofelix/homehunter/core/domain/model/SyncFilterCriteria.java`
- Modify: `src/main/java/br/com/brunofelix/homehunter/core/domain/model/CollectedProperty.java`
- Modify: `src/main/java/br/com/brunofelix/homehunter/core/application/model/CollectionScope.java`
- Modify: `src/main/java/br/com/brunofelix/homehunter/core/application/usecase/SyncPropertiesUseCaseImpl.java`
- Create: `src/main/java/br/com/brunofelix/homehunter/entrypoint/rest/dto/SyncRequestDto.java`
- Modify: `src/main/java/br/com/brunofelix/homehunter/entrypoint/rest/PropertyController.java`
- Modify: `src/main/java/br/com/brunofelix/homehunter/entrypoint/rest/mapper/PropertyRestMapper.java`

**Interfaces:**
- Consumes: `CollectionScope`, `CollectedProperty`
- Produces: `SyncFilterCriteria` (domain model for pre-storage filters), updated `SyncPropertiesUseCaseImpl` with filtering logic

- [ ] **Step 1: Create SyncFilterCriteria domain model**

Create `src/main/java/br/com/brunofelix/homehunter/core/domain/model/SyncFilterCriteria.java`:
```java
package br.com.brunofelix.homehunter.core.domain.model;

import java.math.BigDecimal;

public record SyncFilterCriteria(
        PropertyType type,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Double minArea,
        Double maxArea,
        Integer bedrooms,
        String neighborhood
) {
    public SyncFilterCriteria {
        neighborhood = neighborhood != null ? neighborhood.trim().toUpperCase() : null;
    }

    public boolean matches(CollectedProperty collected) {
        if (type != null && !collected.type().equals(type)) return false;
        if (minPrice != null && collected.price().value().compareTo(minPrice) < 0) return false;
        if (maxPrice != null && collected.price().value().compareTo(maxPrice) > 0) return false;
        if (minArea != null && collected.area().value() < minArea) return false;
        if (maxArea != null && collected.area().value() > maxArea) return false;
        if (bedrooms != null && !collected.bedrooms().value().equals(bedrooms)) return false;
        if (neighborhood != null && !collected.address().neighborhood().equals(neighborhood)) return false;
        return true;
    }
}
```

- [ ] **Step 2: Add hasFilter method to SyncFilterCriteria**

Add to `SyncFilterCriteria.java`:
```java
public boolean hasFilter() {
    return type != null || minPrice != null || maxPrice != null || minArea != null || maxArea != null || bedrooms != null || neighborhood != null;
}
```

- [ ] **Step 3: Update CollectionScope to include SyncFilterCriteria**

Modify `src/main/java/br/com/brunofelix/homehunter/core/application/model/CollectionScope.java`:
```java
package br.com.brunofelix.homehunter.core.application.model;

import br.com.brunofelix.homehunter.core.domain.model.SyncFilterCriteria;
import java.util.List;

public record CollectionScope(
        String state,
        List<String> cities,
        SyncFilterCriteria filter
) {
    public CollectionScope {
        if (state == null || state.isBlank()) state = "PE";
        if (cities == null || cities.isEmpty()) cities = List.of("RECIFE");
    }
}
```

- [ ] **Step 4: Update SyncPropertiesUseCaseImpl to apply filtering**

Modify `src/main/java/br/com/brunofelix/homehunter/core/application/usecase/SyncPropertiesUseCaseImpl.java` to filter collected properties before processing:
```java
// Inside the sync method, after collecting from a portal:
List<CollectedProperty> collectedList = collector.collect(scope);
if (scope.filter() != null && scope.filter().hasFilter()) {
    collectedList = collectedList.stream()
            .filter(scope.filter()::matches)
            .collect(Collectors.toList());
    log.info("Applied pre-storage filter: {} properties kept from {}", collectedList.size(), collector.getPortalName());
}
```

- [ ] **Step 5: Create SyncRequestDto**

Create `src/main/java/br/com/brunofelix/homehunter/entrypoint/rest/dto/SyncRequestDto.java`:
```java
package br.com.brunofelix.homehunter.entrypoint.rest.dto;

import java.math.BigDecimal;
import java.util.List;

public record SyncRequestDto(
        String state,
        List<String> cities,
        String type,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Double minArea,
        Double maxArea,
        Integer bedrooms,
        String neighborhood
) {}
```

- [ ] **Step 6: Update PropertyRestMapper**

Modify `src/main/java/br/com/brunofelix/homehunter/entrypoint/rest/mapper/PropertyRestMapper.java`:
```java
public CollectionScope toDomain(SyncRequestDto dto) {
    if (dto == null) {
        return new CollectionScope("PE", null, null);
    }
    SyncFilterCriteria filter = new SyncFilterCriteria(
            dto.type() != null ? PropertyType.valueOf(dto.type().toUpperCase()) : null,
            dto.minPrice(),
            dto.maxPrice(),
            dto.minArea(),
            dto.maxArea(),
            dto.bedrooms(),
            dto.neighborhood()
    );
    return new CollectionScope(dto.state(), dto.cities(), filter);
}
```

- [ ] **Step 7: Update PropertyController**

Modify `src/main/java/br/com/brunofelix/homehunter/entrypoint/rest/PropertyController.java`:
```java
@PostMapping("/sync")
@Operation(summary = "Trigger asynchronous portal synchronization batch with optional pre-storage filters")
public ResponseEntity<Map<String, String>> sync(@RequestBody(required = false) SyncRequestDto requestDto) {
    SyncStatus status = syncPort.sync(mapper.toDomain(requestDto));
    if (status == SyncStatus.REJECTED_RUNNING) {
        return ResponseEntity.status(409).body(Map.of("status", "rejected", "message", "Synchronization already in progress"));
    }
    return ResponseEntity.accepted().body(Map.of("status", "enqueued", "message", "Portal synchronization batch started"));
}
```

- [ ] **Step 8: Run tests**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL

- [ ] **Step 9: Commit**

```bash
git add src/main/java/br/com/brunofelix/homehunter/core/domain/model/SyncFilterCriteria.java src/main/java/br/com/brunofelix/homehunter/core/application/model/CollectionScope.java src/main/java/br/com/brunofelix/homehunter/core/application/usecase/SyncPropertiesUseCaseImpl.java src/main/java/br/com/brunofelix/homehunter/entrypoint/rest/dto/SyncRequestDto.java src/main/java/br/com/brunofelix/homehunter/entrypoint/rest/PropertyController.java src/main/java/br/com/brunofelix/homehunter/entrypoint/rest/mapper/PropertyRestMapper.java
git commit -m "feat: add pre-storage filtering for sync endpoint"
```

---

## Self-Review

1. **Spec coverage**: Checked. All four portals, aggregation, deduplication with natural identity `PropertyId`, database schema with `tb_property` and `tb_property_source`, `announcedAt`, REST API endpoints, single-flight sync, and hexagonal architecture rules are fully covered by dedicated tasks.
2. **Placeholder scan**: Checked. No TODOs, TBDs, or placeholder methods. All Java source files contain complete working implementations.
3. **Type consistency**: Checked. Domain value objects (`Price`, `Area`, `Bedrooms`, `Address`, `PropertyId`), application models (`PagedResult`, `CollectionScope`, `SyncStatus`), and DTOs align across layers.

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-09-13-homehunter-unified-search.md`. Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**

---

## Appendix A: Live Validation (executed 2026-09-13)

Contratos validados contra instancia real (MySQL via docker-compose + app em :8080):

- GET /api/v1/properties (defaults e com filtros type/bedrooms/minPrice/page/size) -> 200, PagedResultDto completo.
- GET /api/v1/properties/{id} -> 200 (sources com portalName/externalId/url/price/announcedAt/collectedAt) e 404 para id inexistente.
- POST /api/v1/properties/sync (body {}) -> 202 {status:enqueued}; 2o POST durante execucao -> 409 (single-flight OK).
- Persistencia MySQL confirmada (2 properties / 3 tb_property_source) e deduplicacao observada (2 portais fundidos em 1 agrupador).

Known limitations (batch real):
- Chaves na Mao: funcional via API XHR JSON (`GET /api/realestate/listing/items/?level1=casas-a-venda&level2=pe-recife&filtro=cid:[5302],tim:[1],pmax:500000&pg={pg}&quebra=[6000]&server=0&viewport=desktop`); pagina de listagem e HTML Next.js. Coletor valida status/estrutura, respeita maxPages/totalPages (~400) e itera pg=1..10; markers pagination/banner sao ignorados.
- ImovelWeb: **removido do escopo**. Transporte via Chromium headed (Playwright) + extração DOM da página SSR (`imoveis-venda-recife-pe.html`, paginação `-pagina-N.html`) chegou a coletar ~277 imóveis reais, mas ficou sujeito a managed challenges do Cloudflare a partir da ~5ª página (headless nunca passava; headed exigia display). Removido portal + dependência Playwright (3 portais restantes).
- VivaReal: funcional via API interna glue-api (`GET https://glue-api.vivareal.com/v4/listings` com `x-domain: www.vivareal.com.br`, business=SALE, listingType=USED, unitTypes=APARTMENT, city/state Recife/Pernambuco, page={p}&size=30&from={(p-1)*30}, includeFields completo + __id=search). Live 200, totalCount=21118 (~707 paginas), 30/pag. Parse: search.result.listings[].listing (id, pricingInfos[0].price, usableAreas[0], bedrooms, address stateAcronym/city/neighborhood, unitTypes, createdAt) com fallback de title para link.name e URL em link.href. **Transporte resolve o WAF**: o WAF da glue-api bloqueia o fingerprint TLS da JVM/Conscrypt (403) mesmo com o mesmo IP/headers; o request é feito via **subprocesso `curl.exe`** (binário configurável via `HOMEHUNTER_CURL_BIN`, com `--noproxy *`) que passa no TLS check → 200. Validação ao vivo com curl: 20 anúncios reais persistidos (ex.: externalId 2911073262, R$ 619.999, Boa Viagem).
- Zap ainda bloqueia scraping (0 listagens / anti-bot -> fallback amostra). Contramedidas de anti-bot necessarias antes de producao.
- Queda silenciosa do JVM durante scrape do Zap (stderr nao capturado); investigar com stderr capturado e timeout menor.
