package com.nineteenhome.lofimusic;

public class RadioStation {
    public String name;      // 电台名字
    public String style;     // 电台风格
    public String url;       // 播放地址 (B站填网页链接，MP3填直链)

    public RadioStation(String name, String style, String url) {
        this.name = name;
        this.style = style;
        this.url = url;
    }
}