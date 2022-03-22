package com.apress.springrecipes.bank;

import com.apress.springrecipes.bank.exceptions.AccountNotFoundException;
import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class InMemoryAccountDaoTests {

    private static final String EXISTING_ACCOUNT_NO = "1234";
    private static final String NEW_ACCOUNT_NO = "5678";

    private Account existingAccount;
    private Account newAccount;
    private InMemoryAccountDao accountDao;

    @BeforeEach
    public void init() {
        existingAccount = new Account(EXISTING_ACCOUNT_NO, 100);
        newAccount = new Account(NEW_ACCOUNT_NO, 200);
        accountDao = new InMemoryAccountDao();
        accountDao.createAccount(existingAccount);
    }

    @Test
    public void accountExists() {
        Assertions.assertTrue(accountDao.accountExists(EXISTING_ACCOUNT_NO));
        Assertions.assertFalse(accountDao.accountExists(NEW_ACCOUNT_NO));
    }

    @Test
    public void createNewAccount() {
        accountDao.createAccount(newAccount);
        Assertions.assertEquals(accountDao.findAccount(NEW_ACCOUNT_NO), newAccount);
    }
    @Test
    public void updateExistedAccount() {
        existingAccount.setBalance(150);
        accountDao.updateAccount(existingAccount);
        Assertions.assertEquals(accountDao.findAccount(EXISTING_ACCOUNT_NO), existingAccount);
    }

    @Test
    public void updateNotExistedAccount() {
        Assertions.assertThrows(AccountNotFoundException.class, () ->  accountDao.updateAccount(newAccount));
    }

    @Test
    public void removeExistedAccount() {
        accountDao.removeAccount(existingAccount);
        Assertions.assertFalse(accountDao.accountExists(EXISTING_ACCOUNT_NO));
    }

    @Test
    public void findExistedAccount() {
        Account account = accountDao.findAccount(EXISTING_ACCOUNT_NO);
        Assertions.assertEquals(account, existingAccount);
    }

    @Test
    public void findNotExistedAccount() {
        Assertions.assertThrows(AccountNotFoundException.class, () -> accountDao.findAccount(NEW_ACCOUNT_NO));
    }

}
