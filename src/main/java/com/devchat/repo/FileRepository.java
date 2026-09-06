package com.devchat.repo;

import com.devchat.model.StoredFile;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import java.util.Optional;


@Repository
public class FileRepository {

    private final DynamoDbTable<StoredFile> table;


    public FileRepository(DynamoDbEnhancedClient enhanced, TableNames tables) {
        this.table = enhanced.table(tables.files(), TableSchema.fromBean(StoredFile.class));
    }


    public void save(StoredFile file) {
        table.putItem(file);
    }


    public Optional<StoredFile> findById(String id) {
        Key key = Key.builder().partitionValue(id).build();
        StoredFile found = table.getItem(key);
        return Optional.ofNullable(found);
    }
}
