package com.jeongbeom.youtube_livechat_project.service;

import com.jeongbeom.youtube_livechat_project.model.ChatComment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class ChatService {
    private static final Pattern SAFE_VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{6,32}");
    private static final Pattern UNSAFE_FILENAME_CHARS = Pattern.compile("[^A-Za-z0-9._-]");
    private static final Path OUTPUT_DIR = Path.of("output");
    private static final Path LOCAL_YT_DLP = Path.of(System.getProperty("user.home"), ".local", "bin", "yt-dlp");

    private final String ytDlpPath;

    public ChatService(@Value("${app.yt-dlp.path:yt-dlp}") String ytDlpPath) {
        this.ytDlpPath = ytDlpPath;
    }

    public List<ChatComment> getUserComments(String videoId, String targetUsername) throws Exception {
        String safeVideoId = validateVideoId(videoId);
        File chatFile = ensureLiveChatFile(safeVideoId).toFile();

        ObjectMapper mapper = new ObjectMapper();
        List<ChatComment> result = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(chatFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    JsonNode node = mapper.readTree(line);
                    JsonNode replayAction = node.get("replayChatItemAction");
                    if (replayAction == null) continue;

                    for (JsonNode action : replayAction.get("actions")) {
                        JsonNode addChatItemAction = action.get("addChatItemAction");
                        if (addChatItemAction == null) continue;

                        JsonNode item = addChatItemAction.get("item");
                        if (item == null || !item.has("liveChatTextMessageRenderer")) continue;

                        JsonNode chat = item.get("liveChatTextMessageRenderer");

                        String author = chat.path("authorName").path("simpleText").asText().trim();
                        System.out.println("채팅 작성자: " + author);

                        // 필터링 조건 없으면 전체 유저
                        if (targetUsername != null && !targetUsername.isBlank()) {
                            if (!author.replaceAll("\\s", "").contains(targetUsername.replaceAll("\\s", ""))) continue;
                        }

                        // 메시지 조합 (emoji + text)
                        StringBuilder messageBuilder = new StringBuilder();
                        for (JsonNode run : chat.path("message").path("runs")) {
                            if (run.has("text")) {
                                messageBuilder.append(run.path("text").asText());
                            } else if (run.has("emoji")) {
                                JsonNode shortcuts = run.path("emoji").path("shortcuts");
                                if (shortcuts.isArray() && shortcuts.size() > 0) {
                                    messageBuilder.append(shortcuts.get(0).asText());
                                }
                            }
                        }

                        String message = messageBuilder.toString();
                        String timestamp = chat.path("timestampUsec").asText();

                        result.add(new ChatComment(author, message, timestamp));
                    }

                } catch (Exception e) {
                    // JSON 파싱 실패 무시
                }
            }
        }

        System.out.println("필터링된 채팅 수: " + result.size());
        return result;
    }

    public Path downloadLiveChat(String videoId) throws Exception {
        String safeVideoId = validateVideoId(videoId);
        return downloadLiveChatFile(safeVideoId);
    }


    public Path saveCommentsToExcel(List<ChatComment> comments, String videoId, String username) throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Filtered Chat");

            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("작성자");
            header.createCell(1).setCellValue("내용");
            header.createCell(2).setCellValue("타임스탬프");

            for (int i = 0; i < comments.size(); i++) {
                ChatComment comment = comments.get(i);
                Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(comment.getAuthor());
                row.createCell(1).setCellValue(comment.getMessage());
                row.createCell(2).setCellValue(comment.getTime());
            }

            String safeVideoId = validateVideoId(videoId);
            String safeUsername = sanitizeFilename(username);
            Files.createDirectories(OUTPUT_DIR);
            Path outputPath = OUTPUT_DIR.resolve(safeVideoId + "_" + safeUsername + "_filtered_chat.xlsx").normalize();
            if (!outputPath.startsWith(OUTPUT_DIR)) {
                throw new IllegalArgumentException("올바르지 않은 파일명입니다.");
            }

            try (FileOutputStream fileOut = new FileOutputStream(outputPath.toFile())) {
                workbook.write(fileOut);
            }

            return outputPath;
        }
    }

    private String validateVideoId(String videoId) {
        if (videoId == null || !SAFE_VIDEO_ID.matcher(videoId).matches()) {
            throw new IllegalArgumentException("올바르지 않은 videoId입니다.");
        }

        return videoId;
    }

    private String sanitizeFilename(String value) {
        if (value == null || value.isBlank()) {
            return "all";
        }

        String sanitized = UNSAFE_FILENAME_CHARS.matcher(value.trim()).replaceAll("_");
        return sanitized.isBlank() ? "all" : sanitized;
    }

    private Path ensureLiveChatFile(String videoId) throws Exception {
        Path chatFile = liveChatFile(videoId);
        if (Files.exists(chatFile)) {
            return chatFile;
        }

        return downloadLiveChatFile(videoId);
    }

    private Path downloadLiveChatFile(String videoId) throws Exception {
        String executable = resolveYtDlpExecutable();
        String videoUrl = "https://www.youtube.com/watch?v=" + videoId;

        ProcessBuilder processBuilder = new ProcessBuilder(
                executable,
                "--skip-download",
                "--write-subs",
                "--sub-langs",
                "live_chat",
                "-o",
                "%(id)s.%(ext)s",
                videoUrl
        );
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();
        String output;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            StringBuilder outputBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                outputBuilder.append(line).append(System.lineSeparator());
            }
            output = outputBuilder.toString();
        }

        int exitCode = process.waitFor();
        Path chatFile = liveChatFile(videoId);
        if (exitCode != 0 || !Files.exists(chatFile)) {
            throw new IllegalStateException("라이브 채팅 다운로드 실패: " + output.trim());
        }

        return chatFile;
    }

    private Path liveChatFile(String videoId) {
        return Path.of(videoId + ".live_chat.json");
    }

    private String resolveYtDlpExecutable() {
        if (!"yt-dlp".equals(ytDlpPath)) {
            return ytDlpPath;
        }

        if (Files.isExecutable(LOCAL_YT_DLP)) {
            return LOCAL_YT_DLP.toString();
        }

        return ytDlpPath;
    }
}
