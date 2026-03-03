const express = require("express");
const { query } = require("../db");

const router = express.Router();

router.get("/", async (req, res) => {
  const { agreementId, actorId, from, to, limit = 100, offset = 0 } = req.query;
  const params = [];
  const conditions = [];
  let idx = 1;

  if (agreementId) {
    conditions.push(`payload->>'agreementId' = $${idx++}`);
    params.push(agreementId);
  }
  if (actorId) {
    conditions.push(`actor_id = $${idx++}`);
    params.push(actorId);
  }
  if (from) {
    conditions.push(`created_at >= $${idx++}`);
    params.push(from);
  }
  if (to) {
    conditions.push(`created_at <= $${idx++}`);
    params.push(to);
  }
  const where = conditions.length ? `WHERE ${conditions.join(" AND ")}` : "";
  params.push(Number(limit));
  params.push(Number(offset));
  const sql = `SELECT * FROM audit_ledger ${where} ORDER BY created_at DESC LIMIT $${idx++} OFFSET $${idx++}`;
  const result = await query(sql, params);
  res.json({ logs: result.rows });
});

module.exports = router;
