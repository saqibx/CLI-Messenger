import { homedir } from 'node:os';
import { join } from 'node:path';
import { mkdirSync, readFileSync, writeFileSync, chmodSync, rmSync } from 'node:fs';

const DEFAULT_SERVER = 'http://localhost:8099';


function configDir() {
  const base = process.env.XDG_CONFIG_HOME || join(homedir(), '.config');

  const profile = process.env.MSG_PROFILE;

  if (profile) {
    return join(base, 'msg', 'profiles', profile);
  }

  return join(base, 'msg');
}


export function currentProfile() {
  return process.env.MSG_PROFILE || null;
}


function configFile() {
  return join(configDir(), 'config.json');
}


export function load() {
  try {
    return JSON.parse(readFileSync(configFile(), 'utf8'));
  } catch {
    return {};
  }
}


export function save(config) {
  const dir = configDir();
  mkdirSync(dir, { recursive: true });
  writeFileSync(configFile(), JSON.stringify(config, null, 2));

  try {
    chmodSync(configFile(), 0o600);
  } catch {
  }
}


export function getServer() {
  return process.env.MSG_SERVER || load().server || DEFAULT_SERVER;
}


export function setServer(url) {
  const config = load();
  config.server = url.replace(/\/+$/, '');
  save(config);
}


export function saveAuth({ token, username, displayName, email }) {
  const config = load();
  config.auth = { token, username, displayName, email };
  save(config);
}


export function getAuth() {
  return load().auth || null;
}


export function clearAuth() {
  const config = load();
  delete config.auth;
  save(config);
}


export function configPath() {
  return configFile();
}


export function resetAll() {
  try {
    rmSync(configFile());
  } catch {
  }
}
