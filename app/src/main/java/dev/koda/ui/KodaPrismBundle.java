package dev.koda.ui;

import io.noties.prism4j.annotations.PrismBundle;

@PrismBundle(
    include = {
        "java", "kotlin", "javascript", "python", "json", "markup",
        "c", "cpp", "css", "go", "sql", "yaml", "markdown",
        "swift", "dart", "csharp", "groovy", "scala"
    },
    grammarLocatorClassName = ".KodaGrammarLocator"
)
public class KodaPrismBundle {
    // Annotation processor generates KodaGrammarLocator
}
