package com.meridian.keystone.repository;

import com.meridian.keystone.domain.Notification;
import com.meridian.keystone.domain.NotificationType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @EntityGraph(attributePaths = {"workOrder"})
    List<Notification> findTop50ByRecipientIdOrderByCreatedAtDesc(Long recipientId);

    long countByRecipientIdAndReadAtIsNull(Long recipientId);

    Optional<Notification> findByIdAndRecipientId(Long id, Long recipientId);

    /** Guard against re-notifying the same breach on every sweep. */
    boolean existsByWorkOrderIdAndTypeAndRecipientId(Long workOrderId,
                                                     NotificationType type,
                                                     Long recipientId);

    @Modifying
    @Query("update Notification n set n.readAt = :now "
            + "where n.recipient.id = :recipientId and n.readAt is null")
    int markAllRead(@Param("recipientId") Long recipientId, @Param("now") Instant now);
}
