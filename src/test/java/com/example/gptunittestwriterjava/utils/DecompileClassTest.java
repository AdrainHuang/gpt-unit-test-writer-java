package com.example.gptunittestwriterjava.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


class DecompileClassTest {

    @Test
    void test_decompileClass(){
        String s = DecompileClass.decompileClass("src/test/resources/spring-data-commons-2.5.12.jar", "org.springframework.data.domain.PageRequest");
        Assertions.assertNotNull(s);
        Assertions.assertTrue(s.contains("PageRequest"));
    }

}