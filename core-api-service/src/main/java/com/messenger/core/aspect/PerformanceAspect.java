package com.messenger.core.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
public class PerformanceAspect {

    @Around("execution(* com.messenger.core.service.*.*(..))")
    public Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();

        String methodName = joinPoint.getSignature().getDeclaringTypeName() + "." + joinPoint.getSignature().getName();

        try {
            Object result = joinPoint.proceed();
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            if (duration > 100) { // Логируем только медленные запросы
                log.warn("SLOW METHOD: {} took {}ms", methodName, duration);
            } else {
                log.debug("Method {} took {}ms", methodName, duration);
            }

            return result;
        } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            log.error("Method {} failed after {}ms with error: {}",
                methodName, endTime - startTime, e.getMessage());
            throw e;
        }
    }

    @Around("execution(* com.messenger.core.repository.*.*(..))")
    public Object logRepositoryExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();

        String methodName = joinPoint.getSignature().getDeclaringTypeName() + "." + joinPoint.getSignature().getName();

        try {
            Object result = joinPoint.proceed();
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            if (duration > 50) { // Логируем медленные запросы к БД
                log.warn("SLOW REPOSITORY: {} took {}ms", methodName, duration);
            }

            return result;
        } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            log.error("Repository method {} failed after {}ms with error: {}",
                methodName, endTime - startTime, e.getMessage());
            throw e;
        }
    }
}
