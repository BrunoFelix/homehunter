package br.com.brunofelix.homehunter.dataprovider.database.repository;

import br.com.brunofelix.homehunter.dataprovider.database.entity.PropertyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface SpringDataPropertyRepository extends JpaRepository<PropertyEntity, String>, JpaSpecificationExecutor<PropertyEntity> {

    @Query("select p from PropertyEntity p where p.portalName = :portalName and p.externalId = :externalId")
    Optional<PropertyEntity> findBySource(@Param("portalName") String portalName, @Param("externalId") String externalId);

    @Query("select distinct p.state from PropertyEntity p order by p.state asc")
    List<String> findAllStates();

    @Query("select distinct p.city from PropertyEntity p where p.state = :state order by p.city asc")
    List<String> findCitiesByState(@Param("state") String state);

    @Query("select p from PropertyEntity p where p.state = :state and p.city = :city and p.neighborhood = :neighborhood")
    List<PropertyEntity> findByNeighborhood(@Param("state") String state, @Param("city") String city, @Param("neighborhood") String neighborhood);
}
