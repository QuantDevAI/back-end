package com.filmae.repository;

import com.filmae.entity.Person;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Pessoas ({@code pessoas}); use {@code findAllById} para carregar em lote. */
@Repository
public interface PersonRepository extends JpaRepository<Person, String> {
}
