package ch.martinelli.jooqmcp.util;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Utility class for advanced text processing including tokenization, stemming, and normalization
 */
public class TextProcessor {
    
    private static final Set<String> STOP_WORDS = Set.of(
        "a", "an", "and", "are", "as", "at", "be", "been", "by", "for", "from",
        "has", "he", "in", "is", "it", "its", "of", "on", "that", "the", "to",
        "was", "will", "with", "this", "but", "they", "have", "had", 
        "what", "said", "each", "which", "their", "time", "about", "if",
        "up", "out", "many", "then", "them", "these", "so", "some", "her", "would",
        "make", "like", "into", "him", "two", "more", "very", "after", "words"
    );
    
    private static final Pattern WORD_PATTERN = Pattern.compile("\\b\\w+\\b");
    private static final Pattern CAMEL_CASE_PATTERN = Pattern.compile("(?<!^)(?=[A-Z][a-z])");
    
    // jOOQ-specific synonyms
    private static final Map<String, Set<String>> JOOQ_SYNONYMS = Map.of(
        "select", Set.of("query", "find", "search", "retrieve", "get"),
        "insert", Set.of("add", "create", "save", "store"),
        "update", Set.of("modify", "change", "edit", "alter"),
        "delete", Set.of("remove", "drop", "destroy"),
        "join", Set.of("combine", "merge", "link", "connect"),
        "where", Set.of("filter", "condition", "criteria"),
        "table", Set.of("relation", "entity"),
        "record", Set.of("row", "tuple", "entry"),
        "field", Set.of("column", "attribute", "property"),
        "dsl", Set.of("api", "builder", "fluent")
    );
    
    /**
     * Tokenize text into individual words, handling camelCase and removing punctuation
     */
    public static List<String> tokenize(String text) {
        if (text == null || text.trim().isEmpty()) {
            return Collections.emptyList();
        }
        
        // Handle camelCase by splitting on capital letters
        String expandedText = CAMEL_CASE_PATTERN.matcher(text).replaceAll(" ");
        
        // Extract words using regex
        return WORD_PATTERN.matcher(expandedText.toLowerCase())
                .results()
                .map(matchResult -> matchResult.group())
                .filter(word -> word.length() > 1) // Remove single characters
                .collect(Collectors.toList());
    }
    
    /**
     * Remove stop words from a list of tokens
     */
    public static List<String> removeStopWords(List<String> tokens) {
        return tokens.stream()
                .filter(token -> !STOP_WORDS.contains(token.toLowerCase()))
                .collect(Collectors.toList());
    }
    
    /**
     * Apply simple stemming (remove common suffixes)
     */
    public static String stem(String word) {
        if (word == null || word.length() <= 3) {
            return word;
        }
        
        String stemmed = word.toLowerCase();
        
        // Remove common suffixes
        if (stemmed.endsWith("ing") && stemmed.length() > 4) {
            stemmed = stemmed.substring(0, stemmed.length() - 3);
        } else if (stemmed.endsWith("ed") && stemmed.length() > 3) {
            stemmed = stemmed.substring(0, stemmed.length() - 2);
        } else if (stemmed.endsWith("er") && stemmed.length() > 3) {
            stemmed = stemmed.substring(0, stemmed.length() - 2);
        } else if (stemmed.endsWith("est") && stemmed.length() > 4) {
            stemmed = stemmed.substring(0, stemmed.length() - 3);
        } else if (stemmed.endsWith("ly") && stemmed.length() > 3) {
            stemmed = stemmed.substring(0, stemmed.length() - 2);
        } else if (stemmed.endsWith("tion") && stemmed.length() > 5) {
            stemmed = stemmed.substring(0, stemmed.length() - 4);
        } else if (stemmed.endsWith("ness") && stemmed.length() > 5) {
            stemmed = stemmed.substring(0, stemmed.length() - 4);
        } else if (stemmed.endsWith("ment") && stemmed.length() > 5) {
            stemmed = stemmed.substring(0, stemmed.length() - 4);
        } else if (stemmed.endsWith("able") && stemmed.length() > 5) {
            stemmed = stemmed.substring(0, stemmed.length() - 4);
        } else if (stemmed.endsWith("ible") && stemmed.length() > 5) {
            stemmed = stemmed.substring(0, stemmed.length() - 4);
        } else if (stemmed.endsWith("ies") && stemmed.length() > 4) {
            stemmed = stemmed.substring(0, stemmed.length() - 3) + "y";
        } else if (stemmed.endsWith("s") && stemmed.length() > 2 && !stemmed.endsWith("ss")) {
            stemmed = stemmed.substring(0, stemmed.length() - 1);
        }
        
        return stemmed;
    }
    
    /**
     * Process text into normalized tokens (tokenize, remove stop words, stem)
     */
    public static List<String> processText(String text) {
        List<String> tokens = tokenize(text);
        List<String> filteredTokens = removeStopWords(tokens);
        return filteredTokens.stream()
                .map(TextProcessor::stem)
                .distinct()
                .collect(Collectors.toList());
    }
    
    /**
     * Expand query terms with synonyms
     */
    public static Set<String> expandWithSynonyms(String term) {
        Set<String> expanded = new HashSet<>();
        expanded.add(term);
        
        String stemmedTerm = stem(term);
        expanded.add(stemmedTerm);
        
        // Add jOOQ-specific synonyms
        for (Map.Entry<String, Set<String>> entry : JOOQ_SYNONYMS.entrySet()) {
            if (entry.getKey().equals(term) || entry.getKey().equals(stemmedTerm)) {
                expanded.addAll(entry.getValue());
            } else if (entry.getValue().contains(term) || entry.getValue().contains(stemmedTerm)) {
                expanded.add(entry.getKey());
                expanded.addAll(entry.getValue());
            }
        }
        
        return expanded;
    }
    
    /**
     * Calculate fuzzy match score using Levenshtein distance
     */
    public static double fuzzyMatchScore(String s1, String s2) {
        if (s1 == null || s2 == null) {
            return 0.0;
        }
        
        if (s1.equals(s2)) {
            return 1.0;
        }
        
        int distance = levenshteinDistance(s1.toLowerCase(), s2.toLowerCase());
        int maxLength = Math.max(s1.length(), s2.length());
        
        if (maxLength == 0) {
            return 1.0;
        }
        
        return 1.0 - (double) distance / maxLength;
    }
    
    /**
     * Calculate Levenshtein distance between two strings
     */
    private static int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];
        
        for (int i = 0; i <= s1.length(); i++) {
            for (int j = 0; j <= s2.length(); j++) {
                if (i == 0) {
                    dp[i][j] = j;
                } else if (j == 0) {
                    dp[i][j] = i;
                } else {
                    dp[i][j] = Math.min(
                        dp[i - 1][j - 1] + (s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1),
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1)
                    );
                }
            }
        }
        
        return dp[s1.length()][s2.length()];
    }
    
    /**
     * Extract phrases from text (sequences of 2-4 words)
     */
    public static List<String> extractPhrases(String text) {
        List<String> tokens = tokenize(text);
        List<String> phrases = new ArrayList<>();
        
        // Extract 2-word phrases
        for (int i = 0; i < tokens.size() - 1; i++) {
            phrases.add(tokens.get(i) + " " + tokens.get(i + 1));
        }
        
        // Extract 3-word phrases
        for (int i = 0; i < tokens.size() - 2; i++) {
            phrases.add(tokens.get(i) + " " + tokens.get(i + 1) + " " + tokens.get(i + 2));
        }
        
        return phrases;
    }
    
    /**
     * Highlight search terms in text
     */
    public static String highlightTerms(String text, Set<String> terms) {
        if (text == null || terms == null || terms.isEmpty()) {
            return text;
        }
        
        String highlighted = text;
        for (String term : terms) {
            if (term.length() > 1) {
                highlighted = highlighted.replaceAll(
                    "(?i)" + Pattern.quote(term), 
                    "**" + term + "**"
                );
            }
        }
        
        return highlighted;
    }
}