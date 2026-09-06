package com.devchat;

import com.devchat.model.Contact;
import com.devchat.model.ConversationMember;
import com.devchat.model.Message;
import com.devchat.service.AuthService;
import com.devchat.service.ChatService;
import com.devchat.service.ContactService;
import com.devchat.service.ConversationView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "devchat.dynamodb.region=us-east-1"
})
@EmbeddedKafka(partitions = 1, topics = "dev-chat")
@Testcontainers
class DevChatEndToEndIT {

    @Container
    static final GenericContainer<?> DYNAMO =
            new GenericContainer<>(DockerImageName.parse("amazon/dynamodb-local:2.5.2"))
                    .withExposedPorts(8000)
                    .withCommand("-jar DynamoDBLocal.jar -inMemory -sharedDb");


    @DynamicPropertySource
    static void dynamoProps(DynamicPropertyRegistry registry) {
        registry.add("devchat.dynamodb.endpoint",
                () -> "http://" + DYNAMO.getHost() + ":" + DYNAMO.getMappedPort(8000));
    }

    @Autowired
    AuthService auth;

    @Autowired
    ContactService contacts;

    @Autowired
    ChatService chat;


    @Test
    void registrationEnforcesUniquenessAndPasswordCheck() {

        auth.createAccount("alice", "alice@example.com", "supersecret", "Alice");

        assertThatThrownBy(() -> auth.createAccount("alice", "other@example.com", "supersecret", null))
                .isInstanceOf(AuthService.AuthException.class);
        assertThatThrownBy(() -> auth.createAccount("alice2", "alice@example.com", "supersecret", null))
                .isInstanceOf(AuthService.AuthException.class);

        assertThatThrownBy(() -> auth.authenticate("alice", "wrongpassword"))
                .isInstanceOf(AuthService.AuthException.class);

        assertThat(auth.authenticate("alice", "supersecret").getUsername()).isEqualTo("alice");
        assertThat(auth.authenticate("alice@example.com", "supersecret").getUsername()).isEqualTo("alice");
    }


    @Test
    void directMessageIsPersistedAndVisibleToBothParties() {

        auth.createAccount("bob", "bob@example.com", "supersecret", "Bob");
        auth.createAccount("carol", "carol@example.com", "supersecret", "Carol");

        ConversationView fromBob = chat.openDirect("bob", "carol");

        assertThat(chat.openDirect("carol", "bob").id()).isEqualTo(fromBob.id());

        chat.send("bob", fromBob.id(), "hey carol, PR is up");

        List<Message> carolHistory = chat.history("carol", fromBob.id(), 50);
        assertThat(carolHistory).hasSize(1);
        assertThat(carolHistory.get(0).getText()).isEqualTo("hey carol, PR is up");
        assertThat(carolHistory.get(0).getFrom()).isEqualTo("bob");

        assertThat(chat.listChats("bob")).extracting(ConversationMember::getConversationId).contains(fromBob.id());
        assertThat(chat.listChats("carol")).extracting(ConversationMember::getConversationId).contains(fromBob.id());
    }


    @Test
    void groupChatDeliversToAllMembersAndSupportsAdding() {

        auth.createAccount("dan", "dan@example.com", "supersecret", "Dan");
        auth.createAccount("erin", "erin@example.com", "supersecret", "Erin");
        auth.createAccount("finn", "finn@example.com", "supersecret", "Finn");
        auth.createAccount("gus", "gus@example.com", "supersecret", "Gus");

        ConversationView group = chat.createGroup("dan", "backend-team", List.of("erin", "finn@example.com"));

        chat.send("erin", group.id(), "standup in 5");
        assertThat(chat.history("finn", group.id(), 50)).extracting(Message::getText).contains("standup in 5");

        assertThat(chat.listChats("finn")).extracting(ConversationMember::getConversationName).contains("backend-team");

        chat.addToGroup("dan", group.id(), "gus");
        assertThat(chat.listChats("gus")).extracting(ConversationMember::getConversationId).contains(group.id());

        auth.createAccount("mallory", "mallory@example.com", "supersecret", null);
        assertThatThrownBy(() -> chat.send("mallory", group.id(), "let me in"))
                .isInstanceOf(ChatService.ChatException.class);
    }


    @Test
    void contactsCanBeAddedByUsernameOrEmail() {

        auth.createAccount("hank", "hank@example.com", "supersecret", "Hank");
        auth.createAccount("ivy", "ivy@example.com", "supersecret", "Ivy");

        contacts.add("hank", "ivy");
        contacts.add("hank", "ivy@example.com");

        List<Contact> book = contacts.list("hank");
        assertThat(book).extracting(Contact::getContact).contains("ivy");
        assertThatThrownBy(() -> contacts.add("hank", "nobody"))
                .isInstanceOf(ContactService.ContactException.class);
    }
}
