package com.example.gptunittestwriterjava.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

public class MavenDependencyLocator {
    /**
     * 执行 Maven 命令来生成依赖路径文件，然后读取该文件。
     *
     * @param projectDirectory 项目的根目录。
     * @return 包含依赖路径的列表。
     */
    public static List<String> getDependencyPaths(String projectDirectory) {
        List<String> paths = new ArrayList<>();
        String classpathFilePath = projectDirectory + File.separator+ "cp.txt";
        String command = "mvn dependency:build-classpath -Dmdep.outputFile=cp.txt";

        try {
            // 执行 Maven 命令
            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.command("bash", "-c", "cd " + projectDirectory + " && " + command);
            Process process = processBuilder.start();
            int exitVal = process.waitFor();

            if (exitVal != 0) {
                System.out.println("Error in executing Maven command");
                return paths;
            }

            // 读取类路径文件
            try (BufferedReader reader = new BufferedReader(new FileReader(classpathFilePath))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    for (String path : line.split(System.getProperty("path.separator"))) {
                        if (!path.isEmpty()) {
                            paths.add(path);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            Files.delete(Paths.get(classpathFilePath));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return paths;
    }

    /**
     * 在 JAR 文件列表中查找特定的类文件。
     *
     * @param jarPaths JAR 文件路径列表。
     * @param fullClassName 要查找的全类名。
     * @return 找到的 JAR 文件路径，如果没有找到则返回 null。
     */
    public static String findClassInJars(List<String> jarPaths, String fullClassName) {
        String classPath = fullClassName.replace('.', '/') + ".class";

        for (String jarPath : jarPaths) {
            try (JarFile jarFile = new JarFile(jarPath)) {
                ZipEntry entry = jarFile.getEntry(classPath);
                if (entry != null) {
                    // 找到类文件
                    return jarPath;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null; // 没有找到类文件
    }

    /**
     * 从源代码 source-JAR 文件中提取指定的类的源代码。
     *
     * @param sourcesJarPath 源代码 JAR 文件的路径。
     * @param fullClassName 要提取的类的全名。
     * @return 类的源代码，如果找不到则返回 null。
     */
    public static String extractSourceCode(String sourcesJarPath, String fullClassName) {
        String classPath = fullClassName.replace('.', '/') + ".java";
        classPath = FileUtils.changeToSystemFileSeparator(classPath);
        try (JarFile jarFile = new JarFile(sourcesJarPath)) {
            ZipEntry entry = jarFile.getEntry(classPath);
            if (entry != null) {
                // 读取并返回源代码
                return new String(jarFile.getInputStream(entry).readAllBytes());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null; // 没有找到源代码
    }
}
