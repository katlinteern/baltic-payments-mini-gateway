package com.baltic.gateway.accounts.application.ledger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LedgerReconciliationJobTest {
    @Mock
    LedgerReconciliationService service;

    @InjectMocks
    LedgerReconciliationJob job;

    @Test
    void runsTheReconciliation() {
        job.run();

        verify(service).reconcile();
    }

    @Test
    void aFailingRunDoesNotStopTheScheduler() {
        when(service.reconcile()).thenThrow(new IllegalStateException("database down"));

        assertDoesNotThrow(job::run);
    }
}
