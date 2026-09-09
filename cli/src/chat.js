import { createInterface, clearLine, cursorTo } from 'node:readline';
import { stdin, stdout } from 'node:process';
import { readFileSync, writeFileSync, mkdirSync, existsSync } from 'node:fs';
import { basename, resolve, join, extname } from 'node:path';
import { homedir } from 'node:os';
import { api } from './api.js';
import { prompt, promptHidden } from './prompt.js';
import { getAuth, getServer } from './config.js';

const MAX_FILE_BYTES = 256 * 1024;

const ADDFILE_RE = /^\[addfile:\s*(.+?)\s*\]$/i;

const c = {
  green: (s) => `\x1b[32m${s}\x1b[0m`,
  cyan: (s) => `\x1b[36m${s}\x1b[0m`,
  yellow: (s) => `\x1b[33m${s}\x1b[0m`,
  dim: (s) => `\x1b[2m${s}\x1b[0m`,
  bold: (s) => `\x1b[1m${s}\x1b[0m`,
};


function hhmm(ts) {
  const d = new Date(ts);
  const hours = String(d.getHours()).padStart(2, '0');
  const mins = String(d.getMinutes()).padStart(2, '0');
  return `${hours}:${mins}`;
}


function wsUrl(token) {
  const base = getServer().replace(/^http/, 'ws').replace(/\/+$/, '');
  return `${base}/ws?token=${encodeURIComponent(token)}`;
}


function resolvePath(p) {
  if (p === '~') {
    return homedir();
  }
  if (p.startsWith('~/')) {
    return join(homedir(), p.slice(2));
  }
  return resolve(process.cwd(), p);
}


function uniqueDest(dir, name) {
  let dest = join(dir, name);
  if (!existsSync(dest)) {
    return dest;
  }

  const ext = extname(name);
  const stem = name.slice(0, name.length - ext.length);

  for (let i = 1; ; i++) {
    dest = join(dir, `${stem} (${i})${ext}`);
    if (!existsSync(dest)) {
      return dest;
    }
  }
}


export async function chat({ target } = {}) {
  let session = getAuth();
  let current = null;
  let lastList = [];
  const receivedFiles = [];
  let rl = null;
  let ws = null;
  let switching = false;
  let closing = false;

  const me = () => (session ? session.username : null);


  function printAbove(line) {
    if (rl) {
      clearLine(stdout, 0);
      cursorTo(stdout, 0);
    }
    stdout.write(line + '\n');
    if (rl) {
      rl.prompt(true);
    }
  }


  if (!session) {
    session = await interactiveAuth();
    if (!session) {
      return;
    }
  }

  await connect();
  console.log(c.bold(`You are @${me()}`) + c.dim(` - ${getServer()}`));

  if (target) {
    await openDirect(target);
  } else {
    await showChats(true);
  }

  rl = createInterface({ input: stdin, output: stdout, prompt: '' });
  setPrompt();
  rl.prompt();

  const queue = [];
  let draining = Promise.resolve();
  let drainingNow = false;

  rl.on('line', (line) => {
    queue.push(line);
    // process one line at a time so async sends dont race
    if (!drainingNow) {
      draining = drain();
    }
  });

  rl.on('close', async () => {
    closing = true;
    try { await draining; } catch {}
    try { ws && ws.close(); } catch {}
    console.log(c.dim('\nBye.'));
    process.exit(0);
  });


  async function drain() {
    drainingNow = true;
    while (queue.length) {
      await handleLine(queue.shift());
    }
    drainingNow = false;
  }


  async function handleLine(line) {
    const text = line.trim();
    const addfile = text.match(ADDFILE_RE);

    try {
      if (text.startsWith('/')) {
        await command(text);
      } else if (addfile) {
        await sendFileFromPath(addfile[1]);
      } else if (text.length > 0) {
        if (!current) {
          console.log(c.dim('Open a chat first:  /dm <username>'));
        } else {
          await api(`/api/chats/${encodeURIComponent(current.id)}/messages`, {
            method: 'POST', token: session.token, body: { text },
          });
          const stamp = c.dim('[' + hhmm(Date.now()) + ']');
          printAbove(`${stamp} ${c.green('you')}: ${text}`);
        }
      }
    } catch (e) {
      console.log(c.yellow(e.message));
    }

    if (!closing) {
      rl.prompt();
    }
  }


  async function command(text) {
    const parts = text.slice(1).split(/\s+/);
    const cmd = parts[0];
    const rest = parts.slice(1);
    const arg = rest.join(' ');

    switch (cmd) {
      case 'login': return switchLogin(rest);
      case 'logout': return doLogout();
      case 'dm': return arg ? openDirect(arg) : console.log(c.dim('Usage: /dm <username>'));
      case 'group': return createGroup(rest);
      case 'addmember': return arg ? addMember(arg) : console.log(c.dim('Usage: /addmember <username>'));
      case 'file': return arg ? sendFileFromPath(arg) : console.log(c.dim('Usage: /file <path>  (or type [addfile: <path>])'));
      case 'save': return saveFile(rest);
      case 'chats': return showChats(false);
      case 'open': return openByIndex(arg);
      case 'history': return current ? showHistory() : console.log(c.dim('No chat open.'));
      case 'who': return console.log(c.dim(`You are @${me()} on ${getServer()}`));
      case 'help': return showHelp();
      case 'quit':
      case 'q': return rl.close();
      default: console.log(c.dim(`Unknown command: /${cmd} (try /help)`));
    }
  }


  async function interactiveAuth() {
    console.log(c.bold('Welcome to msg.') + c.dim(` (${getServer()})`));

    while (true) {
      const who = await prompt("Username or email (or type 'new' to register): ");
      if (!who) {
        continue;
      }

      if (who.toLowerCase() === 'new') {
        const s = await registerFlow();
        if (s) {
          return s;
        }
        continue;
      }

      const password = await promptHidden('Password: ');
      try {
        return await api('/api/auth/login', {
          method: 'POST', body: { usernameOrEmail: who, password },
        });
      } catch (e) {
        console.log(c.yellow(e.message));
      }
    }
  }


  async function registerFlow() {
    const username = await prompt('Choose a username: ');
    const email = await prompt('Email: ');
    const password = await promptHidden('Choose a password: ');

    try {
      const res = await api('/api/auth/register', {
        method: 'POST', body: { username, email, password },
      });
      console.log(c.green(`Registered as @${res.username}`));
      return res;
    } catch (e) {
      console.log(c.yellow(e.message));
      return null;
    }
  }


  async function switchLogin(args) {
    const who = args[0] || await prompt('Username or email: ');
    const password = args[1] || await promptHidden('Password: ');

    let res;
    try {
      res = await api('/api/auth/login', { method: 'POST', body: { usernameOrEmail: who, password } });
    } catch (e) {
      console.log(c.yellow(e.message));
      return;
    }

    session = res;
    current = null;
    switching = true;
    try { ws && ws.close(); } catch {}
    await connect();
    switching = false;
    console.log(c.green(`now logged in as @${me()}`));
    await showChats(false);
  }


  async function doLogout() {
    try { ws && ws.close(); } catch {}
    session = null;
    session = await interactiveAuth();
    if (!session) {
      return rl.close();
    }
    switching = true;
    await connect();
    switching = false;
    console.log(c.green(`now logged in as @${me()}`));
    current = null;
    await showChats(false);
  }


  function connect() {
    ws = new WebSocket(wsUrl(session.token));

    ws.addEventListener('message', (ev) => {
      let evt;
      try { evt = JSON.parse(ev.data); } catch { return; }

      if (evt.type !== 'message' && evt.type !== 'file') {
        return;
      }
      if (evt.from === me()) {
        return;
      }

      const here = current && evt.conversationId === current.id;

      if (evt.type === 'file') {
        const n = noteFile({ fileId: evt.fileId, name: evt.fileName, from: evt.from });
        const line = `[file] ${evt.from} sent a file: ${c.bold(evt.fileName)} ${c.dim(`- save it with  /save ${n}`)}`;
        if (here) {
          printAbove(line);
        } else {
          printAbove(c.yellow(line) + c.dim('  (in another chat)'));
        }
      } else if (here) {
        const stamp = c.dim('[' + hhmm(evt.timestamp) + ']');
        printAbove(`${stamp} ${c.cyan(evt.from)}: ${evt.text}`);
      } else {
        printAbove(c.yellow(`${evt.from}: ${evt.text}`) + c.dim('  (in another chat - /chats)'));
      }
    });

    ws.addEventListener('close', () => {
      if (!closing && !switching) {
        printAbove(c.dim('- disconnected -'));
      }
    });

    ws.addEventListener('error', () => {});

    return new Promise((resolve, reject) => {
      ws.addEventListener('open', resolve, { once: true });
      ws.addEventListener('error', () => reject(new Error(
        `Couldn't connect to ${getServer()}. Is the server running?`)), { once: true });
    });
  }


  async function openDirect(who) {
    const view = await api('/api/chats/direct', {
      method: 'POST', token: session.token, body: { usernameOrEmail: who },
    });

    current = { id: view.id, name: view.displayName };
    console.log(c.green(`- now chatting with ${view.displayName} -`));
    await showHistory();

    if (rl) {
      setPrompt();
      rl.prompt();
    }
  }


  async function showChats(initial) {
    lastList = await api('/api/chats', { token: session.token });

    if (lastList.length === 0) {
      console.log(c.dim(initial ? 'No chats yet. Start one with:  /dm <username>' : 'No chats yet.'));
      if (rl) {
        rl.prompt();
      }
      return;
    }

    console.log(c.bold('Your chats:'));

    for (let i = 0; i < lastList.length; i++) {
      const ch = lastList[i];
      let mark = '  ';
      if (current && ch.id === current.id) {
        mark = c.green(' *');
      }
      const num = c.dim(String(i + 1) + ')');
      console.log(`${mark} ${num} ${ch.name}`);
    }

    console.log(c.dim('Switch with:  /open <number>   or   /dm <username>'));
    if (rl) {
      rl.prompt();
    }
  }


  async function openByIndex(arg) {
    const n = parseInt(arg, 10);
    if (!n || n < 1 || n > lastList.length) {
      console.log(c.dim('Usage: /open <number>  (see /chats)'));
      return;
    }

    const ch = lastList[n - 1];
    current = { id: ch.id, name: ch.name };
    console.log(c.green(`- opened ${ch.name} -`));
    await showHistory();
    setPrompt();
    rl.prompt();
  }


  async function showHistory() {
    const messages = await api(
      `/api/chats/${encodeURIComponent(current.id)}/messages?limit=30`, { token: session.token });

    if (messages.length === 0) {
      console.log(c.dim('(no messages yet - say hi)'));
      return;
    }

    for (const m of messages) {
      let who = c.cyan(m.from);
      if (m.from === me()) {
        who = c.green('you');
      }

      const stamp = c.dim('[' + hhmm(m.timestamp) + ']');

      if (m.kind === 'FILE') {
        const n = noteFile({ fileId: m.fileId, name: m.text, from: m.from });
        console.log(`${stamp} ${who}: [file] ${c.bold(m.text)} ${c.dim(`(/save ${n})`)}`);
      } else {
        console.log(`${stamp} ${who}: ${m.text}`);
      }
    }
  }


  function noteFile(f) {
    receivedFiles.push(f);
    return receivedFiles.length;
  }


  async function sendFileFromPath(rawPath) {
    if (!current) {
      console.log(c.dim('Open a chat first:  /dm <username>'));
      return;
    }

    const path = resolvePath(rawPath);

    let buf;
    try {
      buf = readFileSync(path);
    } catch (e) {
      const reason = e.code || e.message;
      console.log(c.yellow(`Can't read "${rawPath}" (${reason})`));
      return;
    }

    if (buf.length === 0) {
      console.log(c.yellow('That file is empty.'));
      return;
    }

    if (buf.length > MAX_FILE_BYTES) {
      const kb = (buf.length / 1024) | 0;
      console.log(c.yellow(`"${basename(path)}" is ${kb} KB; max is ${MAX_FILE_BYTES / 1024} KB for now.`));
      return;
    }

    const name = basename(path);
    await api(`/api/chats/${encodeURIComponent(current.id)}/files`, {
      method: 'POST', token: session.token,
      body: { name, contentBase64: buf.toString('base64') },
    });

    const stamp = c.dim('[' + hhmm(Date.now()) + ']');
    printAbove(`${stamp} ${c.green('you')}: [file] ${c.bold(name)} ${c.dim('(sent)')}`);
  }


  async function saveFile(args) {
    const n = parseInt(args[0], 10);
    if (!n || n < 1 || n > receivedFiles.length) {
      console.log(c.dim('Usage: /save <number>   (the number shown next to a file)'));
      return;
    }

    const f = receivedFiles[n - 1];
    const dto = await api(`/api/files/${encodeURIComponent(f.fileId)}`, { token: session.token });

    let dir;
    if (args[1]) {
      dir = resolvePath(args[1]);
    } else {
      dir = join(process.cwd(), 'msg-downloads');
    }

    mkdirSync(dir, { recursive: true });
    const dest = uniqueDest(dir, dto.name);
    writeFileSync(dest, Buffer.from(dto.contentBase64, 'base64'));

    console.log(c.green(`saved ${dto.name} -> ${dest}`));
    console.log(c.dim('  open it in VS Code:  code ' + JSON.stringify(dest)));
  }


  function setPrompt() {
    if (current) {
      rl.setPrompt(`${c.bold(current.name)} ${c.dim('>')} `);
    } else {
      rl.setPrompt(`${c.dim('(no chat) >')} `);
    }
  }


  async function createGroup(users) {
    if (users.length === 0) {
      console.log(c.dim('Usage: /group <username> <username> ...'));
      return;
    }

    const name = users.join(', ');

    const view = await api('/api/chats/group', {
      method: 'POST', token: session.token, body: { name, members: users },
    });

    current = { id: view.id, name: view.displayName };
    console.log(c.green(`- created group ${view.displayName} -`));

    await showHistory();

    if (rl) {
      setPrompt();
      rl.prompt();
    }
  }


  async function addMember(username) {
    if (!current) {
      console.log(c.dim('Open a group first.'));
      return;
    }

    const view = await api(`/api/chats/${encodeURIComponent(current.id)}/members`, {
      method: 'POST', token: session.token, body: { usernameOrEmail: username },
    });

    console.log(c.green(`- added ${username} to ${view.displayName} -`));
  }


  function showHelp() {
    console.log(`${c.bold('Commands')}
  ${c.bold('<text>')}              send to the open chat
  ${c.bold('[addfile: <path>]')}   send a file (e.g. [addfile: pom.xml])
  /save <n>           save a received file (into ./msg-downloads)
  /dm <user>          open a 1:1 chat
  /group <user...>    start a group chat with those people
  /addmember <user>   add someone to the current group
  /chats              list your chats
  /open <n>           switch to chat number n
  /history            reprint recent messages
  /login [user]       log in as someone else (this window only)
  /logout             log out
  /who                show who you are
  /quit               leave`);
  }
}
