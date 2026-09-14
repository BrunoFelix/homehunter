package br.com.brunofelix.homehunter.dataprovider.database.repository;

import br.com.brunofelix.homehunter.dataprovider.database.entity.PropertyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface SpringDataPropertyRepository extends JpaRepository<PropertyEntity, String>, JpaSpecificationExecutor<PropertyEntity> {

    @Query("select p from PropertyEntity p join p.sources s where s.portalName = :portalName and s.externalId = :externalId")
    Optional<PropertyEntity> findBySource(@Param("portalName") String portalName, @Param("externalId") String externalId);
}
