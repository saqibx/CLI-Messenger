# DevChat

A terminal-based chat app. You install a small CLI called `msg`, make an account, and message
other people in real time over websockets. This repo has both the backend and the CLI, which I
built and deployed myself.

It's live at https://chat.saqibmazhar.com, so you can point the CLI at that instead of running
everything locally if you want.

## Project layout

- `cli/` - the `msg` command-line client. Plain Node.js, no dependencies.
- `src/` - the backend server (Java + Spring Boot). Stores data in DynamoDB.
- `deploy/` - Terraform for deploying the backend to AWS.

The CLI never touches the database directly. It only talks to the server over HTTPS and a
websocket using a bearer token.

## Requirements

- Node 20+ (for the CLI)
- JDK 21+ and Maven 3.9+ (for the backend)
- Docker (used to run a local DynamoDB)

## Running it locally

Start the backend. The `devchat` script builds the jar and runs the server on port 8099:

```bash
./devchat
```

Leave that running, then in another terminal set up the CLI and make an account:

```bash
cd cli
npm link          # puts the `msg` command on your PATH
msg register
msg               # opens the chat
```

To open a direct chat with someone:

```bash
msg dm <username>
```

## Commands

Outside a chat:

```
msg                     open the chat
msg dm <user>           1:1 chat with someone
msg register            create an account
msg login / logout      log in or out
msg whoami              show who you're logged in as
msg settings            change your password
msg config server <url> point the CLI at a different server
```

Inside a chat you type to send a message, or use:

```
/dm <user>          open a 1:1 chat
/group <user...>    create a group chat with those people
/addmember <user>   add someone to the group you're in
[addfile: <path>]   send a file (or /file <path>)
/save <n>           save a file someone sent you
/chats              list your chats
/open <n>           switch chats
/history            reprint recent messages
/quit               leave
```

Each running `msg` keeps its login in memory, so you can open two terminals and log in as two
different users to test it.

## How it works

When you send a message, the server saves it to DynamoDB and pushes it out to every member of the
chat over their websocket, so it appears instantly. There's also an optional Kafka mode for
running more than one server instance, but by default delivery happens in-process and no message
broker is needed.

DynamoDB holds users, contacts, conversations, messages, and files. Auth uses stateless,
HMAC-signed tokens with BCrypt-hashed passwords.

## Build

```bash
mvn -Dmaven.test.skip=true package
java -jar target/devchat-cli-0.1.0.jar
```

## Deploying

The `deploy/` folder has Terraform that provisions the backend on AWS (ECS Fargate behind an
Application Load Balancer, plus DynamoDB, ECR, and Secrets Manager). That's how the live instance
runs, with TLS through an ACM certificate and a subdomain pointed at the load balancer.
