const express = require("express");
const { query } = require("../db");
const { newId, agreementId } = require("../utils/ids");
const { appendLedger } = require("../utils/ledger");
const { negotiationSchema, proposalSchema } = require("../utils/validation");
const { generateAgreementPdf } = require("../utils/pdf");
const { uploadAgreement } = require("./storage");

const router = express.Router();

function now() {
  return new Date().toISOString();
}

function normalizeArray(value) {
  if (Array.isArray(value)) {
    return value;
  }
  if (typeof value === "string") {
    try {
      const parsed = JSON.parse(value);
      return Array.isArray(parsed) ? parsed : [String(parsed)];
    } catch (error) {
      return [value];
    }
  }
  if (value == null) {
    return [];
  }
  return [String(value)];
}

router.post("/", async (req, res) => {
  const actor = req.actor;
  const payload = negotiationSchema.safeParse(req.body);
  if (!payload.success) {
    return res.status(400).json({ error: "Invalid negotiation payload" });
  }
  const data = payload.data;
  const id = newId();
  const createdAt = now();
  const turn = "CLIENT";
  await query(
    "INSERT INTO negotiations (id, client_id, ca_id, status, current_turn, created_at, updated_at) VALUES ($1,$2,$3,$4,$5,$6,$6)",
    [id, data.clientId, data.caId, "OPEN", turn, createdAt]
  );
  await appendLedger({
    entityType: "negotiation",
    entityId: id,
    action: "created",
    actorId: actor.id,
    actorRole: actor.role,
    payload: data
  });
  res.status(201).json({ id, status: "OPEN", currentTurn: turn });
});

router.get("/:id", async (req, res) => {
  const result = await query("SELECT * FROM negotiations WHERE id = $1", [
    req.params.id
  ]);
  if (!result.rowCount) {
    return res.status(404).json({ error: "Negotiation not found" });
  }
  const proposals = await query(
    "SELECT * FROM proposals WHERE negotiation_id = $1 ORDER BY created_at ASC",
    [req.params.id]
  );
  res.json({ negotiation: result.rows[0], proposals: proposals.rows });
});

router.post("/:id/proposals", async (req, res) => {
  const actor = req.actor;
  const negotiationResult = await query(
    "SELECT * FROM negotiations WHERE id = $1",
    [req.params.id]
  );
  if (!negotiationResult.rowCount) {
    return res.status(404).json({ error: "Negotiation not found" });
  }
  const negotiation = negotiationResult.rows[0];
  if (negotiation.status !== "OPEN") {
    return res.status(409).json({ error: "Negotiation is locked" });
  }
  if (negotiation.current_turn !== actor.role) {
    return res.status(403).json({ error: "Not your turn" });
  }
  const payload = proposalSchema.safeParse(req.body);
  if (!payload.success) {
    return res.status(400).json({ error: "Invalid proposal payload" });
  }
  const data = payload.data;
  const proposalId = newId();
  const createdAt = now();
  await query(
    "INSERT INTO proposals (id, negotiation_id, author_id, author_role, amount_inr, scope_text, deliverables_json, acceptance_json, proposal_type, status, created_at) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11)",
    [
      proposalId,
      negotiation.id,
      actor.id,
      actor.role,
      data.amountInr,
      data.scopeText,
      JSON.stringify(data.deliverables),
      JSON.stringify(data.acceptanceCriteria),
      data.proposalType,
      "SUBMITTED",
      createdAt
    ]
  );

  const nextTurn =
    actor.role === "CLIENT" ? "CA" : actor.role === "CA" ? "CLIENT" : "CLIENT";
  const status = data.proposalType === "FINAL" ? "FINAL_OFFER" : "OPEN";
  await query(
    "UPDATE negotiations SET current_turn = $1, status = $2, updated_at = $3, locked_at = $4 WHERE id = $5",
    [
      status === "FINAL_OFFER" ? "LOCKED" : nextTurn,
      status,
      createdAt,
      status === "FINAL_OFFER" ? createdAt : null,
      negotiation.id
    ]
  );
  await appendLedger({
    entityType: "negotiation",
    entityId: negotiation.id,
    action: "proposal_submitted",
    actorId: actor.id,
    actorRole: actor.role,
    payload: {
      proposalId,
      proposalType: data.proposalType,
      amountInr: data.amountInr
    }
  });
  res.status(201).json({
    proposalId,
    status,
    nextTurn: status === "FINAL_OFFER" ? "LOCKED" : nextTurn
  });
});

router.post("/:id/accept-final", async (req, res) => {
  const actor = req.actor;
  const negotiationResult = await query(
    "SELECT * FROM negotiations WHERE id = $1",
    [req.params.id]
  );
  if (!negotiationResult.rowCount) {
    return res.status(404).json({ error: "Negotiation not found" });
  }
  const negotiation = negotiationResult.rows[0];
  if (negotiation.status !== "FINAL_OFFER") {
    return res.status(409).json({ error: "Final offer not available" });
  }
  if (negotiation.current_turn !== "LOCKED") {
    return res.status(409).json({ error: "Negotiation not locked" });
  }
  const proposalResult = await query(
    "SELECT * FROM proposals WHERE negotiation_id = $1 AND proposal_type = $2 ORDER BY created_at DESC LIMIT 1",
    [negotiation.id, "FINAL"]
  );
  if (!proposalResult.rowCount) {
    return res.status(404).json({ error: "Final proposal not found" });
  }
  const proposal = proposalResult.rows[0];
  const deliverables = normalizeArray(proposal.deliverables_json);
  const acceptanceCriteria = normalizeArray(proposal.acceptance_json);
  const agreementUuid = newId();
  const agreementCode = agreementId();
  const finalPrice = Number(proposal.amount_inr);
  const installmentAdvance = Number((finalPrice / 2).toFixed(2));
  const installmentBalance = Number((finalPrice - installmentAdvance).toFixed(2));
  const pdfResult = await generateAgreementPdf({
    agreementId: agreementCode,
    finalPrice,
    scopeText: proposal.scope_text,
    deliverables,
    acceptanceCriteria,
    installmentAdvance,
    installmentBalance
  });
  const storage = await uploadAgreement(agreementCode, pdfResult.buffer);
  const createdAt = now();
  await query(
    "INSERT INTO agreements (id, agreement_id, negotiation_id, final_price, scope_text, deliverables_json, acceptance_json, installment_advance, installment_balance, status, qr_payload, unsigned_pdf_url, created_at, updated_at) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$13)",
    [
      agreementUuid,
      agreementCode,
      negotiation.id,
      finalPrice,
      proposal.scope_text,
      JSON.stringify(deliverables),
      JSON.stringify(acceptanceCriteria),
      installmentAdvance,
      installmentBalance,
      "AGREEMENT_PENDING",
      pdfResult.qrPayload,
      storage.url,
      createdAt
    ]
  );
  await query("UPDATE negotiations SET status = $1, updated_at = $2 WHERE id = $3", [
    "AGREEMENT_CREATED",
    createdAt,
    negotiation.id
  ]);
  await appendLedger({
    entityType: "agreement",
    entityId: agreementUuid,
    action: "agreement_created",
    actorId: actor.id,
    actorRole: actor.role,
    payload: {
      agreementId: agreementCode,
      amountInr: finalPrice
    }
  });
  res.status(201).json({
    agreementUuid,
    agreementId: agreementCode,
    unsignedPdfUrl: storage.url
  });
});

module.exports = router;
