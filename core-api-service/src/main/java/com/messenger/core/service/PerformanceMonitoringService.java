package com.messenger.core.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.springframework.stereotype.Service;

import jakarta.persistence.EntityManagerFactory;

@Service
@RequiredArgsConstructor
@Slf4j
public class PerformanceMonitoringService {

    private final EntityManagerFactory entityManagerFactory;

    /**
     * Получить статистику Hibernate для мониторинга N+1 проблем
     */
    public void logHibernateStatistics() {
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        Statistics stats = sessionFactory.getStatistics();

        if (stats.isStatisticsEnabled()) {
            log.info("=== Hibernate Statistics ===");
            log.info("Queries executed: {}", stats.getQueryExecutionCount());
            log.info("Query cache hits: {}", stats.getQueryCacheHitCount());
            log.info("Query cache misses: {}", stats.getQueryCacheMissCount());
            log.info("Second level cache hits: {}", stats.getSecondLevelCacheHitCount());
            log.info("Second level cache misses: {}", stats.getSecondLevelCacheMissCount());
            log.info("Sessions opened: {}", stats.getSessionOpenCount());
            log.info("Sessions closed: {}", stats.getSessionCloseCount());
            log.info("Transactions: {}", stats.getTransactionCount());
            log.info("Successful transactions: {}", stats.getSuccessfulTransactionCount());
            log.info("Entity loads: {}", stats.getEntityLoadCount());
            log.info("Entity fetches: {}", stats.getEntityFetchCount());
            log.info("Collection loads: {}", stats.getCollectionLoadCount());
            log.info("Collection fetches: {}", stats.getCollectionFetchCount());

            // Показываем общую статистику запросов
            String[] queries = stats.getQueries();
            if (queries == null) queries = new String[0];
            log.info("Total unique queries executed: {}", queries.length);
            log.info("Slowest query time: {}ms", stats.getQueryExecutionMaxTime());

            // Показываем список всех выполненных запросов
            for (String query : queries) {
                log.debug("Executed query: {}", query);
            }

            // Показываем самые медленные запросы
            log.info("=== Query Performance Summary ===");
            log.info("Max query execution time: {}ms", stats.getQueryExecutionMaxTime());
            log.info("Total query executions: {}", stats.getQueryExecutionCount());

            // Детальная информация о сущностях
            String[] entityNames = stats.getEntityNames();
            for (String entityName : entityNames) {
                log.debug("Entity {}: loads={}, fetches={}, inserts={}, updates={}, deletes={}",
                    entityName,
                    stats.getEntityLoadCount(),
                    stats.getEntityFetchCount(),
                    stats.getEntityInsertCount(),
                    stats.getEntityUpdateCount(),
                    stats.getEntityDeleteCount());
            }

            // Информация о коллекциях
            String[] collectionRoleNames = stats.getCollectionRoleNames();
            for (String roleName : collectionRoleNames) {
                log.debug("Collection {}: loads={}, fetches={}, updates={}, recreates={}",
                    roleName,
                    stats.getCollectionLoadCount(),
                    stats.getCollectionFetchCount(),
                    stats.getCollectionUpdateCount(),
                    stats.getCollectionRecreateCount());
            }

        } else {
            log.warn("Hibernate statistics are disabled. Enable them in application.yml");
        }
    }

    /**
     * Сбросить статистику
     */
    public void resetStatistics() {
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        Statistics stats = sessionFactory.getStatistics();
        stats.clear();
        log.info("Hibernate statistics cleared");
    }

    /**
     * Включить статистику
     */
    public void enableStatistics() {
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        Statistics stats = sessionFactory.getStatistics();
        stats.setStatisticsEnabled(true);
        log.info("Hibernate statistics enabled");
    }
}
