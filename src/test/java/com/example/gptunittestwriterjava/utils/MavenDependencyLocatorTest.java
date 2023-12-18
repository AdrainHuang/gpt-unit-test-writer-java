package com.example.gptunittestwriterjava.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class MavenDependencyLocatorTest {

    @Test
    void test_getDependencyPaths(){
        //current folder
        String projectDirectory = ".";
        List<String> output = MavenDependencyLocator.getDependencyPaths(projectDirectory);
        assertThat(output).size().isGreaterThan(1);
    }

    @Test
    void test_findClassInJars(){
        String projectDirectory = ".";
        List<String> dependencyPaths = MavenDependencyLocator.getDependencyPaths(projectDirectory);
        String path = MavenDependencyLocator.findClassInJars(dependencyPaths, "org.springframework.data.domain.PageRequest");
        System.out.println(path);
        assertThat(path).isNotNull();
    }

}