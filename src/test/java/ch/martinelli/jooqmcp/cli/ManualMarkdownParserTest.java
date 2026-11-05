package ch.martinelli.jooqmcp.cli;

import ch.martinelli.jooqmcp.cli.ManualMarkdownParser.Section;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManualMarkdownParserTest {

    private static final String MANUAL = """
            # The jOOQ User Manual, version 3.21

            ## Overview

            Front matter

            <a id="toc"></a>
            ## Table of contents

            - [1. SQL building](#sql-building)

            <a id="sql-building"></a>
            ## 1. SQL building

            Source: https://www.jooq.org/doc/3.21/manual/sql-building/

            See [the SELECT statement](#select-statement) or [jOOQ](https://www.jooq.org).

            <a id="select-statement"></a>
            ### 1.1. The SELECT statement

            Source: https://www.jooq.org/doc/3.21/manual/sql-building/select-statement/

            Intro

            ```sql
            # not a heading
            SELECT 1
            ```

            #### Single tables

            Details

            <a id="deep-section"></a>
            ###### 1.1.1.1.1. Deep section

            Deep text

            ###### Oracle

            Oracle text

            <a id="next-section"></a>
            ### 1.2. Next section

            Next text
            """;

    @Test
    void skipsFrontMatterAndTableOfContents() throws IOException {
        assertEquals("sql-building", parse(MANUAL).getFirst().id());
    }

    @Test
    void stripsSectionNumbersFromTitlesAndBreadcrumbs() throws IOException {
        Section section = parse(MANUAL).get(1);

        assertEquals("The SELECT statement", section.title());
        assertEquals("SQL building > The SELECT statement", section.breadcrumb());
    }

    @Test
    void removesSourceLinesAndInternalLinksButKeepsExternalLinks() throws IOException {
        assertEquals("See the SELECT statement or [jOOQ](https://www.jooq.org).", parse(MANUAL).getFirst().content());
    }

    @Test
    void keepsRemarksOfSourceLines() throws IOException {
        List<Section> sections = parse("""
                <a id="commercial"></a>
                ## 1. Commercial

                Source: https://www.jooq.org/doc/3.21/manual/commercial/ · Supported editions: Express, Professional

                Text
                """);

        assertEquals("Supported editions: Express, Professional\n\nText", sections.getFirst().content());
    }

    @Test
    void keepsCodeBlocksAndIgnoresHeadingsInsideThem() throws IOException {
        String content = parse(MANUAL).get(1).content();

        assertTrue(content.contains("```sql\n# not a heading\nSELECT 1\n```"));
    }

    @Test
    void subheadingSharesIdOfEnclosingSection() throws IOException {
        Section section = parse(MANUAL).get(2);

        assertEquals("select-statement", section.id());
        assertEquals("Single tables", section.title());
        assertEquals("SQL building > The SELECT statement > Single tables", section.breadcrumb());
    }

    @Test
    void subheadingOnSameLevelAsItsSectionStaysBelowIt() throws IOException {
        Section section = parse(MANUAL).get(4);

        assertEquals("deep-section", section.id());
        assertTrue(section.breadcrumb().endsWith("Deep section > Oracle"));
    }

    @Test
    void popsBreadcrumbWhenSectionLevelRises() throws IOException {
        Section section = parse(MANUAL).getLast();

        assertEquals("SQL building > Next section", section.breadcrumb());
    }

    @Test
    void skipsHeadingsWithoutContent() throws IOException {
        List<Section> sections = parse("""
                <a id="empty"></a>
                ## 1. Empty

                Source: https://www.jooq.org/doc/3.21/manual/empty/

                <a id="filled"></a>
                ### 1.1. Filled

                Text
                """);

        assertEquals(1, sections.size());
        assertFalse(sections.stream().anyMatch(section -> section.id().equals("empty")));
    }

    private static List<Section> parse(String markdown) throws IOException {
        return ManualMarkdownParser.parse(new BufferedReader(new StringReader(markdown)));
    }
}
