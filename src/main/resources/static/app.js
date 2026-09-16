document.addEventListener('DOMContentLoaded', () => {
    const searchBtn = document.getElementById('search-btn');
    const searchInput = document.getElementById('search-input');
    const resultsGrid = document.getElementById('results-grid');

    searchBtn.addEventListener('click', () => {
        const query = searchInput.value;
        fetch(`/api/v1/properties?search=${query}`)
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
