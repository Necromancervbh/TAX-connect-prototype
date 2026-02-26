const { createHmac, createCipheriv, createDecipheriv, randomBytes } = require("crypto");

const SECRET = process.env.TOKENIZATION_SECRET;
if (!SECRET) {
  throw new Error("TOKENIZATION_SECRET is required");
}

function fpeShiftDigit(digit, offset) {
  const n = Number(digit);
  return ((n + offset) % 10).toString();
}

function fpeUnshiftDigit(digit, offset) {
  const n = Number(digit);
  return ((n - offset + 10) % 10).toString();
}

function fpeOffset(index, tweak) {
  const hmac = createHmac("sha256", SECRET);
  hmac.update(String(tweak));
  hmac.update(":");
  hmac.update(String(index));
  return hmac.digest()[0] % 10;
}

function fpeEncryptDigits(value, tweak) {
  if (!/^\d+$/.test(value)) {
    return value;
  }
  const chars = value.split("");
  return chars.map((digit, index) => fpeShiftDigit(digit, fpeOffset(index, tweak))).join("");
}

function fpeDecryptDigits(value, tweak) {
  if (!/^\d+$/.test(value)) {
    return value;
  }
  const chars = value.split("");
  return chars.map((digit, index) => fpeUnshiftDigit(digit, fpeOffset(index, tweak))).join("");
}

function encryptPayload(payload) {
  const iv = randomBytes(12);
  const key = createHmac("sha256", SECRET).update("payload").digest();
  const cipher = createCipheriv("aes-256-gcm", key, iv);
  const ciphertext = Buffer.concat([cipher.update(payload, "utf8"), cipher.final()]);
  const tag = cipher.getAuthTag();
  return Buffer.concat([iv, tag, ciphertext]).toString("base64");
}

function decryptPayload(token) {
  const raw = Buffer.from(token, "base64");
  const iv = raw.subarray(0, 12);
  const tag = raw.subarray(12, 28);
  const ciphertext = raw.subarray(28);
  const key = createHmac("sha256", SECRET).update("payload").digest();
  const decipher = createDecipheriv("aes-256-gcm", key, iv);
  decipher.setAuthTag(tag);
  const plaintext = Buffer.concat([decipher.update(ciphertext), decipher.final()]);
  return plaintext.toString("utf8");
}

module.exports = {
  fpeEncryptDigits,
  fpeDecryptDigits,
  encryptPayload,
  decryptPayload
};
