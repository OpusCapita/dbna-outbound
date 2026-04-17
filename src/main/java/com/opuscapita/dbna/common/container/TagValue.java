package com.opuscapita.dbna.common.container;

import com.google.gson.annotations.Since;

public class TagValue {

    @Since(1.0) private String tag;
    @Since(1.0) private String value;

    public TagValue(String tag, String value) {
        this.tag = tag;
        this.value = value;
    }

    public String getTag() {
        return this.tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getValue() {
        return this.value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
