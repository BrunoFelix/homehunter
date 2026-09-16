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

## Tratamento de Erros
- Exibição de mensagens amigáveis em caso de falha na requisição.

## Testes
- Validação visual manual e testes de unidade para `app.js` se necessário.
