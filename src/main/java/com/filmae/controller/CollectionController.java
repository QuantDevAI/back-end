package com.filmae.controller;

import com.filmae.dto.collection.CollectionMovieResponse;
import com.filmae.dto.collection.CollectionResponse;
import com.filmae.dto.collection.UpsertCollectionMovieRequest;
import com.filmae.dto.common.ApiResponse;
import com.filmae.security.CurrentUser;
import com.filmae.service.collection.CollectionFilter;
import com.filmae.service.collection.CollectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me/collection")
@Tag(name = "Coleção")
@RequiredArgsConstructor
public class CollectionController {

    private final CollectionService collectionService;

    @Operation(summary = "Coleção do usuário (filter = all | watchlist | watched | favorite | rewatch)")
    @GetMapping
    public ApiResponse<CollectionResponse> collection(@CurrentUser Long userId,
                                                      @RequestParam(required = false) String filter) {
        return ApiResponse.of(collectionService.getCollection(userId, CollectionFilter.parse(filter)));
    }

    @Operation(summary = "Estado de um filme na coleção")
    @GetMapping("/{tconst}")
    public ApiResponse<CollectionMovieResponse> movie(@CurrentUser Long userId, @PathVariable String tconst) {
        return ApiResponse.of(collectionService.getMovie(userId, tconst));
    }

    @Operation(summary = "Atualiza o estado do filme (parcial: campos omitidos ficam como estão)")
    @PutMapping("/{tconst}")
    public ApiResponse<CollectionMovieResponse> upsert(@CurrentUser Long userId,
                                                       @PathVariable String tconst,
                                                       @RequestBody(required = false) UpsertCollectionMovieRequest request) {
        return ApiResponse.of(collectionService.upsert(userId, tconst, request));
    }

    @Operation(summary = "Remove da coleção (zera os quatro estados e mantém a nota)")
    @DeleteMapping("/{tconst}")
    public ApiResponse<CollectionMovieResponse> remove(@CurrentUser Long userId, @PathVariable String tconst) {
        return ApiResponse.of(collectionService.remove(userId, tconst));
    }
}
