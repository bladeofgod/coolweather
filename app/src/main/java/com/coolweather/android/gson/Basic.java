package com.coolweather.android.gson;

import com.google.gson.annotations.SerializedName;

/**
 * Created by 45010 on 2018/1/4.
 */

public class Basic {

    @SerializedName("city")
    public String cityName;
    @SerializedName("id")
    public String weatherId;

    public Update update;
    public class Update{
        @SerializedName("loc")
        public String updateTime;
    }
}
