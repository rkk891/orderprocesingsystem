package com.rkk.orderprocessing;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;

/**
 * Guards the small package boundary contract without introducing an architecture-test dependency.
 */
class ArchitectureRulesTest {

	private static final Path MAIN_SOURCES = Path.of("src/main/java");
	private static final Path TEST_SOURCES = Path.of("src/test/java");
	private static final String FEATURE_PACKAGE = "com.rkk.orderprocessing.order.";

	@BeforeAll
	static void sourceRootsExist() {
		assertTrue(Files.isDirectory(MAIN_SOURCES), "Run architecture tests from the Maven module root");
		assertTrue(Files.isDirectory(TEST_SOURCES), "Test source root must exist");
	}

	@Test
	void placeholderPackageIsAbsentFromAllJavaSources() throws IOException {
		String obsoletePackage = "com.example" + ".ordersystem";
		List<String> violations = findViolations(
				Stream.of(MAIN_SOURCES, TEST_SOURCES),
				content -> content.contains(obsoletePackage),
				"references the obsolete scaffold package");

		assertNoViolations(violations);
	}

	@Test
	void apiDependsOnlyOnTheApplicationFeatureBoundary() throws IOException {
		Path apiSources = MAIN_SOURCES.resolve("com/rkk/orderprocessing/order/api");
		List<String> violations = findViolations(
				Stream.of(apiSources),
				content -> content.contains(FEATURE_PACKAGE + "persistence.")
						|| content.contains(FEATURE_PACKAGE + "domain.")
						|| content.contains(FEATURE_PACKAGE + "job."),
				"references a forbidden feature package");

		assertNoViolations(violations);
	}

	@Test
	void jobDependsOnlyOnTheApplicationFeaturePackage() throws IOException {
		Path jobSources = MAIN_SOURCES.resolve("com/rkk/orderprocessing/order/job");
		List<String> violations = new ArrayList<>();

		try (Stream<Path> sources = javaSources(jobSources)) {
			sources.forEach(path -> featureImports(path).stream()
					.filter(importName -> !importName.startsWith(FEATURE_PACKAGE + "application."))
					.forEach(importName -> violations.add(path + " imports " + importName)));
		}

		assertNoViolations(violations);
	}

	@Test
	void domainDoesNotImportFrameworkOrPersistenceTypes() throws IOException {
		Path domainSources = MAIN_SOURCES.resolve("com/rkk/orderprocessing/order/domain");
		List<String> violations = findViolations(
				Stream.of(domainSources),
				content -> content.contains("org.springframework.")
						|| content.contains("jakarta.")
						|| content.contains(FEATURE_PACKAGE + "persistence."),
				"imports a framework or persistence type");

		assertNoViolations(violations);
	}

	@Test
	void applicationDoesNotDependOnApiOrJob() throws IOException {
		Path applicationSources = MAIN_SOURCES.resolve("com/rkk/orderprocessing/order/application");
		List<String> violations = findViolations(
				Stream.of(applicationSources),
				content -> content.contains(FEATURE_PACKAGE + "api.")
						|| content.contains(FEATURE_PACKAGE + "job."),
				"references an inbound adapter");

		assertNoViolations(violations);
	}

	@Test
	void persistenceDependsOnlyOnDomainWithinTheFeature() throws IOException {
		Path persistenceSources = MAIN_SOURCES.resolve("com/rkk/orderprocessing/order/persistence");
		List<String> violations = findViolations(
				Stream.of(persistenceSources),
				content -> content.contains(FEATURE_PACKAGE + "api.")
						|| content.contains(FEATURE_PACKAGE + "application.")
						|| content.contains(FEATURE_PACKAGE + "job."),
				"references a forbidden feature package");

		assertNoViolations(violations);
	}

	@Test
	void springComponentsKeepOnlyFinalInstanceState() throws ClassNotFoundException {
		ClassPathScanningCandidateComponentProvider scanner =
				new ClassPathScanningCandidateComponentProvider(false);
		scanner.addIncludeFilter(new AnnotationTypeFilter(Component.class));
		var components = scanner.findCandidateComponents("com.rkk.orderprocessing");
		assertTrue(!components.isEmpty(), "Component scan must not pass vacuously");

		List<String> violations = new ArrayList<>();
		for (var component : components) {
			Class<?> type = Class.forName(component.getBeanClassName());
			for (Field field : type.getDeclaredFields()) {
				int modifiers = field.getModifiers();
				if (!Modifier.isFinal(modifiers)) {
					violations.add(type.getName() + " has non-final field " + field.getName());
				}
			}
		}

		assertNoViolations(violations);
	}

	/**
	 * Finds Java sources below a path, returning an empty stream before a package exists.
	 */
	private static Stream<Path> javaSources(Path root) throws IOException {
		if (Files.notExists(root)) {
			return Stream.empty();
		}
		return Files.walk(root).filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"));
	}

	/**
	 * Reads feature-local imports from one Java source file.
	 */
	private static List<String> featureImports(Path source) {
		try {
			return Files.readAllLines(source).stream()
					.map(String::strip)
					.filter(line -> line.startsWith("import " + FEATURE_PACKAGE))
					.map(line -> line.substring("import ".length(), line.length() - 1))
					.toList();
		}
		catch (IOException exception) {
			throw new IllegalStateException("Cannot inspect " + source, exception);
		}
	}

	/**
	 * Applies a content rule to every Java source below the supplied roots.
	 */
	private static List<String> findViolations(
			Stream<Path> roots, Predicate<String> forbiddenContent, String description) throws IOException {
		List<String> violations = new ArrayList<>();
		for (Path root : roots.toList()) {
			try (Stream<Path> sources = javaSources(root)) {
				sources.forEach(path -> {
					try {
						if (forbiddenContent.test(Files.readString(path))) {
							violations.add(path + " " + description);
						}
					}
					catch (IOException exception) {
						throw new IllegalStateException("Cannot inspect " + path, exception);
					}
				});
			}
		}
		return violations;
	}

	/**
	 * Reports every boundary violation in one actionable assertion.
	 */
	private static void assertNoViolations(List<String> violations) {
		assertTrue(violations.isEmpty(), () -> "Architecture violations:\n" + String.join("\n", violations));
	}

}
