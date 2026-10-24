package com.filmae.repository;

import com.filmae.entity.MoviePrincipal;
import com.filmae.entity.MoviePrincipalId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface MoviePrincipalRepository extends JpaRepository<MoviePrincipal, MoviePrincipalId> {

    List<MoviePrincipal> findByTconstOrderByOrderingAsc(String tconst);

    /** Carga em lote do elenco/equipe de vários filmes, agrupável por {@code tconst}. */
    List<MoviePrincipal> findByTconstInOrderByTconstAscOrderingAsc(Collection<String> tconsts);

    List<MoviePrincipal> findByTconstInAndCategoryInOrderByTconstAscOrderingAsc(Collection<String> tconsts,
                                                                                Collection<String> categories);
}
