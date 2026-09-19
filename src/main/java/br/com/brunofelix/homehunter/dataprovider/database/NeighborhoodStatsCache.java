package br.com.brunofelix.homehunter.dataprovider.database;

import br.com.brunofelix.homehunter.core.domain.model.PropertyStats;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class NeighborhoodStatsCache {

    static final int DEFAULT_MAX_ENTRIES = 512;

    private final Map<NeighborhoodKey, PropertyStats> cache;

    public NeighborhoodStatsCache() {
        this(DEFAULT_MAX_ENTRIES);
    }

    NeighborhoodStatsCache(int maxEntries) {
        this.cache = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<NeighborhoodKey, PropertyStats> eldest) {
                return size() > maxEntries;
            }
        };
    }

    public synchronized PropertyStats get(String state, String city, String neighborhood) {
        return cache.get(new NeighborhoodKey(state, city, neighborhood));
    }

    public synchronized void put(String state, String city, String neighborhood, PropertyStats stats) {
        cache.put(new NeighborhoodKey(state, city, neighborhood), stats);
    }

    public synchronized void clear() {
        cache.clear();
    }

    record NeighborhoodKey(String state, String city, String neighborhood) {}
}