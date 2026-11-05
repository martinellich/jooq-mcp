package ch.martinelli.jooqmcp.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Splits the single-file Markdown jOOQ manual into one {@link Section} per heading.
 * <p>
 * Manual sections are the headings preceded by an {@code <a id="..."></a>} anchor. Headings without
 * an anchor are subheadings of the enclosing manual section and share its id. Everything before the
 * first manual section, and the table of contents, is skipped.
 */
final class ManualMarkdownParser {

    record Section(String id, String title, String breadcrumb, String content) {
    }

    private record Heading(int level, String title) {
    }

    private static final String CODE_FENCE = "```";
    private static final String TABLE_OF_CONTENTS_ID = "toc";
    private static final Pattern ANCHOR = Pattern.compile("^<a id=\"([^\"]+)\"></a>$");
    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.+)$");
    private static final Pattern SECTION_NUMBER = Pattern.compile("^\\d+(\\.\\d+)*\\.\\s+");
    private static final Pattern SOURCE_LINE = Pattern.compile("^Source: https?://\\S+(?: · (.+))?$");
    private static final Pattern INTERNAL_LINK = Pattern.compile("\\[([^]]*)]\\(#[^)]*\\)");

    private final List<Section> sections = new ArrayList<>();
    private final Deque<Heading> headings = new ArrayDeque<>();
    private final StringBuilder content = new StringBuilder();
    private String sectionId;
    private int sectionLevel;

    private ManualMarkdownParser() {
    }

    static List<Section> parse(BufferedReader reader) throws IOException {
        ManualMarkdownParser parser = new ManualMarkdownParser();
        parser.read(reader);
        return parser.sections;
    }

    private void read(BufferedReader reader) throws IOException {
        boolean inCodeBlock = false;
        String pendingAnchor = null;

        for (String line = reader.readLine(); line != null; line = reader.readLine()) {
            if (line.startsWith(CODE_FENCE)) {
                inCodeBlock = !inCodeBlock;
                appendContent(line);
            } else if (inCodeBlock) {
                appendContent(line);
            } else {
                Matcher anchor = ANCHOR.matcher(line);
                Matcher heading = HEADING.matcher(line);
                Matcher source = SOURCE_LINE.matcher(line);
                if (anchor.matches()) {
                    pendingAnchor = anchor.group(1);
                } else if (heading.matches()) {
                    startHeading(pendingAnchor, heading.group(1).length(), heading.group(2).trim());
                    pendingAnchor = null;
                } else if (source.matches()) {
                    // Drop the URL, but keep remarks like the supported editions
                    if (source.group(1) != null) {
                        appendContent(source.group(1));
                    }
                } else {
                    appendContent(INTERNAL_LINK.matcher(line).replaceAll("$1"));
                }
            }
        }
        flushSection();
    }

    private void startHeading(String anchor, int level, String title) {
        flushSection();

        int effectiveLevel = level;
        if (anchor != null) {
            sectionId = TABLE_OF_CONTENTS_ID.equals(anchor) ? null : anchor;
            sectionLevel = level;
        } else {
            // Markdown stops at six levels, so a subheading can sit on the level of its own section
            effectiveLevel = Math.max(level, sectionLevel + 1);
        }

        if (sectionId == null) {
            return;
        }

        while (!headings.isEmpty() && headings.peekLast().level() >= effectiveLevel) {
            headings.removeLast();
        }
        headings.addLast(new Heading(effectiveLevel, SECTION_NUMBER.matcher(title).replaceFirst("")));
    }

    private void appendContent(String line) {
        if (sectionId != null) {
            content.append(line).append('\n');
        }
    }

    private void flushSection() {
        String text = content.toString().strip();
        content.setLength(0);

        if (sectionId != null && !text.isEmpty()) {
            String breadcrumb = headings.stream().map(Heading::title).collect(Collectors.joining(" > "));
            sections.add(new Section(sectionId, headings.peekLast().title(), breadcrumb, text));
        }
    }
}
