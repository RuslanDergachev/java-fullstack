import work.customannotatition.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class CustomTestRunner {
    public static void main(String[] args) throws Exception {
        String packageName = "custom.tests";
        List<Class<?>> classesToTest = findClasses(packageName);

        int totalTests = 0;
        int passedTests = 0;
        int failedTests = 0;
        long totalTime = 0;

        List<String> results = new ArrayList<>();

        System.out.println("=== Custom Test Runner Results ===");
        System.out.println("Package: " + packageName);
        System.out.println("Classes scanned: " + classesToTest.size());

        // Счётчик тестов
        for (Class<?> clazz : classesToTest) {
            Method[] methods = clazz.getDeclaredMethods();
            for (Method method : methods) {
                if (method.isAnnotationPresent(Test.class)) {
                    totalTests++;
                    Object instance = clazz.getDeclaredConstructor().newInstance();

                    long start = System.currentTimeMillis();
                    try {
                        method.invoke(instance);
                        long duration = System.currentTimeMillis() - start;
                        totalTime += duration;
                        passedTests++;
                        results.add("✓ " + clazz.getSimpleName() + "." + method.getName() + " (" + duration + "ms)");
                    } catch (Exception e) {
                        long duration = System.currentTimeMillis() - start;
                        totalTime += duration;
                        failedTests++;
                        Throwable cause = e.getCause() != null ? e.getCause() : e;
                        results.add("✗ " + clazz.getSimpleName() + "." + method.getName() + " (" + duration + "ms) - " + cause);
                    }
                }
            }
        }

        System.out.println("Tests discovered: " + totalTests);
        System.out.println("Test Results:");
        results.forEach(System.out::println);

        System.out.println("\nSummary:");
        System.out.println("Total tests: " + totalTests);
        System.out.println("Passed: " + passedTests);
        System.out.println("Failed: " + failedTests);
        System.out.println("Total execution time: " + totalTime + "ms");
        System.out.printf("Success rate: %.1f%%\n", totalTests == 0 ? 0 : (passedTests * 100.0 / totalTests));
    }

    public static List<Class<?>> findClasses(String packageName) throws IOException, ClassNotFoundException {
        String path = packageName.replace('.', '/');
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        URL resource = classLoader.getResource(path);
        if (resource == null) {
            throw new IllegalArgumentException("Package not found: " + packageName);
        }

        File dir = new File(resource.getFile());
        List<Class<?>> classes = new ArrayList<>();

        for (File file : Objects.requireNonNull(dir.listFiles())) {
            if (file.getName().endsWith(".class")) {
                String className = packageName + "." + file.getName().replace(".class", "");
                classes.add(Class.forName(className));
            }
        }
        return classes;
    }
}
