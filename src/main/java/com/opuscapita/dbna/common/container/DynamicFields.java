package com.opuscapita.dbna.common.container;

import com.google.gson.annotations.Since;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * Stores free-form message fields while preserving insertion order.
 */
public class DynamicFields implements Serializable {

    @Since(1.0) private ArrayList<TagValue> list;
    private static final long serialVersionUID = 1L;

    public DynamicFields() {
        this.list = new ArrayList<>();
    }

    public ArrayList<TagValue> getList() {
        return this.list;
    }

    public void setList(ArrayList<TagValue> list) {
        this.list = list;
    }

    public void add(TagValue tv) {
        this.list.add(tv);
    }

    public String getValue(String tag) {
        Iterator<TagValue> itr = this.list.iterator();
        while (itr.hasNext()) {
            TagValue tv = itr.next();
            if (tv.getTag().equals(tag)) {
                return tv.getValue();
            }
        }
        return null;
    }
}
