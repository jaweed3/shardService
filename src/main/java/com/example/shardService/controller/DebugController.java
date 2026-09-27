package com.example.shardService.controller;

import java.util.Map;

import com.example.shardService.router.ShardRouter;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/debug")
public class DebugController {

    private final ShardRouter router;

    DebugController(ShardRouter router) {
        this.router = router;
    }

    @GetMapping("/count")
    Map<String, Long> count() {
        return router.countPerShard();
    }

    @GetMapping("/debug/route/{key}")
    Map<String, String> route(@PathVariable String key) {
        return Map.of("key", key, "node", router.routeNode(key));
    }
}
