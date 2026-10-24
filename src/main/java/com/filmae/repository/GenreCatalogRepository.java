package com.filmae.repository;

import com.filmae.entity.GenreCatalog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface GenreCatalogRepository extends JpaRepository<GenreCatalog, String> {

    List<GenreCatalog> findAllByOrderBySortOrderAsc();

    /** Um registro por gênero IMDb (sem aliases como {@code suspense}). */
    List<GenreCatalog> findByCanonicalTrueOrderBySortOrderAsc();

    Optional<GenreCatalog> findFirstByImdbNameIgnoreCaseAndCanonicalTrue(String imdbName);

    List<GenreCatalog> findBySlugIn(Collection<String> slugs);
}
