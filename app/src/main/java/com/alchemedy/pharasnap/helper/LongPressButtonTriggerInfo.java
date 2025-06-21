package com.alchemedy.pharasnap.helper;

import org.json.JSONException;
import org.json.JSONObject;

public class LongPressButtonTriggerInfo {
    public String contentDescription;
    public String systemPackageName;

    public LongPressButtonTriggerInfo(String data) {
        try {
            JSONObject jsonData = new JSONObject(data);
            contentDescription = jsonData.getString("contentDescription");
            systemPackageName = jsonData.getString("systemPackageName");
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }
    public LongPressButtonTriggerInfo(String contentDescription, String systemPackageName) {
        this.contentDescription = contentDescription;
        this.systemPackageName = systemPackageName;
    }

    public String toJSONString() {
        try {
            return new JSONObject().put("contentDescription", contentDescription)
                    .put("systemPackageName", systemPackageName)
                    .toString();
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }
}
