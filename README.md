# DevChat

Chat with other people from your terminal. You run a little CLI called `msg`, it talks
to a backend server over HTTP and a websocket, and messages show up live.

There are two parts in here:

- `cli/` - the `msg` command line tool (plain Node, no dependencies)
- `src/` - the backend server (Java / Spring Boot). Uses Kafka to push messages out live
  and DynamoDB to store users, chats and messages.

The CLI only ever talks to the server over HTTPS + websocket with a token. It never touches
Kafka or the database itself.

## What you need

- Node 20 or newer (for the CLI)
- JDK 21+ and Maven 3.9+ (for the backend)
- Docker, for running Kafka and DynamoDB locally

## Running it locally

Start the backend first. There is a script that does the docker stuff and builds/runs the
server for you:

```bash
./devchat
```

That starts the server on http://localhost:8099. Leave it running (Ctrl-C stops it).

Now in another terminal set up the CLI:

```bash
cd cli
npm link        # makes `msg` available on your path
```

Then make an account and start chatting:

```bash
msg register        # pick a username, email and password
msg                 # opens the chat
```

Open a chat with someone and type to send. To message a specific person:

```bash
msg dm <username>
```

## msg commands

```
msg                     open the chat (once you are logged in)
msg dm <user>           open a 1:1 chat with someone
msg register            make an account
msg login               log in
msg logout              log out
msg whoami              show who you are logged in as
msg config server <url> point the CLI at a different server
msg config show         show current settings
msg help                help
```

Once you are inside a chat you can type these:

```
<text>              send a message
[addfile: <path>]   send a file (also /file <path>)
/save <n>           save a file someone sent you (goes into ./msg-downloads)
/dm <user>          open a 1:1 chat
/chats              list your chats
/open <n>           switch to chat number n
/history            show recent messages again
/login [user]       log in as someone else (just this window)
/logout             log out
/who                show who you are
/quit               leave
```

Each running `msg` holds its login in memory, so you can open two terminals and log in as
two different people to test it.

## How it works

- The CLI hits REST endpoints on the backend (register, login, send message, list chats,
  history, upload/download files).
- When you send a message the server saves it and drops it on a Kafka topic. The server
  reads that topic and pushes the message down the websocket to everyone in the chat, so it
  shows up live.
- Users, contacts, chats, messages and small files live in DynamoDB. Files are capped at
  256KB for now since the bytes sit in the database (S3 comes later).

## Build and test

```bash
mvn -Dmaven.test.skip=true package   # build the server jar
java -jar target/devchat-cli-0.1.0.jar
```

## Deploying

The same backend is meant to run on AWS (ECS Fargate + MSK for Kafka + DynamoDB). Point the
CLI at it with `msg config server https://your-server`.
