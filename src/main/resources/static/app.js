document.addEventListener('DOMContentLoaded', () => {
    const searchBtn = document.getElementById('search-btn');
    const stateSelect = document.getElementById('state-select');
    const citySelect = document.getElementById('city-select');
    const minPrice = document.getElementById('min-price');
    const maxPrice = document.getElementById('max-price');
    const sortSelect = document.getElementById('sort-select');
    const sizeSelect = document.getElementById('size-select');
    const typeSelect = document.getElementById('type-select');
    const resultsGrid = document.querySelector('._card-list');

    // Carregar estados
    fetch('/api/v1/properties/states')
        .then(res => res.json())
        .then(states => {
            states.forEach(state => {
                const opt = document.createElement('option');
                opt.value = state;
                opt.textContent = state;
                stateSelect.appendChild(opt);
            });
        });

    // Carregar cidades quando estado mudar
    stateSelect.addEventListener('change', () => {
        citySelect.innerHTML = '<option value="">Todas as Cidades</option>';
        if (stateSelect.value) {
            fetch(`/api/v1/properties/cities?state=${stateSelect.value}`)
                .then(res => res.json())
                .then(cities => {
                    cities.forEach(city => {
                        const opt = document.createElement('option');
                        opt.value = city;
                        opt.textContent = city;
                        citySelect.appendChild(opt);
                    });
                });
        }
    });

    searchBtn.addEventListener('click', () => {
        let url = new URL('/api/v1/properties', window.location.origin);
        if (stateSelect.value) url.searchParams.append('state', stateSelect.value);
        if (citySelect.value) url.searchParams.append('city', citySelect.value);
        if (typeSelect.value) url.searchParams.append('type', typeSelect.value);
        if (minPrice.value) url.searchParams.append('minPrice', minPrice.value);
        if (maxPrice.value) url.searchParams.append('maxPrice', maxPrice.value);
        url.searchParams.append('sort', sortSelect.value);
        url.searchParams.append('size', sizeSelect.value);

        fetch(url)
            .then(response => response.json())
            .then(data => {
                resultsGrid.innerHTML = '';
                data.content.forEach(property => {
                    const card = document.createElement('section');
                    card.className = '_card';
                    
                    const formatPrice = (price) => {
                        return price >= 1000 ? `R$ ${(price / 1000).toFixed(0)}K` : `R$ ${price}`;
                    };

                    card.innerHTML = `
                        <h2 class="_heading | -fluid-text -trim-both">${property.title}</h2>
                        <p class="_category | -trim-both">${property.type}</p>
                        <div class="_thumbnail-stack">
                            ${property.images && property.images.length > 0
                                ? property.images.slice(0, 9).map(img => `<img src="${img}" alt="${property.title}" width="400" height="400" referrerpolicy="no-referrer" />`).join('')
                                : "{{Sem Imagem}}"}
                        </div>
                        <p class="_price">${formatPrice(property.price)}</p>
                        <div class="_details">
                            ${property.bedrooms ? `<p>Quartos: ${property.bedrooms}</p>` : ''}
                            ${property.condoFee ? `<p>Condomínio: R$ ${property.condoFee}</p>` : ''}
                            ${property.iptu ? `<p>IPTU: R$ ${property.iptu}</p>` : ''}
                            ${property.createdAt ? `<p>Anunciado em: ${new Date(property.createdAt).toLocaleDateString()}</p>` : ''}
                        </div>
                        <div class="_subdetails">
                            <p class="_description | -line-clamp">${property.neighborhood || ''} | Anunciado em: ${property.announcedAt ? new Date(property.announcedAt).toLocaleDateString() : '{{Não informado}}'}</p>
                        </div>
                        <div class="_button">
                            <a href="${property.url}" class="scope purchase-button" target="_blank" rel="noopener">Ver Anúncio</a>
                        </div>
                    `;
                    resultsGrid.appendChild(card);
                });
            })
            .catch(err => console.error('Erro na busca:', err));
    });
});
