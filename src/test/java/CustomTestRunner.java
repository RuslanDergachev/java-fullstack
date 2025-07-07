import work.customannotatition.AfterEach;
import work.customannotatition.BeforeEach;
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

        for (Class<?> clazz : classesToTest) {
            Method[] methods = clazz.getDeclaredMethods();

            List<Method> beforeEachMethods = new ArrayList<>();
            List<Method> afterEachMethods = new ArrayList<>();

            for (Method method : methods) {
                if (method.isAnnotationPresent(BeforeEach.class)) {
                    beforeEachMethods.add(method);
                }
                if (method.isAnnotationPresent(AfterEach.class)) {
                    afterEachMethods.add(method);
                }
            }

            for (Method testMethod : methods) {
                if (testMethod.isAnnotationPresent(Test.class)) {
                    totalTests++;
                    Object instance = clazz.getDeclaredConstructor().newInstance();

                    long start = System.currentTimeMillis();
                    try {
                        for (Method before : beforeEachMethods) {
                            before.setAccessible(true);
                            before.invoke(instance);
                        }

                        testMethod.setAccessible(true);
                        testMethod.invoke(instance);

                        for (Method after : afterEachMethods) {
                            after.setAccessible(true);
                            after.invoke(instance);
                        }

                        long duration = System.currentTimeMillis() - start;
                        totalTime += duration;
                        passedTests++;
                        results.add(
                                "✓ " + clazz.getSimpleName() + "." + testMethod.getName() + " (" + duration + "ms)");
                    } catch (Exception e) {
                        long duration = System.currentTimeMillis() - start;
                        totalTime += duration;
                        failedTests++;
                        Throwable cause = e.getCause() != null ? e.getCause() : e;
                        results.add(
                                "✗ " + clazz.getSimpleName() + "." + testMethod.getName() + " (" + duration + "ms) - " +
                                        cause);
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
