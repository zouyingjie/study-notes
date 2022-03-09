package com.apress.springrecipes.bank;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Account {

    private String accountNo;
    private double balance;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Account account = (Account) o;
        return Objects.equals(this.accountNo, account.accountNo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.accountNo);
    }
}
