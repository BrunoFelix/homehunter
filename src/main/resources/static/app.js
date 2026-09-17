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

    // Modal logic
    const modal = document.getElementById('image-modal');
    const modalImg = document.getElementById('modal-img');
    const closeBtn = document.getElementById('close-modal');
    const prevBtn = document.getElementById('prev-img');
    const nextBtn = document.getElementById('next-img');
    const counter = document.getElementById('image-counter');
    let currentImages = [];
    let currentIndex = 0;

    function updateCounter() {
        counter.textContent = `${currentIndex + 1} de ${currentImages.length}`;
    }

    function openModal(images, index) {
        currentImages = images;
        currentIndex = index;
        modalImg.src = currentImages[currentIndex];
        updateCounter();
        modal.style.display = 'flex';
    }

    closeBtn.onclick = () => modal.style.display = 'none';
    prevBtn.onclick = () => {
        currentIndex = (currentIndex > 0) ? currentIndex - 1 : currentImages.length - 1;
        modalImg.src = currentImages[currentIndex];
        updateCounter();
    };
    nextBtn.onclick = () => {
        currentIndex = (currentIndex < currentImages.length - 1) ? currentIndex + 1 : 0;
        modalImg.src = currentImages[currentIndex];
        updateCounter();
    };

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

    // Pagination
    const paginationControls = document.getElementById('pagination-controls');
    const pageInfo = document.getElementById('page-info');
    const prevPageBtn = document.getElementById('prev-page');
    const nextPageBtn = document.getElementById('next-page');
    let currentPage = 0;
    let totalPages = 0;

    function fetchProperties(page = 0) {
        currentPage = page;
        let url = new URL('/api/v1/properties', window.location.origin);
        if (stateSelect.value) url.searchParams.append('state', stateSelect.value);
        if (citySelect.value) url.searchParams.append('city', citySelect.value);
        if (typeSelect.value) url.searchParams.append('type', typeSelect.value);
        if (minPrice.value) url.searchParams.append('minPrice', minPrice.value);
        if (maxPrice.value) url.searchParams.append('maxPrice', maxPrice.value);
        url.searchParams.append('sort', sortSelect.value);
        url.searchParams.append('size', sizeSelect.value);
        url.searchParams.append('page', currentPage);

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
                        <div class="_thumbnail-stack" style="cursor: pointer;">
                            ${property.images && property.images.length > 0
                                ? property.images.slice(0, 4).map(img => `<img src="${img}" alt="${property.title}" width="400" height="400" referrerpolicy="no-referrer" />`).join('')
                                : "{{Sem Imagem}}"}
                        </div>
                        <p class="_price">${formatPrice(property.price)}</p>
                        <div class="_details">
                            <p>
                                ${property.bedrooms ? `Quartos: ${property.bedrooms}` : ''}
                                ${property.bathrooms ? ` | Banheiros: ${property.bathrooms}` : ''}
                                ${property.condoFee ? ` | Condomínio: R$ ${property.condoFee}` : ''}
                                ${property.iptu ? ` | IPTU: R$ ${property.iptu}` : ''}
                            </p>
                        </div>
                        <div class="_subdetails">
                            <p class="_description | -line-clamp">${property.neighborhood || ''} | Anunciado em: ${property.announcedAt ? new Date(property.announcedAt).toLocaleDateString() : '{{Não informado}}'}</p>
                        </div>
                        <div class="_button">
                            <a href="${property.url}" class="scope purchase-button" target="_blank" rel="noopener">Ver Anúncio</a>
                        </div>
                    `;
                    
                    if (property.images && property.images.length > 0) {
                        const thumbnailStack = card.querySelector('._thumbnail-stack');
                        thumbnailStack.addEventListener('click', (e) => {
                            if(e.target.tagName === 'IMG') {
                                const allImgs = Array.from(thumbnailStack.querySelectorAll('img'));
                                const index = allImgs.indexOf(e.target);
                                openModal(property.images, index);
                            }
                        });
                    }
                    resultsGrid.appendChild(card);
                });

                totalPages = data.totalPages;
                pageInfo.textContent = `Página ${currentPage + 1} de ${totalPages}`;
                paginationControls.style.display = totalPages > 1 ? 'flex' : 'none';
                prevPageBtn.disabled = currentPage === 0;
                nextPageBtn.disabled = currentPage >= totalPages - 1;
            })
            .catch(err => console.error('Erro na busca:', err));
    }

    // ... (modal logic acima) ...

    function init() {
        const searchBtn = document.getElementById('search-btn');
        if (!searchBtn) {
            console.error('Botão de busca não encontrado!');
            return;
        }
        searchBtn.onclick = () => {
            console.log('Botão buscar clicado!');
            fetchProperties(0);
        };
        console.log('Evento de busca vinculado!');
    }

    init();
    document.addEventListener('DOMContentLoaded', () => {
        init();
        // ... carregar estados ...
    });

    prevPageBtn.addEventListener('click', () => { if (currentPage > 0) fetchProperties(currentPage - 1); });
    nextPageBtn.addEventListener('click', () => { if (currentPage < totalPages - 1) fetchProperties(currentPage + 1); });

});
