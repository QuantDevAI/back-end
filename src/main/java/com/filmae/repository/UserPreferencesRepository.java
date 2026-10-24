package com.filmae.repository;

import com.filmae.entity.UserPreferences;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserPreferencesRepository extends JpaRepository<UserPreferences, Long> {

    /** Cria a linha com os defaults do banco se ainda não existir. */
    @Modifying
    @Query(value = "INSERT INTO user_preferences (user_id) VALUES (:userId) ON CONFLICT (user_id) DO NOTHING",
        nativeQuery = true)
    int insertDefaultsIfAbsent(@Param("userId") Long userId);
}
