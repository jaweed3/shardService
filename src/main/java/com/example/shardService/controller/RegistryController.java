package com.example.shardService.controller;

import java.util.List;
import java.util.Map;

import com.example.shardService.registry.ShardRegistry;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/registry")
public class RegistryController {

    private final ShardRegistry registry;

    public RegistryController(ShardRegistry registry) {
        this.registry = registry;
    }

    @PostMapping("/register/{nodeId}")
    void register(@PathVariable String nodeId) {
        registry.register(nodeId);
    }

