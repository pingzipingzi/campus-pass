package com.example.campuspass.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class ThreadPoolConfig {

    @Bean
    public ExecutorService threadPoolExecutor(){

        AtomicInteger threadNumber=new AtomicInteger(1);

        ThreadFactory defaultThreadFactory=Executors.defaultThreadFactory();

        ThreadFactory serviceThreadFactory=task ->{
            Thread thread=defaultThreadFactory.newThread(task);
            thread.setName("service-pool-"+threadNumber.getAndIncrement());
            return thread;
        };

        return new ThreadPoolExecutor(2, 4, 60, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(20),
                serviceThreadFactory,
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

}
