package io.github.gagann06.urlshortener;

import java.security.SecureRandom;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

@Service
public class UrlService {
    
    private final UrlRepository urlRepository;
    private static final int CODE_LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    public UrlService(UrlRepository urlRepository) {
        this.urlRepository = urlRepository;
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < CODE_LENGTH; i++) {
            char c = ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length()));
            sb.append(c);
        }

        return sb.toString();
    }

    @Transactional 
    public Url shorten(String originalUrl) {
        String shortCode = generateCode();
        Url url = new Url(shortCode, originalUrl);

        return urlRepository.save(url);
    }

    @Transactional(readOnly = true)
    public Optional<Url> resolve(String shortCode) {
        Optional<Url> url = urlRepository.findByShortCode(shortCode);
        return url;
    }
}
