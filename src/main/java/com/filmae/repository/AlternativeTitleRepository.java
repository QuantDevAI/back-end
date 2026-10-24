package com.filmae.repository;

import com.filmae.entity.AlternativeTitle;
import com.filmae.entity.AlternativeTitleId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface AlternativeTitleRepository extends JpaRepository<AlternativeTitle, AlternativeTitleId> {

    /** Títulos de uma região para vários filmes (ex.: {@code AlternativeTitle.REGION_BR}), menor ordering primeiro. */
    List<AlternativeTitle> findByTitleIdInAndRegionOrderByTitleIdAscOrderingAsc(Collection<String> titleIds, String region);

    List<AlternativeTitle> findByTitleIdAndRegionOrderByOrderingAsc(String titleId, String region);
}
