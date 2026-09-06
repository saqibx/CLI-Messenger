package com.devchat.repo;

import com.devchat.config.DynamoDbProperties;
import org.springframework.stereotype.Component;


@Component
public class TableNames {

    private final DynamoDbProperties props;


    public TableNames(DynamoDbProperties props) {
        this.props = props;
    }


    public String users() {
        return props.tableName("Users");
    }


    public String contacts() {
        return props.tableName("Contacts");
    }


    public String conversations() {
        return props.tableName("Conversations");
    }


    public String conversationMembers() {
        return props.tableName("ConversationMembers");
    }


    public String messages() {
        return props.tableName("Messages");
    }


    public String files() {
        return props.tableName("Files");
    }
}
