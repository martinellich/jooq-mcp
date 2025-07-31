package ch.martinelli.jooqmcp.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
public class JooqDocumentationCrawler {

    private static final Logger logger = LoggerFactory.getLogger(JooqDocumentationCrawler.class);
    private static final String JOOQ_BASE_URL = "https://www.jooq.org/doc/3.21/manual/";
    
    @Value("${jooq.documentation.crawler.timeout-ms:10000}")
    private int timeoutMs;
    
    @Value("${jooq.documentation.crawler.max-depth:4}")
    private int maxDepth;
    
    @Value("${jooq.documentation.crawler.max-urls-per-section:100}")
    private int maxUrlsPerSection;
    
    @Value("${jooq.documentation.crawler.cache-duration-hours:24}")
    private int cacheDurationHours;

    private final Map<String, DocumentationPage> discoveredPages = new ConcurrentHashMap<>();
    private volatile boolean crawlInProgress = false;
    private volatile long lastCrawlTime = 0;

    public static class DocumentationPage {
        private final String url;
        private final String title;
        private final String breadcrumb;
        private final int depth;
        private final String section;
        private final Set<String> childUrls;

        public DocumentationPage(String url, String title, String breadcrumb, int depth, String section) {
            this.url = url;
            this.title = title;
            this.breadcrumb = breadcrumb;
            this.depth = depth;
            this.section = section;
            this.childUrls = new HashSet<>();
        }

        // Getters
        public String getUrl() { return url; }
        public String getTitle() { return title; }
        public String getBreadcrumb() { return breadcrumb; }
        public int getDepth() { return depth; }
        public String getSection() { return section; }
        public Set<String> getChildUrls() { return childUrls; }
    }

    @Cacheable(value = "documentationUrls", unless = "#result.isEmpty()")
    public Map<String, DocumentationPage> discoverAllUrls() {
        // Return cached results if crawl was done recently
        if (!discoveredPages.isEmpty() && 
            (System.currentTimeMillis() - lastCrawlTime) < TimeUnit.HOURS.toMillis(cacheDurationHours)) {
            return new HashMap<>(discoveredPages);
        }

        // Prevent concurrent crawls
        if (crawlInProgress) {
            logger.info("Crawl already in progress, returning current results");
            return new HashMap<>(discoveredPages);
        }

        try {
            crawlInProgress = true;
            logger.info("Starting documentation URL discovery");
            discoveredPages.clear();

            // Start from the main manual page
            crawlSection(JOOQ_BASE_URL, "jOOQ Manual", "", 0, "root");

            // Also crawl known major sections
            String[] majorSections = {
                "getting-started",
                "sql-building",
                "code-generation",
                "sql-execution",
                "sql-dialects",
                "reference"
            };

            for (String section : majorSections) {
                String sectionUrl = JOOQ_BASE_URL + section + "/";
                crawlSection(sectionUrl, section, "Manual", 1, section);
            }

            lastCrawlTime = System.currentTimeMillis();
            logger.info("Documentation discovery completed. Found {} pages", discoveredPages.size());

        } finally {
            crawlInProgress = false;
        }

        return new HashMap<>(discoveredPages);
    }

    private void crawlSection(String url, String title, String parentBreadcrumb, int depth, String section) {
        if (depth > maxDepth) {
            logger.debug("Max depth reached for URL: {}", url);
            return;
        }

        if (discoveredPages.containsKey(url)) {
            logger.debug("URL already discovered: {}", url);
            return;
        }

        // Limit URLs per section to prevent infinite crawling
        long sectionCount = discoveredPages.values().stream()
            .filter(p -> p.section.equals(section))
            .count();
        if (sectionCount >= maxUrlsPerSection) {
            logger.debug("Max URLs reached for section: {}", section);
            return;
        }

        try {
            logger.debug("Crawling URL at depth {}: {}", depth, url);
            Document doc = Jsoup.connect(url)
                    .timeout(timeoutMs)
                    .userAgent("jOOQ-MCP-Server/1.0")
                    .get();

            // Extract page title
            Element titleElement = doc.selectFirst("h1, h2, title");
            String pageTitle = titleElement != null ? titleElement.text() : title;

            // Build breadcrumb
            String breadcrumb = parentBreadcrumb.isEmpty() ? pageTitle : 
                parentBreadcrumb + " > " + pageTitle;

            // Create page entry
            DocumentationPage page = new DocumentationPage(url, pageTitle, breadcrumb, depth, section);
            discoveredPages.put(url, page);

            // Find subsection links - look in multiple places
            Set<String> subsectionUrls = new HashSet<>();
            
            // 1. Look for navigation links
            Elements navLinks = doc.select("nav a[href], .navigation a[href], .toc a[href], .menu a[href]");
            
            // 2. Look for content links  
            Elements contentLinks = doc.select("article a[href], .content a[href], main a[href]");
            
            // 3. Look for any remaining links
            Elements allLinks = doc.select("a[href]");
            
            // Combine all link sources
            Elements links = new Elements();
            links.addAll(navLinks);
            links.addAll(contentLinks);
            links.addAll(allLinks);

            for (Element link : links) {
                String href = link.attr("href");
                String absoluteUrl = resolveUrl(url, href);

                if (isValidSubsectionUrl(absoluteUrl, url, depth)) {
                    subsectionUrls.add(absoluteUrl);
                    page.getChildUrls().add(absoluteUrl);
                }
            }

            // Recursively crawl subsections
            for (String subsectionUrl : subsectionUrls) {
                // Extract subsection title from link text if possible
                Element linkElement = links.stream()
                    .filter(l -> resolveUrl(url, l.attr("href")).equals(subsectionUrl))
                    .findFirst()
                    .orElse(null);
                
                String subsectionTitle = linkElement != null ? linkElement.text() : "Subsection";
                crawlSection(subsectionUrl, subsectionTitle, breadcrumb, depth + 1, section);
            }

        } catch (IOException e) {
            logger.warn("Failed to crawl URL {}: {}", url, e.getMessage());
        }
    }

    private String resolveUrl(String baseUrl, String href) {
        try {
            // Skip anchor links
            if (href.startsWith("#")) {
                return baseUrl + href;
            }

            URI base = new URI(baseUrl);
            URI resolved = base.resolve(href);
            return resolved.toString();
        } catch (URISyntaxException e) {
            logger.warn("Invalid URL: {} with href: {}", baseUrl, href);
            return "";
        }
    }

    private boolean isValidSubsectionUrl(String url, String parentUrl, int currentDepth) {
        // Must be a jOOQ manual URL
        if (!url.startsWith(JOOQ_BASE_URL)) {
            return false;
        }

        // Skip non-HTML resources and anchors
        if (url.endsWith(".pdf") || url.endsWith(".zip") || url.contains("#")) {
            return false;
        }

        // Skip external links
        if (url.contains("?") || url.contains("javadoc") || url.contains("github")) {
            return false;
        }

        // For deeper crawling, also accept siblings at the same level
        String parentPath = parentUrl.endsWith("/") ? parentUrl : parentUrl + "/";
        String urlPath = url.endsWith("/") ? url : url + "/";
        
        // Check if it's a child or a relevant sibling
        boolean isChild = urlPath.startsWith(parentPath) && !urlPath.equals(parentPath);
        boolean isRelevantSibling = false;
        
        if (!isChild && currentDepth > 0) {
            // Check if it's a sibling (same parent directory)
            String parentDir = parentPath.substring(0, parentPath.lastIndexOf('/', parentPath.length() - 2) + 1);
            isRelevantSibling = urlPath.startsWith(parentDir) && !urlPath.equals(parentPath);
        }
        
        if (!isChild && !isRelevantSibling) {
            return false;
        }

        // Check URL depth
        String relativePath = url.substring(JOOQ_BASE_URL.length());
        long slashCount = relativePath.chars().filter(ch -> ch == '/').count();
        
        return slashCount <= maxDepth;
    }

    public List<String> searchUrls(String query) {
        Map<String, DocumentationPage> pages = discoverAllUrls();
        String lowerQuery = query.toLowerCase();

        return pages.values().stream()
            .filter(page -> 
                page.getTitle().toLowerCase().contains(lowerQuery) ||
                page.getBreadcrumb().toLowerCase().contains(lowerQuery) ||
                page.getUrl().toLowerCase().contains(lowerQuery))
            .sorted(Comparator
                .comparing((DocumentationPage p) -> !p.getTitle().toLowerCase().contains(lowerQuery))
                .thenComparing(DocumentationPage::getDepth))
            .map(DocumentationPage::getUrl)
            .limit(20)
            .collect(Collectors.toList());
    }

    public DocumentationPage getPageInfo(String url) {
        return discoveredPages.get(url);
    }
}