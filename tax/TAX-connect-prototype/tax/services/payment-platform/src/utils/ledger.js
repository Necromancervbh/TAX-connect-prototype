const { query } = require("../db");

async function appendLedger({
  entityType,
  entityId,
  action,
  actorId,
  actorRole,
  payload
}) {
  const sql =
    "INSERT INTO audit_ledger (entity_type, entity_id, action, actor_id, actor_role, payload, created_at) VALUES ($1,$2,$3,$4,$5,$6,$7)";
  const now = new Date().toISOString();
  await query(sql, [
    entityType,
    entityId,
    action,
    actorId,
    actorRole,
    payload,
    now
  ]);
  return now;
}

module.exports = {
  appendLedger
};
