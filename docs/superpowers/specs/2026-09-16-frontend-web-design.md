# Design: Front-end Web para HomeHunter (Arquivos Estáticos)

## Objetivo
Implementar uma interface web simples para consumir a API `homehunter`, permitindo que usuários pesquisem e visualizem imóveis.

## Abordagem
Arquivos estáticos (HTML, CSS, JS) servidos diretamente pelo Spring Boot (`src/main/resources/static/`).

## Componentes
- `index.html`: Layout principal.
- `style.css`: Estilização (CSS).
- `app.js`: Lógica de interface e chamadas `fetch` para a API.

## Fluxo de Dados
1. Interface do usuário envia requisição.
2. `app.js` realiza `fetch` para os endpoints da API (ex: `/api/properties`).
3. API retorna JSON.
4. `app.js` renderiza dados no DOM.

## Detalhamento da API
- Endpoint de Busca: `GET /api/v1/properties`
- Endpoint de Metadados:
  - `GET /api/v1/properties/states`: Retorna lista de estados disponíveis.
  - `GET /api/v1/properties/cities?state={state}`: Retorna lista de cidades para um estado.
- Parâmetros de Query (Filtros):
  - `state` (String, opcional, default: null - busca todos)
  - `city` (String, opcional, default: null - busca todos)
  - `neighborhood` (String, opcional)
  - `type` (Enum, opcional)
  - `minPrice`, `maxPrice` (BigDecimal, opcional)
  - `minArea`, `maxArea` (Double, opcional)
  - `bedrooms` (Integer, opcional)
  - `page`, `size` (int, default: 0, 20)
  - `sort` (String, opcional: "price", "type", "announcedAt", direction: "asc" ou "desc")

## Layout e UX
- Formulário de Busca Avançada:
  - Campos Dinâmicos: Estado e Cidade serão `<select>` preenchidos dinamicamente ao carregar a página. Inicialmente não selecionam filtro (buscam tudo).
  - Seleção de Ordenação: Campo para escolher campo (preço, tipo, data de anúncio) e direção (asc, desc).
  - Seleção de Tamanho de Página: Campo para escolher o número de resultados por página (20, 50 ou 100).
- Conteúdo: Grid responsivo exibindo cards de imóveis com detalhes principais.
- Interação: Botão "Buscar" aplica todos os filtros e ordenação na query da API.



## Configuração Spring Boot
- Por padrão, o Spring Boot serve arquivos em `src/main/resources/static/`. Não será necessária configuração adicional de `WebMvcConfigurer` a menos que requisitos de roteamento avançado (SPA) surjam.

