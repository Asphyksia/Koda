package dev.koda.ui;

import io.noties.prism4j.annotations.PrismBundle;

@PrismBundle(
    include = {
        "java", "kotlin", "javascript", "python", "json", "xml",
        "c", "cpp", "css", "go", "sql", "yaml", "markdown",
        "typescript", "swift", "ruby"
    },
    grammarLocatorClassName = ".KodaGrammarLocator"
)
public class KodaPrismBundle {
    // Annotation processor generates KodaGrammarLocator
}
