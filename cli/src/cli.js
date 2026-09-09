import { api } from './api.js';
import { prompt, promptHidden } from './prompt.js';
import { chat } from './chat.js';
import {
  saveAuth, getAuth, clearAuth, getServer, setServer, configPath,
} from './config.js';

const c = {
  green: (s) => `\x1b[32m${s}\x1b[0m`,
  red: (s) => `\x1b[31m${s}\x1b[0m`,
  cyan: (s) => `\x1b[36m${s}\x1b[0m`,
  dim: (s) => `\x1b[2m${s}\x1b[0m`,
  bold: (s) => `\x1b[1m${s}\x1b[0m`,
};


export async function run(argv) {
  const { positionals, flags } = parseArgs(argv);

  const command = positionals[0];

  switch (command) {
    case 'register': return register(positionals.slice(1), flags);
    case 'login': return login(positionals.slice(1), flags);
    case 'logout': return logout();
    case 'whoami': return whoami();
    case 'settings': return settings();
    case 'config': return config(positionals.slice(1));
    case undefined: return chat({});
    case 'chat': return chat({ target: positionals[1] });
    case 'dm': return chat({ target: positionals[1] });
    case 'help':
    case '--help':
    case '-h': return help();
    default:
      console.error(c.red(`Unknown command: ${command}`));
      help();
      process.exitCode = 1;
  }
}


async function register(args, flags) {
  const username = args[0] || await prompt('Username: ');
  const email = args[1] || await prompt('Email: ');
  const password = flags.password || await promptHidden('Choose a password: ');

  const res = await api('/api/auth/register', {
    method: 'POST',
    body: { username, email, password, displayName: flags.name },
  });

  saveAuth(res);

  console.log(c.green(`Registered and logged in as @${res.username}`) + c.dim(` <${res.email}>`));
  console.log(c.dim("Start a chat with:  ") + c.bold('msg'));
}


async function login(args, flags) {
  const usernameOrEmail = args[0] || await prompt('Username or email: ');
  const password = flags.password || await promptHidden('Password: ');

  const res = await api('/api/auth/login', {
    method: 'POST',
    body: { usernameOrEmail, password },
  });

  saveAuth(res);

  console.log(c.green(`Logged in as @${res.username}`) + c.dim(` (${res.displayName})`));
}


function logout() {
  const auth = getAuth();

  if (!auth) {
    console.log('You are not logged in.');
    return;
  }

  clearAuth();
  console.log(`Logged out @${auth.username}.`);
}


function whoami() {
  const auth = getAuth();

  if (!auth) {
    console.log('Not logged in. Use ' + c.bold('msg register') + ' or ' + c.bold('msg login') + '.');
    process.exitCode = 1;
    return;
  }

  console.log(`@${auth.username} ${c.dim(`(${auth.displayName}) <${auth.email}>`)}`);
  console.log(c.dim(`server: ${getServer()}`));
}


function config(args) {
  const sub = args[0];
  const value = args[1];

  if (sub === 'server' && value) {
    setServer(value);
    console.log(`Server set to ${getServer()}`);
  } else if (sub === 'show' || sub === undefined) {
    console.log(`server: ${getServer()}`);
    console.log(c.dim(`config file: ${configPath()}`));
  } else {
    console.log('Usage: msg config server <url>   |   msg config show');
    process.exitCode = 1;
  }
}


async function settings() {
  let auth = getAuth();

  if (!auth) {
    const who = await prompt('Username or email: ');
    const password = await promptHidden('Password: ');
    auth = await api('/api/auth/login', { method: 'POST', body: { usernameOrEmail: who, password } });
  }

  console.log(`Settings for @${auth.username}`);
  console.log('  1) Change password');
  console.log('  q) Quit');

  const choice = await prompt('> ');

  if (choice === '1') {
    await changePassword(auth.token);
  }
}


async function changePassword(token) {
  const current = await promptHidden('Current password: ');
  const next = await promptHidden('New password: ');
  const confirm = await promptHidden('Confirm new password: ');

  if (next !== confirm) {
    console.log(c.red('Passwords do not match.'));
    return;
  }

  await api('/api/auth/password', {
    method: 'POST',
    token,
    body: { currentPassword: current, newPassword: next },
  });

  console.log(c.green('Password changed.'));
}


function help() {
  console.log(`${c.bold('msg')} - terminal chat for developers

${c.bold('Usage')}
  msg                         open the chat picker (once logged in)
  msg dm <user>               open a live 1:1 chat
  msg register [user] [email] create an account
  msg login [user|email]      log in
  msg logout                  log out
  msg whoami                  show the logged-in account
  msg settings                change your password
  msg config server <url>     point at a backend server
  msg config show             show current settings
  msg help                    this help

${c.bold('Inside a chat')}
  type text to send - /dm <user> - /chats - /open <n> - /history - /quit

${c.bold('Flags')}
  --password <pw>   supply the password non-interactively
  --name <name>     display name at registration
`);
}


function parseArgs(argv) {
  const positionals = [];
  const flags = {};

  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];

    if (arg.startsWith('--')) {
      const key = arg.slice(2);
      const eq = key.indexOf('=');

      if (eq >= 0) {
        flags[key.slice(0, eq)] = key.slice(eq + 1);
      } else if (i + 1 < argv.length && !argv[i + 1].startsWith('--')) {
        flags[key] = argv[++i];
      } else {
        flags[key] = true;
      }
    } else {
      positionals.push(arg);
    }
  }

  return { positionals, flags };
}
