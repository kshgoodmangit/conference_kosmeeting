package com.bjworld21.conference.service;

import com.bjworld21.conference.config.PersonalDataProperties;
import com.bjworld21.conference.entity.AdminAccount;
import com.bjworld21.conference.repository.AdminAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminLoginTransactionTest {
    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class Config {}

    static class Transactions extends AbstractPlatformTransactionManager {
        int committed;
        int rolledBack;
        protected Object doGetTransaction() { return new Object(); }
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            assertThat(definition.isReadOnly()).isFalse();
        }
        protected void doCommit(DefaultTransactionStatus status) { committed++; }
        protected void doRollback(DefaultTransactionStatus status) { rolledBack++; }
    }

    @Test
    void rejectedCredentialsCommitFailuresButDatabaseErrorsRollBackForBothEntryPoints() {
        var repository = mock(AdminAccountRepository.class);
        var encoder = mock(PasswordEncoder.class);
        var transactions = new Transactions();
        when(repository.findByEmailForUpdate("admin", PersonalDataTestSupport.DB_ENC_STRING)).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            return AdminAccount.builder().seq(1L).status("active").role("admin").password("hash").build();
        });
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(Config.class);
            context.registerBean("transactionManager", Transactions.class, () -> transactions);
            context.registerBean(AdminAccountRepository.class, () -> repository);
            context.registerBean(PasswordEncoder.class, () -> encoder);
            context.registerBean(PersonalDataProperties.class, PersonalDataTestSupport::properties);
            context.registerBean(AdminCredentialVerifier.class);
            context.refresh();
            var verifier = context.getBean(AdminCredentialVerifier.class);

            assertThatThrownBy(() -> verifier.verifyLogin("admin", "wrong"))
                    .isInstanceOf(AdminCredentialVerifier.AuthenticationRejectedException.class);
            assertThatThrownBy(() -> verifier.verifyActiveAdministrator("admin", "wrong"))
                    .isInstanceOf(AdminCredentialVerifier.AuthenticationRejectedException.class);
            assertThat(transactions.committed).isEqualTo(2);
            assertThat(transactions.rolledBack).isZero();
            verify(repository, times(2)).recordLoginFailure(1L, 1);

            doThrow(new IllegalStateException("database unavailable")).when(repository).recordLoginFailure(1L, 1);
            assertThatThrownBy(() -> verifier.verifyLogin("admin", "wrong"))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(transactions.committed).isEqualTo(2);
            assertThat(transactions.rolledBack).isEqualTo(1);
        }
    }
}
