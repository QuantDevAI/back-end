package com.filmae.repository;

import com.filmae.entity.UserMovieId;
import com.filmae.entity.UserOnboardingFavoriteMovie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserOnboardingFavoriteMovieRepository extends JpaRepository<UserOnboardingFavoriteMovie, UserMovieId> {

    List<UserOnboardingFavoriteMovie> findByUserIdOrderByPositionAsc(Long userId);

    long countByUserId(Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM UserOnboardingFavoriteMovie f WHERE f.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
