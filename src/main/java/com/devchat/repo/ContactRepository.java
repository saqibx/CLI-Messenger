package com.devchat.repo;

import com.devchat.model.Contact;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.util.ArrayList;
import java.util.List;


@Repository
public class ContactRepository {

    private final DynamoDbTable<Contact> table;


    public ContactRepository(DynamoDbEnhancedClient enhanced, TableNames tables) {
        this.table = enhanced.table(tables.contacts(), TableSchema.fromBean(Contact.class));
    }


    public void save(Contact contact) {
        table.putItem(contact);
    }


    public List<Contact> listForOwner(String owner) {
        List<Contact> result = new ArrayList<>();
        for (Contact contact : table.query(QueryConditional.keyEqualTo(k -> k.partitionValue(owner))).items()) {
            result.add(contact);
        }
        return result;
    }


    public boolean exists(String owner, String contact) {
        Key key = Key.builder().partitionValue(owner).sortValue(contact).build();
        Contact found = table.getItem(key);
        return found != null;
    }
}
