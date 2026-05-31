package dto;

import org.bson.Document;

/** A linked Plaid "item" (one connection to a bank) for a user. */
public class PlaidItemDto extends BaseDto {

    public String userName;
    public String itemId;
    public String accessTokenEnc; // encrypted at rest (see security.CryptoUtil)
    public String institutionName;
    public Long createdAt;

    @Override
    public Document toDocument() {
        return new Document()
                .append("userName", userName)
                .append("itemId", itemId)
                .append("accessTokenEnc", accessTokenEnc)
                .append("institutionName", institutionName)
                .append("createdAt", createdAt);
    }

    public static PlaidItemDto fromDocument(Document d) {
        PlaidItemDto p = new PlaidItemDto();
        if (d.get("_id") != null) p.loadUniqueId(d);
        p.userName = d.getString("userName");
        p.itemId = d.getString("itemId");
        p.accessTokenEnc = d.getString("accessTokenEnc");
        p.institutionName = d.getString("institutionName");
        p.createdAt = d.getLong("createdAt");
        return p;
    }
}
