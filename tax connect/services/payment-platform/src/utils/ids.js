const { randomUUID, createHash } = require("crypto");

function newId() {
  return randomUUID();
}

function agreementId() {
  return "AGR-" + randomUUID().split("-")[0].toUpperCase();
}

function ticketId() {
  return "DSP-" + randomUUID().split("-")[0].toUpperCase();
}

function sha256(data) {
  return createHash("sha256").update(data).digest("hex");
}

module.exports = {
  newId,
  agreementId,
  ticketId,
  sha256
};
