package com.filmae.repository;

import com.filmae.entity.Episode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Episódios ({@code episodios}). */
@Repository
public interface EpisodeRepository extends JpaRepository<Episode, String> {
}
