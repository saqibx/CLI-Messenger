package com.devchat.repo;

import com.devchat.model.User;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.util.Optional;


@Repository
public class UserRepository {

    private final DynamoDbTable<User> table;


    public UserRepository(DynamoDbEnhancedClient enhanced, TableNames tables) {
        this.table = enhanced.table(tables.users(), TableSchema.fromBean(User.class));
    }


    public void createNew(User user) {
        try {
            Expression condition = Expression.builder()
                    .expression("attribute_not_exists(username)")
                    .build();
            PutItemEnhancedRequest<User> request = PutItemEnhancedRequest.builder(User.class)
                    .item(user)
                    .conditionExpression(condition)
                    .build();
            table.putItem(request);
        } catch (ConditionalCheckFailedException e) {
            throw new UsernameTakenException(user.getUsername());
        }
    }


    public Optional<User> findByUsername(String username) {
        Key key = Key.builder().partitionValue(username).build();
        User found = table.getItem(key);
        return Optional.ofNullable(found);
    }


    public Optional<User> findByEmail(String email) {
        DynamoDbIndex<User> index = table.index(User.EMAIL_INDEX);
        for (var page : index.query(QueryConditional.keyEqualTo(k -> k.partitionValue(email)))) {
            for (User user : page.items()) {
                return Optional.of(user);
            }
        }
        return Optional.empty();
    }


    public Optional<User> findByUsernameOrEmail(String identifier) {
        Optional<User> byName = findByUsername(identifier);
        if (byName.isPresent()) {
            return byName;
        }
        return findByEmail(identifier);
    }


    public static class UsernameTakenException extends RuntimeException {
        public UsernameTakenException(String username) {
            super("Username already taken: " + username);
        }
    }
}
