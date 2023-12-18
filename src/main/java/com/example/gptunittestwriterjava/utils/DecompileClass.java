package com.example.gptunittestwriterjava.utils;

import com.strobel.assembler.InputTypeLoader;
import com.strobel.assembler.metadata.ITypeLoader;
import com.strobel.assembler.metadata.MetadataSystem;
import com.strobel.assembler.metadata.TypeReference;
import com.strobel.decompiler.DecompilationOptions;
import com.strobel.decompiler.DecompilerSettings;
import com.strobel.decompiler.PlainTextOutput;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class DecompileClass {
    public static String decompileClass(String jarPath, String className) {
        try (JarFile jarFile = new JarFile(jarPath)) {
            String classPath = className.replace('.', '/') + ".class";
            classPath = FileUtils.changeToSystemFileSeparator(classPath);
            JarEntry entry = jarFile.getJarEntry(classPath);

            if (entry == null) {
                return null; // 类文件不存在
            }

            try (InputStream inputStream = jarFile.getInputStream(entry)) {
                ITypeLoader typeLoader = new InputTypeLoader();
                MetadataSystem metadataSystem = new MetadataSystem(typeLoader);

                TypeReference type = metadataSystem.lookupType(classPath);
                if (type == null) {
                    return null; // 类型未找到
                }

                DecompilerSettings settings = DecompilerSettings.javaDefaults();
                settings.setUnicodeOutputEnabled(true);

                StringWriter stringWriter = new StringWriter();
                settings.getLanguage().decompileType(type.resolve(), new PlainTextOutput(stringWriter), new DecompilationOptions());
                return stringWriter.toString();
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
