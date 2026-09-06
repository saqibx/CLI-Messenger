import { createInterface } from 'node:readline/promises';
import { stdin, stdout } from 'node:process';


export async function prompt(question) {
  const rl = createInterface({ input: stdin, output: stdout });
  try {
    const answer = await rl.question(question);
    return answer.trim();
  } finally {
    rl.close();
  }
}


export function promptHidden(question) {
  if (!stdin.isTTY) {
    return prompt(question);
  }

  return new Promise((resolve, reject) => {
    stdout.write(question);
    stdin.setRawMode(true);
    stdin.resume();

    let input = '';

    const onData = (chunk) => {
      const chars = chunk.toString('utf8');
      for (const ch of chars) {
        const code = ch.charCodeAt(0);

        if (code === 13 || code === 10 || code === 4) {
          stdin.setRawMode(false);
          stdin.pause();
          stdin.removeListener('data', onData);
          stdout.write('\n');
          resolve(input);
          return;
        } else if (code === 3) {
          stdin.setRawMode(false);
          stdout.write('\n');
          process.exit(1);
        } else if (code === 127 || code === 8) {
          input = input.slice(0, -1);
        } else {
          input += ch;
        }
      }
    };

    stdin.on('data', onData);
  });
}
