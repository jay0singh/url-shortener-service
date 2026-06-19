package com.project.urlshortener.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UrlRequestDTO {

    @NotBlank(message = "URL must not be blank")
    private String url;

    @Min(value = 1, message = "ttlDays must be at least 1")
    @Max(value = 365, message = "ttlDays must be at most 365")
    private Integer ttlDays;

}
