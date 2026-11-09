package com.archive.app.data.dao.model;

/** Flat projection of one global-search hit, regardless of which table produced it. */
public class SearchResult {
    public String type;
    public String id;
    public String title;
    public String subtitle;
    public long updatedAt;
}
