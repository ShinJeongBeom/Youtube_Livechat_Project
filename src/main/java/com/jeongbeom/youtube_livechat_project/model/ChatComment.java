package com.jeongbeom.youtube_livechat_project.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChatComment {
    private String author;
    private String message;
    private String time;

    public ChatComment(String author, String message, String time) {
        this.author = author;
        this.message = message;
        this.time = time;
    }

}
