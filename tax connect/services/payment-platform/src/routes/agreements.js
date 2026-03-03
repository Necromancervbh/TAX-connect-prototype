const express = require("express");
const multer = require("multer");
const { query } = require("../db");
const { appendLedger } = require("../utils/ledger");
const { uploadSignedAgreement } = require("./storage");
const { newId } = require("../utils/ids");

const router = express.Router();
const upload = multer();

function now() {
  return new Date().toISOString();
}

router.get("/:id", async (req, res) => {
  const result = await query("SELECT * FROM agreements WHERE id = $1", [
    req.params.id
  ]);
  if (!result.rowCount) {
    return res.status(404).json({ error: "Agreement not found" });
  }
  res.json(result.rows[0]);
});

router.post("/:id/sign", upload.single("file"), async (req, res) => {
  const actor = req.actor;
  if (!req.file) {
    return res.status(400).json({ error: "Signed file is required" });
  }
  const agreementResult = await query("SELECT * FROM agreements WHERE id = $1", [
    req.params.id
  ]);
  if (!agreementResult.rowCount) {
    return res.status(404).json({ error: "Agreement not found" });
  }
  const agreement = agreementResult.rows[0];
  const uploaded = await uploadSignedAgreement(
    agreement.agreement_id,
    req.file.buffer
  );
  const updatedAt = now();
  await query(
    "UPDATE agreements SET signed_pdf_url = $1, signed_checksum = $2, status = $3, updated_at = $4 WHERE id = $5",
    [uploaded.url, uploaded.checksum, "SIGNED", updatedAt, agreement.id]
  );
  await appendLedger({
    entityType: "agreement",
    entityId: agreement.id,
    action: "agreement_signed",
    actorId: actor.id,
    actorRole: actor.role,
    payload: {
      signedUrl: uploaded.url
    }
  });
  res.json({ status: "SIGNED", signedUrl: uploaded.url });
});

router.post("/:id/milestones", async (req, res) => {
  const actor = req.actor;
  const { milestone, status, evidenceUrl } = req.body || {};
  if (!milestone || !status) {
    return res.status(400).json({ error: "Milestone and status required" });
  }
  const agreementResult = await query("SELECT * FROM agreements WHERE id = $1", [
    req.params.id
  ]);
  if (!agreementResult.rowCount) {
    return res.status(404).json({ error: "Agreement not found" });
  }
  const milestoneId = newId();
  const createdAt = now();
  await query(
    "INSERT INTO milestone_events (id, agreement_id, milestone, status, evidence_url, created_at) VALUES ($1,$2,$3,$4,$5,$6)",
    [milestoneId, req.params.id, milestone, status, evidenceUrl || null, createdAt]
  );
  await appendLedger({
    entityType: "agreement",
    entityId: req.params.id,
    action: "milestone_update",
    actorId: actor.id,
    actorRole: actor.role,
    payload: { milestone, status }
  });
  res.status(201).json({ id: milestoneId, status });
});

module.exports = router;
