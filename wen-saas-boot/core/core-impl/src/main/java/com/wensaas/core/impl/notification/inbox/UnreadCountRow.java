package com.wensaas.core.impl.notification.inbox;

/** {@code countUnreadByCategory} 投影行（category + count）。 */
public class UnreadCountRow {
    private String category;
    private long count;

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public long getCount() { return count; }
    public void setCount(long count) { this.count = count; }
}
