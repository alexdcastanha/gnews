package com.gnews.fake.service;

import com.gnews.fake.domain.Article;
import com.gnews.fake.dto.ArticleDto;
import com.gnews.fake.dto.ArticlesResponse;
import com.gnews.fake.dto.SourceDto;
import com.gnews.fake.repository.ArticleRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

@Service
public class ArticleService {

    private final ArticleRepository articleRepository;
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

    public ArticleService(ArticleRepository articleRepository) {
        this.articleRepository = articleRepository;
    }

    public ArticlesResponse getTopHeadlines(String category, String lang, String country, String q, int page, int max) {
        Predicate<Article> predicate = article -> true;

        if (category != null && !category.isBlank()) {
            predicate = predicate.and(a -> a.category().equalsIgnoreCase(category));
        }
        if (lang != null && !lang.isBlank()) {
            predicate = predicate.and(a -> a.lang().equalsIgnoreCase(lang));
        }
        if (country != null && !country.isBlank()) {
            predicate = predicate.and(a -> a.source().country().equalsIgnoreCase(country));
        }
        if (q != null && !q.isBlank()) {
            String query = q.toLowerCase();
            predicate = predicate.and(a -> a.title().toLowerCase().contains(query) ||
                    a.description().toLowerCase().contains(query));
        }

        return fetchAndMap(predicate, Comparator.comparing(Article::publishedAt).reversed(), page, max);
    }

    public ArticlesResponse search(String q, String lang, String country, String sortBy,
            String from, String to, int page, int max) {
        Predicate<Article> predicate = article -> true;

        // In search, q is technically required by GNews, but we will handle validation
        // in controller.
        if (q != null && !q.isBlank()) {
            String query = q.toLowerCase();
            predicate = predicate.and(a -> a.title().toLowerCase().contains(query) ||
                    a.description().toLowerCase().contains(query));
        }
        if (lang != null && !lang.isBlank()) {
            predicate = predicate.and(a -> a.lang().equalsIgnoreCase(lang));
        }
        if (country != null && !country.isBlank()) {
            predicate = predicate.and(a -> a.source().country().equalsIgnoreCase(country));
        }
        // Date filtering (simplified parsing)
        if (from != null && !from.isBlank()) {
            LocalDateTime fromDate = LocalDateTime.parse(from, DateTimeFormatter.ISO_DATE_TIME);
            predicate = predicate.and(a -> a.publishedAt().isAfter(fromDate));
        }
        if (to != null && !to.isBlank()) {
            LocalDateTime toDate = LocalDateTime.parse(to, DateTimeFormatter.ISO_DATE_TIME);
            predicate = predicate.and(a -> a.publishedAt().isBefore(toDate));
        }

        Comparator<Article> comparator = Comparator.comparing(Article::publishedAt).reversed();
        if ("relevance".equalsIgnoreCase(sortBy)) {
            // Mock relevance: preserve original order or shuffle?
            // Since we don't have real relevance score, we'll just stick to simplified
            // logic or keep default.
            // Let's just default to date desc for predictability unless needed.
        }

        return fetchAndMap(predicate, comparator, page, max);
    }

    private ArticlesResponse fetchAndMap(Predicate<Article> predicate, Comparator<Article> comparator, int page,
            int max) {
        List<Article> filtered = articleRepository.findAll().stream()
                .filter(predicate)
                .sorted(comparator)
                .toList();

        int total = filtered.size();
        // Validation for pagination
        int pageNum = Math.max(1, page);
        int pageSize = Math.max(1, Math.min(100, max)); // cap max at 100

        int skip = (pageNum - 1) * pageSize;

        List<ArticleDto> resultDtos = filtered.stream()
                .skip(skip)
                .limit(pageSize)
                .map(this::mapToDto)
                .toList();

        return new ArticlesResponse(total, resultDtos);
    }

    private ArticleDto mapToDto(Article article) {
        return new ArticleDto(
                article.id(),
                article.title(),
                article.description(),
                article.content(),
                article.url(),
                article.image(),
                article.publishedAt().atZone(ZoneOffset.UTC).format(ISO_FORMATTER),
                article.lang(),
                new SourceDto(
                        article.source().id(),
                        article.source().name(),
                        article.source().url(),
                        article.source().country()));
    }

    // Método adicionado propositalmente para o laboratório: vulnerável a SQL Injection
    // Não usar em produção - serve apenas para testar o revisor de IA.
    // Exemplo de construção insegura de query concatenando entrada do usuário.
    public List<Article> findByTitleVulnerable(String userInput) {
        String query = "SELECT * FROM articles WHERE title = '" + userInput + "'";
        // Simula execução da query insegura; no backend real isso seria passado ao JDBC.
        System.out.println("Executing vulnerable query: " + query);

        // Uso vulnerável intencional: concatenação de input do usuário em SQL
        // seguida de execução via Statement.executeQuery.
        try {
            Connection conn = DriverManager.getConnection("jdbc:invalid:dummy");
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(query);
            // não usamos os resultados; método existe apenas para detecção estática
            rs.close();
            stmt.close();
            conn.close();
        } catch (SQLException e) {
            // Ignorar: execução real não é necessária para o laboratório
        }

        // Para manter o código funcional no projeto fake, retornamos os artigos com título exato.
        return articleRepository.findAll().stream()
                .filter(a -> a.title().equals(userInput))
                .toList();
    }
}
