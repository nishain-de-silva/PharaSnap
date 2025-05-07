package com.alchemedy.pharasnap.helper;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

public class WidgetLocationCoordinate {
    public int x, y;
    public int screenOrientation;

    public WidgetLocationCoordinate(String JSONString, Context context) throws JSONException {
        if (JSONString == null) return;
        JSONObject parsable = new JSONObject(JSONString);
        screenOrientation = parsable.getInt("screenOrientation");
        if(context.getResources().getConfiguration().orientation == screenOrientation) {
            x = parsable.getInt("x");
            y = parsable.getInt("y");
        }
    }

    public boolean isSameAsDefault() {
        return x == 0 && y == 0;
    }

    public WidgetLocationCoordinate(int x, int y, Context context) {
        screenOrientation = context.getResources().getConfiguration().orientation;
        this.x = x;
        this.y = y;
    }

    public String toJSONString() {
        try {
            return new JSONObject()
                    .put("screenOrientation", screenOrientation)
                    .put("x", x)
                    .put("y", y)
                    .toString();
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }
}
