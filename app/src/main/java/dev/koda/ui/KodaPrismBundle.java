package dev.koda.ui;

import io.noties.prism4j.annotations.PrismBundle;

@PrismBundle(
    include = {
        "java", "kotlin", "javascript", "python", "bash", "json", "xml",
        "c", "cpp", "css", "go", "rust", "sql", "yaml", "markdown",
        "typescript", "swift", "ruby", "php"
    },
    grammarLocatorClassName = ".KodaGrammarLocator"
)
public class KodaPrismBundle {
    // Annotation processor generates KodaGrammarLocator
}
