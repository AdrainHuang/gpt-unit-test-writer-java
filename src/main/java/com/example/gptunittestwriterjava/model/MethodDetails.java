package com.example.gptunittestwriterjava.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Set;

@Getter
@Setter
@ToString
public class MethodDetails {
    private String methodName;
    private String code;
    private int startLine;
    private int endLine;
    private Set<String> importedClasses;

    public String getCodeWithLineNumbers() {
        StringBuilder sb = new StringBuilder();

        // Append imported classes
        if (importedClasses != null) {
            for (String importClass : importedClasses) {
                sb.append("import ").append(importClass).append(";\n");
            }
        }

        // Append method code with line numbers
        String[] lines = code.split("\n");
        for (int i = 0; i < lines.length; i++) {
            sb.append(startLine + i)
                    .append(": ")
                    .append(lines[i])
                    .append("\n");
        }
        return sb.toString();
    }
}
