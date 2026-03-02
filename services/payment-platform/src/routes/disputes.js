const express = require("express");
const { query } = require("../db");
const { appendLedger } = require("../utils/ledger");
const { disputeSchema } = require("../utils/validation");
const { newId, ticketId } = require("../utils/ids");

const router = express.Router();

function now() {
  return new Date().toISOString();
}

router.post("/:id/disputes", async (req, res) => {
  const actor = req.actor;
  const payload = disputeSchema.safeParse(req.body);
  if (!payload.success) {
    return res.status(400).json({ error: "Invalid dispute payload" });
  }
  const agreementResult = await query("SELECT * FROM agreements WHERE id = $1", [
    req.params.id
  ]);
  if (!agreementResult.rowCount) {
    return res.status(404).json({ error: "Agreement not found" });
  }
  const disputeId = newId();
  const ticket = ticketId();
  const createdAt = now();
  await query(
    "INSERT INTO disputes (id, agreement_id, ticket_id, status, created_at, updated_at) VALUES ($1,$2,$3,$4,$5,$5)",
    [disputeId, req.params.id, ticket, "OPEN", createdAt]
  );
  await query(
    "UPDATE escrow_accounts SET status = $1, updated_at = $2 WHERE agreement_id = $3",
    ["FROZEN", createdAt, req.params.id]
  );
  await query("UPDATE agreements SET status = $1, updated_at = $2 WHERE id = $3", [
    "DISPUTE",
    createdAt,
    req.params.id
  ]);
  await appendLedger({
    entityType: "dispute",
    entityId: disputeId,
    action: "dispute_opened",
    actorId: actor.id,
    actorRole: actor.role,
    payload: { ticket, reason: payload.data.reason }
  });
  res.status(201).json({ id: disputeId, ticketId: ticket });
});

router.get("/:id/disputes", async (req, res) => {
  const result = await query(
    "SELECT * FROM disputes WHERE agreement_id = $1 ORDER BY created_at DESC",
    [req.params.id]
  );
  res.json({ disputes: result.rows });
});

module.exports = router;
