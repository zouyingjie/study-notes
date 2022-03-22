package com.apress.springrecipes.bank.web;

import com.apress.springrecipes.bank.AccountService;
import com.apress.springrecipes.web.DepositController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DepositControllerTest {
    private static final String TEST_ACCOUNT_NO = "1234";
    private static final double TEST_AMOUNT = 50;
    private AccountService accountService;
    private DepositController depositController;

    @BeforeEach
    public void init() {
        accountService = Mockito.mock(AccountService.class);
        depositController = new DepositController(accountService);
    }

    @Test
    public void deposit() {
        //Setup
        Mockito.when(accountService.getBalance(TEST_ACCOUNT_NO)).thenReturn(150.0);
        ModelMap model = new ModelMap();

        //Execute
        String viewName =
                depositController.deposit(TEST_ACCOUNT_NO, TEST_AMOUNT, model);

        assertEquals(viewName, "success");
        assertEquals(model.get("accountNo"), TEST_ACCOUNT_NO);
        assertEquals(model.get("balance"), 150.0);
    }

}