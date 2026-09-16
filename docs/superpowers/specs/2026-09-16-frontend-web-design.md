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
- Endpoint de Busca: `GET /api/v1/properties?search={termo}`
- Estrutura de Resposta esperada (exemplo):
  ```json
  {
    "content": [
      {
        "id": "...",
        "title": "...",
        "type": "...",
        "price": 100000.00,
        "area": 50.0,
        "bedrooms": 2,
        "bathrooms": 1,
        "suites": 0,
        "parkingSpaces": 1,
        "condoFee": 500.00,
        "iptu": 100.00,
        "state": "...",
        "city": "...",
        "neighborhood": "...",
        "street": "...",
        "sources": [...]
      }
    ]
  }
  ```

## Layout e UX
- Cabeçalho: Título "HomeHunter" e barra de busca centralizada.
- Conteúdo: Grid responsivo exibindo cards de imóveis com foto, título e preço.
- Interação: Busca em tempo real ou ao pressionar "Enter".

## Configuração Spring Boot
- Por padrão, o Spring Boot serve arquivos em `src/main/resources/static/`. Não será necessária configuração adicional de `WebMvcConfigurer` a menos que requisitos de roteamento avançado (SPA) surjam.

