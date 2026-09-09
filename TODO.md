# TODO

- group chats from the cli. add a `msg group <name> <user...>` command plus /group and /add inside a chat. the backend endpoints already exist (POST /api/chats/group and POST /api/chats/{id}/members), it just isnt wired up in the cli yet
- change password. add a POST /api/auth/password endpoint that checks the current password and updates the stored hash, plus a `msg passwd` / /passwd command to use it
