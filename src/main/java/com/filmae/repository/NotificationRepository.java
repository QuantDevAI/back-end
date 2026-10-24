package com.filmae.repository;

import com.filmae.entity.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    long countByUserIdAndReadFalse(Long userId);

    boolean existsByUserIdAndDedupeKey(Long userId, String dedupeKey);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Notification n SET n.read = TRUE, n.readAt = :now WHERE n.userId = :userId AND n.read = FALSE")
    int markAllRead(@Param("userId") Long userId, @Param("now") Instant now);

    /**
     * Cria a notificação só se ainda não existir outra com o mesmo {@code dedupeKey} para o usuário.
     * Retorna 1 se inseriu. {@code type} é o valor do banco ({@code NotificationType.getValue()}).
     */
    @Modifying
    @Query(value = """
        INSERT INTO notifications (user_id, type, title, message, dedupe_key)
        VALUES (:userId, :type, :title, :message, :dedupeKey)
        ON CONFLICT (user_id, dedupe_key) DO NOTHING
        """, nativeQuery = true)
    int insertIfAbsent(@Param("userId") Long userId,
                       @Param("type") String type,
                       @Param("title") String title,
                       @Param("message") String message,
                       @Param("dedupeKey") String dedupeKey);
}
