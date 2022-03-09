package com.apress.springrecipes.bank;

import com.apress.springrecipes.bank.exceptions.InsufficientBalanceException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class AccountServiceImplStubTests {

    private static final String TEST_ACCOUNT_NO = "1234";
    private AccountDaoStub accountDaoStub;
    private AccountService accountService;

    private class AccountDaoStub implements AccountDao {

        private String accountNo;
        private double balance;

        public void createAccount(Account account) {}
        public void removeAccount(Account account) {}

        public Account findAccount(String accountNo) {
            return new Account(this.accountNo, this.balance);
        }

        public void updateAccount(Account account) {
            this.accountNo = account.getAccountNo();
            this.balance = account.getBalance();
        }
    }

    @BeforeEach
    public void init() {
        accountDaoStub = new AccountDaoStub();
        accountDaoStub.accountNo = TEST_ACCOUNT_NO;
        accountDaoStub.balance = 100;
        accountService = new AccountServiceImpl(accountDaoStub);
    }

    @Test
    public void deposit() {
        accountService.deposit(TEST_ACCOUNT_NO, 50);
        Assertions.assertEquals(accountDaoStub.accountNo, TEST_ACCOUNT_NO);
        Assertions.assertEquals(accountDaoStub.balance, 150, 0);
    }

    @Test
    public void withdrawWithSufficientBalance() {
        accountService.withdraw(TEST_ACCOUNT_NO, 50);
        Assertions.assertEquals(accountDaoStub.accountNo, TEST_ACCOUNT_NO);
        Assertions.assertEquals(accountDaoStub.balance, 50, 0);
    }

    @Test
    public void withdrawWithInsufficientBalance() {
        Assertions.assertThrows(InsufficientBalanceException.class, () ->  accountService.withdraw(TEST_ACCOUNT_NO, 150));
    }
}
