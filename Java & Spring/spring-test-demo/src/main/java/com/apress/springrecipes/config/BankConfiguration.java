package com.apress.springrecipes.config;

import com.apress.springrecipes.bank.AccountServiceImpl;
import com.apress.springrecipes.bank.InMemoryAccountDao;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BankConfiguration {

    @Bean
    public InMemoryAccountDao accountDao() {
        return new InMemoryAccountDao();
    }

    @Bean
    public AccountServiceImpl accountService() {
        return new AccountServiceImpl(accountDao());
    }
}