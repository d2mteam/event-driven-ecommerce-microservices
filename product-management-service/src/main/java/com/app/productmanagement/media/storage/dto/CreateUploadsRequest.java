package com.app.productmanagement.media.storage.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateUploadsRequest(
        @NotEmpty
        @Size(max = 8, message = "You can upload up to 8 images at a time")
        List<@Pattern(regexp = "image/(jpeg|png|webp)") String> mimeTypes) {
}
