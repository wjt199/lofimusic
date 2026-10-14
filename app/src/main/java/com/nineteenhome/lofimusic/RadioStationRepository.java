package com.nineteenhome.lofimusic;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class RadioStationRepository {

    /**
     * 从 res/raw/stations.json 读取并解析电台列表（已自动去重）
     */
    public static List<RadioStation> getStations(Context context) {
        List<RadioStation> stationList = new ArrayList<>();
        // 使用 LinkedHashMap 保持 JSON 中的原始顺序，并以 URL 为 Key 进行二次去重保护
        Map<String, RadioStation> uniqueMap = new LinkedHashMap<>();

        try {
            // 1. 读取 raw 目录下的 stations.json 文件
            InputStream is = context.getResources().openRawResource(R.raw.stations);
            Scanner scanner = new Scanner(is).useDelimiter("\\A");
            String jsonStr = scanner.hasNext() ? scanner.next() : "";
            scanner.close();

            // 2. 解析 JSON 数组
            JSONArray jsonArray = new JSONArray(jsonStr);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject obj = jsonArray.getJSONObject(i);
                String name = obj.getString("name");
                String category = obj.getString("category");
                String url = obj.getString("url");

                // 3. 放入 Map 自动去重
                uniqueMap.putIfAbsent(url, new RadioStation(name, category, url));
            }

            stationList.addAll(uniqueMap.values());
        } catch (Exception e) {
            //noinspection CallToPrintStackTrace
            e.printStackTrace();
        }

        return stationList;
    }
}