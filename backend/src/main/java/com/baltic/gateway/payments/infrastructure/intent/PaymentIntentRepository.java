package com.baltic.gateway.payments.infrastructure.intent;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import com.baltic.gateway.payments.domain.intent.PaymentIntent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentIntentRepository extends JpaRepository<PaymentIntent, UUID> {
    /** Another user's intent is simply absent, so its existence is not revealed. */
    Optional<PaymentIntent> findByIdAndOwnerId(UUID id, UUID ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from PaymentIntent i where i.id = :id and i.ownerId = :ownerId")
    Optional<PaymentIntent> findByIdAndOwnerIdForUpdate(@Param("id") UUID id, @Param("ownerId") UUID ownerId);
}
