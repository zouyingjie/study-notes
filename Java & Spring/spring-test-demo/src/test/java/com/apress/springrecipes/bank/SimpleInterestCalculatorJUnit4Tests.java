package com.apress.springrecipes.bank;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


public class SimpleInterestCalculatorJUnit4Tests {

    private InterestCalculator interestCalculator;

    @BeforeEach
    public void init() {
        interestCalculator = new SimpleInterestCalculator();
        interestCalculator.setRate(0.05);
    }

    @Test
    public void calculate() {
        double interest = interestCalculator.calculate(10000, 2);
        assertEquals(interest, 1000.0, 0);
    }

    @Test
    public void illegalCalculate() {
        assertThrows(IllegalArgumentException.class, () -> interestCalculator.calculate(-10000, 2));
    }
}
