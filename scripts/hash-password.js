// Prints the value for ADMIN_PASSWORD_HASH:   npm run hash-password -- "your password"
// (or pipe it in so it stays out of your shell history:  echo "your password" | npm run hash-password)
import { hashPassword } from "../lib/auth.js";

async function readStdin() {
  let text = "";
  for await (const chunk of process.stdin) text += chunk;
  return text.replace(/\r?\n$/, "");
}

const password = process.argv[2] ?? (process.stdin.isTTY ? "" : await readStdin());

if (password.length < 10) {
  console.error("Give a password of at least 10 characters:  npm run hash-password -- \"your password\"");
  process.exit(1);
}

console.log(hashPassword(password));
