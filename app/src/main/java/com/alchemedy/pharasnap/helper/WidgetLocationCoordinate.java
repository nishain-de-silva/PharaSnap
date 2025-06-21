package com.alchemedy.pharasnap.helper;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

public class WidgetLocationCoordinate {
    public int y;
    public int screenOrientation;

    public WidgetLocationCoordinate(String JSONString, Context context) throws JSONException {
        if (JSONString == null) return;
        JSONObject parsable = new JSONObject(JSONString);
        screenOrientation = parsable.getInt("screenOrientation");
        if(context.getResources().getConfiguration().orientation == screenOrientation) {
            y = parsable.getInt("y");
        }
    }

    public boolean isSameAsDefault() {
        return y == 0;
    }

    public WidgetLocationCoordinate(int y, Context context) {
        screenOrientation = context.getResources().getConfiguration().orientation;
        this.y = y;
    }

    public String toJSONString() {
        try {
            return new JSONObject()
                    .put("screenOrientation", screenOrientation)
                    .put("y", y)
                    .toString();
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }
}
