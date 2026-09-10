package io.github.gagann06.urlshortener;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import java.net.URI;
import java.util.Optional;

@RestController 
public class UrlController {

    private final UrlService urlService;

    public UrlController(UrlService urlService) {
        this.urlService = urlService;
    }
    
    @PostMapping("/api/urls")
    public ResponseEntity<CreateUrlResponse> create(@Valid @RequestBody CreateUrlRequest request) {
        Url url = urlService.shorten(request.url());
        String code = url.getShortCode();
        return ResponseEntity.created(URI.create("/" + code)).body(new CreateUrlResponse(code));
    }

    @GetMapping("/{code}")
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        Optional<Url> found = urlService.resolve(code);

        if (found.isEmpty()) {
            return ResponseEntity.notFound().build();
        } 
        Url url = found.get();
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url.getOriginalUrl())).build();
    }
}
