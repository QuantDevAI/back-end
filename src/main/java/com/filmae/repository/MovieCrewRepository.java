package com.filmae.repository;

import com.filmae.entity.MovieCrew;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Diretores/roteiristas ({@code equipe_filme}); use {@code findAllById} para carregar em lote. */
@Repository
public interface MovieCrewRepository extends JpaRepository<MovieCrew, String> {
}
