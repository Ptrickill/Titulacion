package ec.rehabelite.patients;

/**
 * A legal text in force (MEJ-06): the privacy notice or the text of a consent, versioned in
 * {@code resources/legal/*.md}. {@code content} is Markdown (headings, paragraphs and lists).
 */
public record LegalText(String key, String version, String title, String content) {
}
