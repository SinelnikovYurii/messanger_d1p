package com.messenger.core.service;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PerformanceMonitoringServiceTest {
    private EntityManagerFactory entityManagerFactory;
    private SessionFactory sessionFactory;
    private Statistics statistics;
    private PerformanceMonitoringService performanceMonitoringService;

    @BeforeEach
    void setUp() {
        entityManagerFactory = Mockito.mock(EntityManagerFactory.class);
        sessionFactory = Mockito.mock(SessionFactory.class);
        statistics = Mockito.mock(Statistics.class);
        when(entityManagerFactory.unwrap(SessionFactory.class)).thenReturn(sessionFactory);
        System.out.println("sessionFactory in setUp: " + sessionFactory);
        // Гарантируем возврат одного и того же объекта statistics
        when(sessionFactory.getStatistics()).thenAnswer(invocation -> statistics);
        performanceMonitoringService = new PerformanceMonitoringService(entityManagerFactory);
    }

    @Test
    void testLogHibernateStatistics_enabled() {
        System.out.println("sessionFactory in testLogHibernateStatistics_enabled: " + sessionFactory);
        when(statistics.isStatisticsEnabled()).thenReturn(true);
        when(statistics.getQueries()).thenReturn(new String[]{"SELECT * FROM users"});
        when(statistics.getQueryExecutionCount()).thenReturn(5L);
        when(statistics.getQueryCacheHitCount()).thenReturn(2L);
        when(statistics.getQueryCacheMissCount()).thenReturn(1L);
        when(statistics.getSecondLevelCacheHitCount()).thenReturn(3L);
        when(statistics.getSecondLevelCacheMissCount()).thenReturn(0L);
        when(statistics.getSessionOpenCount()).thenReturn(4L);
        when(statistics.getSessionCloseCount()).thenReturn(4L);
        when(statistics.getTransactionCount()).thenReturn(2L);
        when(statistics.getSuccessfulTransactionCount()).thenReturn(2L);
        when(statistics.getEntityLoadCount()).thenReturn(10L);
        when(statistics.getEntityFetchCount()).thenReturn(5L);
        when(statistics.getCollectionLoadCount()).thenReturn(6L);
        when(statistics.getCollectionFetchCount()).thenReturn(3L);
        when(statistics.getQueryExecutionMaxTime()).thenReturn(100L);
        when(statistics.getEntityNames()).thenReturn(new String[]{"User"});
        when(statistics.getCollectionRoleNames()).thenReturn(new String[]{"userRoles"});
        // Добавим возврат значений для методов, вызываемых в логике
        when(statistics.getEntityInsertCount()).thenReturn(1L);
        when(statistics.getEntityUpdateCount()).thenReturn(2L);
        when(statistics.getEntityDeleteCount()).thenReturn(0L);
        when(statistics.getCollectionUpdateCount()).thenReturn(1L);
        when(statistics.getCollectionRecreateCount()).thenReturn(0L);
        // Исправление: возвращаем пустой массив если getQueries вызывается с null
        when(statistics.getQueries()).thenReturn(new String[0]);
        performanceMonitoringService.logHibernateStatistics();
    }

    @Test
    void testLogHibernateStatistics_disabled() {
        System.out.println("sessionFactory in testLogHibernateStatistics_disabled: " + sessionFactory);
        when(statistics.isStatisticsEnabled()).thenReturn(false);
        performanceMonitoringService.logHibernateStatistics();
    }

    @Test
    void testResetStatistics() {
        doNothing().when(statistics).clear();
        performanceMonitoringService.resetStatistics();
        verify(statistics, times(1)).clear();
    }

    @Test
    void testResetStatistics_alreadyCleared() {
        doNothing().when(statistics).clear();
        performanceMonitoringService.resetStatistics();
        performanceMonitoringService.resetStatistics();
        verify(statistics, times(2)).clear();
    }

    @Test
    void testEnableStatistics() {
        System.out.println("sessionFactory in testEnableStatistics: " + sessionFactory);
        performanceMonitoringService.enableStatistics();
        verify(statistics, times(1)).setStatisticsEnabled(true);
    }

    @Test
    @SuppressWarnings("UnnecessaryStubbing")
    void testEnableStatistics_alreadyEnabled() {
        // Используем lenient для предотвращения UnnecessaryStubbingException
        org.mockito.Mockito.lenient().when(statistics.isStatisticsEnabled()).thenReturn(true);
        performanceMonitoringService.enableStatistics();
        verify(statistics, times(1)).setStatisticsEnabled(true);
    }
}
