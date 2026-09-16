document.addEventListener('DOMContentLoaded', () => {
    const searchBtn = document.getElementById('search-btn');
    const stateSelect = document.getElementById('state-select');
    const citySelect = document.getElementById('city-select');
    const minPrice = document.getElementById('min-price');
    const maxPrice = document.getElementById('max-price');
    const sortSelect = document.getElementById('sort-select');
    const sizeSelect = document.getElementById('size-select');
    const resultsGrid = document.getElementById('results-grid');

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
        if (minPrice.value) url.searchParams.append('minPrice', minPrice.value);
        if (maxPrice.value) url.searchParams.append('maxPrice', maxPrice.value);
        url.searchParams.append('sort', sortSelect.value);
        url.searchParams.append('size', sizeSelect.value);

        fetch(url)
            .then(response => response.json())
            .then(data => {
                resultsGrid.innerHTML = '';
                data.content.forEach(property => {
                    const card = document.createElement('div');
                    card.className = 'property-card';
                    card.innerHTML = `<h3>${property.title}</h3><p>Preço: ${property.price}</p>`;
                    resultsGrid.appendChild(card);
                });
            })
            .catch(err => console.error('Erro na busca:', err));
    });
});
