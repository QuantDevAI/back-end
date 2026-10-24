package com.filmae.repository;

import com.filmae.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByAuthUserId(UUID authUserId);

    /** Cria o usuário local se ainda não existir (seguro sob concorrência). Retorna 1 se inseriu. */
    @Modifying
    @Query(value = """
        INSERT INTO users (auth_user_id, email, name)
        VALUES (:authUserId, :email, :name)
        ON CONFLICT (auth_user_id) DO NOTHING
        """, nativeQuery = true)
    int insertIfAbsent(@Param("authUserId") UUID authUserId, @Param("email") String email, @Param("name") String name);
}
