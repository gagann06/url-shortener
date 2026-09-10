package io.github.gagann06.urlshortener;

import jakarta.validation.constraints.NotBlank;

public record CreateUrlRequest(@NotBlank String url) {}
