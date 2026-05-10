package com.jeongbeom.youtube_livechat_project.controller;

import com.jeongbeom.youtube_livechat_project.model.ChatComment;
import com.jeongbeom.youtube_livechat_project.service.ChatService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/filter")
    public List<ChatComment> getUserComments(
            @RequestParam String videoId,
            @RequestParam(required = false) String username
    ) throws Exception {
        return chatService.getUserComments(videoId, username);
    }


    @GetMapping("/download")
    public ResponseEntity<String> downloadLiveChat(@RequestParam String videoId) {
        try {
            Path chatFile = chatService.downloadLiveChat(videoId);
            return ResponseEntity.ok("라이브 채팅 저장 완료: " + chatFile);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("라이브 채팅 저장 중 오류: " + e.getMessage());
        }
    }


    @GetMapping("/filter/excel")
    public ResponseEntity<String> saveFilteredChatToExcel(
            @RequestParam String videoId,
            @RequestParam(required = false) String username // ✅ 옵션 처리
    ) {
        try {
            List<ChatComment> comments = chatService.getUserComments(videoId, username);
            Path outputPath = chatService.saveCommentsToExcel(comments, videoId, (username != null ? username : "all"));
            return ResponseEntity.ok("엑셀 파일로 저장 완료: " + outputPath);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("엑셀 저장 중 오류: " + e.getMessage());
        }
    }


}
