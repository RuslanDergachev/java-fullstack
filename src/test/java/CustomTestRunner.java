import org.junit.jupiter.params.provider.ValueSource;
import work.customannotatition.BeforeEach;
import work.customannotatition.ParameterizedTest;
import work.customannotatition.AfterEach;
import work.customannotatition.Test;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class CustomTestRunner {
     static void main(String[] args) throws Exception {
        String[] packageNames = {"custom.tests", "work.collectionclasses"};
        List<Class<?>> classesToTest = new ArrayList<>();
        for (String pkg : packageNames) {
            classesToTest.addAll(findClasses(pkg));
        }

        int totalTests = 0;
        int passedTests = 0;
        int failedTests = 0;
        long totalTime = 0;

        List<String> results = new ArrayList<>();

        System.out.println("=== Custom Test Runner Results ===");
        System.out.println("Packages: " + String.join(", ", packageNames));
        System.out.println("Classes scanned: " + classesToTest.size());

        for (Class<?> clazz : classesToTest) {
            Method[] methods = clazz.getDeclaredMethods();

            List<Method> beforeEachMethods = new ArrayList<>();
            List<Method> afterEachMethods = new ArrayList<>();

            for (Method method : methods) {
                if (method.isAnnotationPresent(BeforeEach.class)
                        || method.isAnnotationPresent(org.junit.jupiter.api.BeforeEach.class)) {
                    beforeEachMethods.add(method);
                }
                if (method.isAnnotationPresent(AfterEach.class)
                        || method.isAnnotationPresent(org.junit.jupiter.api.AfterEach.class)) {
                    afterEachMethods.add(method);
                }
            }

            for (Method method : methods) {
                boolean isCustomTest = method.isAnnotationPresent(Test.class);
                boolean isJunitTest = method.isAnnotationPresent(org.junit.jupiter.api.Test.class);
                boolean isRepeated = method.isAnnotationPresent(org.junit.jupiter.api.RepeatedTest.class);

                if (isCustomTest || isJunitTest || isRepeated) {
                    int repetitions = 1;
                    if (isRepeated) {
                        org.junit.jupiter.api.RepeatedTest rt = method.getAnnotation(org.junit.jupiter.api.RepeatedTest.class);
                        repetitions = rt.value();
                    }

                    for (int rep = 1; rep <= repetitions; rep++) {
                        totalTests++;
                        Object instance = clazz.getDeclaredConstructor().newInstance();

                        long start = System.currentTimeMillis();
                        try {
                            for (Method before : beforeEachMethods) {
                                before.setAccessible(true);
                                before.invoke(instance);
                            }

                            method.setAccessible(true);
                            if (method.getParameterCount() == 0) {
                                method.invoke(instance);
                            } else {
                                throw new IllegalStateException("Test method must have 0 parameters: " +
                                                                        clazz.getSimpleName() + "." + method.getName());
                            }

                            for (Method after : afterEachMethods) {
                                after.setAccessible(true);
                                after.invoke(instance);
                            }

                            long duration = System.currentTimeMillis() - start;
                            totalTime += duration;
                            passedTests++;
                            String suffix = isRepeated ? (" [#" + rep + "/" + repetitions + "]") : "";
                            results.add("✓ " + clazz.getSimpleName() + "." + method.getName() + suffix + " (" + duration + "ms)");
                        } catch (InvocationTargetException ite) {
                            long duration = System.currentTimeMillis() - start;
                            totalTime += duration;
                            failedTests++;
                            Throwable cause = ite.getCause() != null ? ite.getCause() : ite;
                            String suffix = isRepeated ? (" [#" + rep + "/" + repetitions + "]") : "";
                            results.add("✗ " + clazz.getSimpleName() + "." + method.getName() + suffix + " (" + duration + "ms) - "
                                                + cause.getClass().getSimpleName() + ": " + cause.getMessage());
                        } catch (Exception e) {
                            long duration = System.currentTimeMillis() - start;
                            totalTime += duration;
                            failedTests++;
                            String suffix = isRepeated ? (" [#" + rep + "/" + repetitions + "]") : "";
                            results.add("✗ " + clazz.getSimpleName() + "." + method.getName() + suffix + " (" + duration + "ms) - "
                                                + e.getClass().getSimpleName() + ": " + e.getMessage());
                        }
                    }
                }
                else if (method.isAnnotationPresent(ParameterizedTest.class) && method.isAnnotationPresent(ValueSource.class)) {
                    ValueSource valueSource = method.getAnnotation(ValueSource.class);
                    int[] ints = valueSource.ints();

                    for (int param : ints) {
                        totalTests++;
                        Object instance = clazz.getDeclaredConstructor().newInstance();

                        for (Method bm : beforeEachMethods) {
                            bm.setAccessible(true);
                            bm.invoke(instance);
                        }

                        long start = System.currentTimeMillis();
                        try {
                            method.setAccessible(true);
                            method.invoke(instance, param);
                            long duration = System.currentTimeMillis() - start;
                            totalTime += duration;
                            passedTests++;
                            results.add("✓ " + clazz.getSimpleName() + "." + method.getName() + "(" + param + ") (" + duration + "ms)");
                        } catch (InvocationTargetException e) {
                            long duration = System.currentTimeMillis() - start;
                            totalTime += duration;
                            failedTests++;
                            Throwable cause = e.getCause() != null ? e.getCause() : e;
                            results.add("✗ " + clazz.getSimpleName() + "." + method.getName() + "(" + param + ") (" + duration + "ms) - " + cause.getClass().getSimpleName() + ": " + cause.getMessage());
                        } catch (Exception e) {
                            long duration = System.currentTimeMillis() - start;
                            totalTime += duration;
                            failedTests++;
                            results.add("✗ " + clazz.getSimpleName() + "." + method.getName() + "(" + param + ") (" + duration + "ms) - " + e.getClass().getSimpleName() + ": " + e.getMessage());
                        }

                        for (Method am : afterEachMethods) {
                            am.setAccessible(true);
                            am.invoke(instance);
                        }
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

    public static List<Class<?>> findClasses(String packageName) throws ClassNotFoundException {
        String path = packageName.replace('.', '/');
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        URL resource = classLoader.getResource(path);
        if (resource == null) {
            // Пакет может отсутствовать — просто вернём пустой список
            return List.of();
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
