package com.lsm.idea_print.controller;
import com.lsm.idea_print.service.McpService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mcp")
@RequiredArgsConstructor
public class McpController {
    
    private final McpService mcpService;

    @PostMapping("/run")
    public ResponseEntity<String> runMcpPipeline() {
        try {
            mcpService.executeFullPipeline();
            return ResponseEntity.ok("✅ MCP Pipeline executed successfully");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("❌ MCP Pipeline failed: " + e.getMessage());
        }
    }
    
    @PostMapping("/test")
    public ResponseEntity<String> testMcpPipeline() {
        try {
            mcpService.executeForTesting();
            return ResponseEntity.ok("✅ MCP Pipeline test completed");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("❌ MCP Pipeline test failed: " + e.getMessage());
        }
    }
}