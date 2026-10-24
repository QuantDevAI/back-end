package com.filmae.repository;

import com.filmae.entity.UserOnboarding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Progresso do onboarding (id = users.id). */
@Repository
public interface UserOnboardingRepository extends JpaRepository<UserOnboarding, Long> {
}
