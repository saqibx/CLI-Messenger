import { getServer } from './config.js';


export async function api(path, { method = 'GET', body, token } = {}) {
  const url = getServer() + path;

  const headers = {};
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  let requestBody = undefined;
  if (body !== undefined) {
    requestBody = JSON.stringify(body);
  }

  let res;
  try {
    res = await fetch(url, {
      method,
      headers,
      body: requestBody,
    });
  } catch (e) {
    const reason = e.code || e.message;
    throw new Error(`Can't reach the server at ${getServer()} (${reason}). ` +
      `Is it running? Set another with: msg config server <url>`);
  }

  const text = await res.text();

  let data = {};
  if (text) {
    data = safeJson(text);
  }

  if (!res.ok) {
    throw new Error(data?.error || `Server error (${res.status}).`);
  }

  return data;
}


function safeJson(text) {
  try {
    return JSON.parse(text);
  } catch {
    return { error: text };
  }
}
